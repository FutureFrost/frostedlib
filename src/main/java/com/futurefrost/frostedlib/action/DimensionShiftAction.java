package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.util.TeleportHelper;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.registry.tag.TagKey;

import java.util.*;

/**
 * A comprehensive entity action that teleports the target to a specified dimension and position.
 * Supports multiple targeting modes, safe position search, platform generation, and robust error handling.
 */
public class DimensionShiftAction {

    // =============== CONFIGURATION CONSTANTS ===============
    public static final String MODE_RELATIVE = "relative";
    public static final String MODE_FIXED = "fixed";
    public static final String MODE_BIOME = "biome";
    public static final String MODE_STRUCTURE = "structure";

    public static final String HEIGHT_EXPOSED = "exposed";
    public static final String HEIGHT_UNEXPOSED = "unexposed";
    public static final String HEIGHT_RELATIVE = "relative";

    public static final String SHAPE_CIRCLE = "circle";
    public static final String SHAPE_SQUARE = "square";
    public static final String SHAPE_CROSS = "cross";
    public static final String SHAPE_PLATFORM_ONLY = "platform_only";
    public static final String SHAPE_SAFE_ROOM = "safe_room";

    // =============== ERROR HANDLING ===============
    public enum ErrorType {
        VALIDATION_ERROR,
        BIOME_NOT_FOUND,
        STRUCTURE_NOT_FOUND,
        NO_SAFE_POSITION,
        DIMENSION_NOT_FOUND,
        TELEPORT_FAILED,
        RUNTIME_EXCEPTION,
        STRICT_HEIGHT_VIOLATION
    }

    // =============== MAIN ACTION ENTRY POINT ===============
    public static void action(SerializableData.Instance data, Entity entity) {
        if (entity.getServer() == null) {
            handleError(data, entity, ErrorType.RUNTIME_EXCEPTION, "Entity server is null", null, null);
            return;
        }

        try {
            executeDimensionShift(data, entity);
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Unexpected error in dimension_shift action", e);
            handleError(data, entity, ErrorType.RUNTIME_EXCEPTION, "Unexpected error: " + e.getMessage(), null, null);
        }
    }

    // =============== CORE EXECUTION LOGIC ===============
    private static void executeDimensionShift(SerializableData.Instance data, Entity entity) {
        // 1. TARGET DIMENSION
        RegistryKey<World> targetDimensionKey = getTargetDimension(data, entity);
        ServerWorld targetWorld = entity.getServer().getWorld(targetDimensionKey);

        if (targetWorld == null) {
            handleError(data, entity, ErrorType.DIMENSION_NOT_FOUND,
                    "Target dimension not found or not loaded: " + targetDimensionKey.getValue(), null, targetDimensionKey);
            return;
        }

        // 2. TARGET POSITION (Base Calculation)
        Vec3d baseTargetPos;
        try {
            baseTargetPos = calculateBaseTargetPosition(data, entity, targetWorld);
        } catch (IllegalArgumentException e) {
            handleError(data, entity, ErrorType.VALIDATION_ERROR, e.getMessage(), null, targetDimensionKey);
            return;
        } catch (RuntimeException e) {
            // This catches biome/structure not found errors
            String errorMsg = e.getMessage();
            ErrorType errorType = errorMsg.contains("biome") ? ErrorType.BIOME_NOT_FOUND : ErrorType.STRUCTURE_NOT_FOUND;
            handleError(data, entity, errorType, errorMsg, null, targetDimensionKey);
            return;
        }

        // 3. RANDOM OFFSET
        Vec3d randomizedPos = applyRandomOffset(data, baseTargetPos, targetWorld);

        // 4. SAFE POSITION SEARCH
        Vec3d finalPosition = findSafePositionWithFallbacks(data, entity, targetWorld,
                (int) randomizedPos.x, (int) randomizedPos.z);

        if (finalPosition == null) {
            handleError(data, entity, ErrorType.NO_SAFE_POSITION,
                    "No safe teleport position found after all fallbacks.", baseTargetPos, targetDimensionKey);
            return;
        }

        // 5. MOUNT HANDLING & TELEPORT
        boolean bringMount = data.getBoolean("bring_mount");
        Entity mount = bringMount ? entity.getVehicle() : null;

        boolean teleportSuccess = teleportEntityAndMount(entity, targetWorld, finalPosition, bringMount, mount);

        if (!teleportSuccess) {
            handleError(data, entity, ErrorType.TELEPORT_FAILED,
                    "Failed to teleport entity to final position.", finalPosition, targetDimensionKey);
            return;
        }

        // 6. SUCCESS FEEDBACK
        if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
            boolean isChangingDimension = !entity.getWorld().getRegistryKey().equals(targetDimensionKey);
            String message = isChangingDimension ?
                    "Dimension shifted to " + targetDimensionKey.getValue() :
                    "Teleported within " + targetDimensionKey.getValue();
            player.sendMessage(Text.literal(message), false);
        }
    }

    // =============== STEP 1: TARGET DIMENSION ===============
    private static RegistryKey<World> getTargetDimension(SerializableData.Instance data, Entity entity) {
        if (data.isPresent("target_dimension")) {
            Identifier dimId = data.getId("target_dimension");
            return RegistryKey.of(RegistryKeys.WORLD, dimId);
        }
        return entity.getWorld().getRegistryKey();
    }

    // =============== STEP 2: TARGET POSITION CALCULATION ===============
    private static Vec3d calculateBaseTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        String mode = data.getString("target_position");
        if (mode == null) {
            mode = MODE_RELATIVE;
        }

        switch (mode) {
            case MODE_FIXED:
                return getWorldSpawnPosition(targetWorld);
            case MODE_BIOME:
                return findBiomePosition(data, entity, targetWorld);
            case MODE_STRUCTURE:
                return findStructurePosition(data, entity, targetWorld);
            case MODE_RELATIVE:
            default:
                return calculateRelativePosition(data, entity);
        }
    }

    private static Vec3d getWorldSpawnPosition(ServerWorld world) {
        BlockPos spawnPos = world.getSpawnPos();
        return Vec3d.ofBottomCenter(spawnPos);
    }

    private static Vec3d calculateRelativePosition(SerializableData.Instance data, Entity entity) {
        double scale = data.getDouble("scale_factor");
        return new Vec3d(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
    }

    private static Vec3d findBiomePosition(SerializableData.Instance data, Entity entity, ServerWorld world) {
        Identifier biomeId = data.getId("biome_id");
        if (biomeId == null) {
            throw new IllegalArgumentException("Parameter 'biome_id' must be specified when target_position is 'biome'.");
        }

        // For 1.20.1 Fabric, get the biome from the dynamic registry
        Registry<Biome> biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);

        RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);

        Optional<RegistryEntry.Reference<Biome>> biomeEntry = biomeRegistry.getEntry(biomeKey);

        if (biomeEntry.isEmpty()) {
            throw new IllegalArgumentException("Biome not found in registry: " + biomeId);
        }

        RegistryEntry<Biome> targetBiome = biomeEntry.get();

        int searchRadius = data.getInt("biome_search_radius");

        // locateBiome returns Pair<BlockPos, RegistryEntry<Biome>> in 1.20.1
        var biomeResult = world.locateBiome(
                biome -> biome.equals(targetBiome),
                entity.getBlockPos(),
                searchRadius * 16,
                8,
                64
        );

        if (biomeResult == null) {
            throw new RuntimeException("Could not find biome: " + biomeId + " within " + searchRadius + " chunks.");
        }

        // Extract BlockPos from the Pair
        BlockPos biomePos = biomeResult.getFirst();
        return new Vec3d(biomePos.getX() + 0.5, biomePos.getY(), biomePos.getZ() + 0.5);
    }

    private static Vec3d findStructurePosition(SerializableData.Instance data, Entity entity, ServerWorld world) {
        Identifier structureId = data.getId("structure_id");
        if (structureId == null) {
            throw new IllegalArgumentException("Parameter 'structure_id' must be specified when target_position is 'structure'.");
        }

        // In 1.20.1, we need to create a TagKey for the structure
        // First, let's check if the structure exists in the registry
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Structure structure = structureRegistry.get(structureId);

        if (structure == null) {
            throw new RuntimeException("Structure not found in registry: " + structureId);
        }

        int searchRadius = data.getInt("structure_search_radius");

        // Create a TagKey for the structure - structures are typically referenced by tags
        TagKey<Structure> structureTagKey = TagKey.of(RegistryKeys.STRUCTURE, structureId);

        // Use the TagKey version of locateStructure
        BlockPos structurePos = world.locateStructure(
                structureTagKey,
                entity.getBlockPos(),
                searchRadius,
                false
        );

        if (structurePos == null) {
            // Fallback: try to find any structure at that position
            structurePos = world.locateStructure(
                    TagKey.of(RegistryKeys.STRUCTURE, new Identifier("village")), // Try a common structure tag as fallback
                    entity.getBlockPos(),
                    searchRadius,
                    false
            );

            if (structurePos == null) {
                throw new RuntimeException("Could not find structure: " + structureId + " within " + searchRadius + " chunks.");
            }
        }

        // Optional: Find a better spawn point
        if (data.getBoolean("prefer_spawn_point")) {
            BlockPos spawnPos = findStructureSpawnPoint(world, structurePos);
            if (spawnPos != null) {
                return new Vec3d(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            }
        }

        return new Vec3d(structurePos.getX() + 0.5, structurePos.getY(), structurePos.getZ() + 0.5);
    }

    private static BlockPos findStructureSpawnPoint(ServerWorld world, BlockPos structurePos) {
        // Simple implementation: find the highest solid block at the structure position
        BlockPos.Mutable mutablePos = structurePos.mutableCopy();
        for (int y = world.getTopY(); y > world.getBottomY(); y--) {
            mutablePos.setY(y);
            BlockState state = world.getBlockState(mutablePos);
            BlockState aboveState = world.getBlockState(mutablePos.up());
            if (state.isSolidBlock(world, mutablePos) && aboveState.isAir()) {
                return mutablePos.up();
            }
        }
        return structurePos; // Fallback to original position
    }

    // =============== STEP 3: RANDOM OFFSET ===============
    private static Vec3d applyRandomOffset(SerializableData.Instance data, Vec3d basePos, ServerWorld world) {
        double randomLimit = data.getDouble("random_limit");
        if (randomLimit <= 0) return basePos;

        Random random = new Random();
        double offsetX = (random.nextDouble() * 2 - 1) * randomLimit;
        double offsetZ = (random.nextDouble() * 2 - 1) * randomLimit;
        return new Vec3d(basePos.x + offsetX, basePos.y, basePos.z + offsetZ);
    }

    // =============== STEP 4: SAFE POSITION SEARCH ===============
    // Main search method with all fallbacks
    private static Vec3d findSafePositionWithFallbacks(SerializableData.Instance data, Entity entity,
                                                       ServerWorld world, int centerX, int centerZ) {
        String heightMode = data.getString("target_height");
        if (heightMode == null) {
            heightMode = HEIGHT_EXPOSED;
        }
        double preferredY = entity.getY();
        boolean strictHeight = data.getBoolean("strict_height");
        boolean generatePlatform = data.getBoolean("generate_platform");

        // STAGE 1: Try exact position with strict height checking
        Vec3d exactPos = findSafeHeightPosition(data, world, centerX, centerZ, heightMode, preferredY, strictHeight);
        if (exactPos != null && isPositionActuallySafe(data, world, exactPos)) {
            return exactPos;
        }

        // STAGE 2: Expanding search
        int maxSearchRadius = Math.min(data.getInt("search_radius"), 128);
        int maxSearchAttempts = data.getInt("max_search_attempts");

        Vec3d expandingPos = findSafePositionExpandingSearch(data, world, centerX, centerZ, heightMode,
                preferredY, maxSearchRadius, maxSearchAttempts, strictHeight);
        if (expandingPos != null) return expandingPos;

        // STAGE 3: Platform generation (always try if over liquid, regardless of strict height)
        boolean isOverLiquid = isOverLiquidSurface(data, world, centerX, centerZ);
        if (generatePlatform || isOverLiquid) {
            Vec3d platformPos = generatePlatformAtPosition(data, world, centerX, centerZ, heightMode, preferredY, strictHeight, isOverLiquid);
            if (platformPos != null) return platformPos;
        }

        // STAGE 4: Final fallbacks (check strict height)
        if (strictHeight) {
            // For exposed over liquid, we should have created a platform already
            // If we're here, it means we couldn't even create a platform
            if (heightMode.equals(HEIGHT_EXPOSED)) {
                handleError(data, entity, ErrorType.STRICT_HEIGHT_VIOLATION,
                        "Strict height enabled: Could not find exposed position.", null, world.getRegistryKey());
                return null;
            } else if (heightMode.equals(HEIGHT_UNEXPOSED)) {
                handleError(data, entity, ErrorType.STRICT_HEIGHT_VIOLATION,
                        "Strict height enabled: Could not find unexposed position.", null, world.getRegistryKey());
                return null;
            }
        }

        // STAGE 5: Non-strict fallback to opposite height
        return getOppositeHeightFallback(data, world, centerX, centerZ, heightMode, preferredY);
    }

    // =============== LIQUID HANDLING METHODS ===============
    private static boolean isLiquidSafe(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        boolean liquidsSafe = data.getBoolean("liquids_safe");
        if (liquidsSafe) {
            return true; // All liquids are safe
        }

        // Get the fluid predicate from data
        SerializableData.Instance fluidPredicate = data.get("liquid_condition");
        if (fluidPredicate == null) {
            return false; // No specific condition, all liquids are unsafe
        }

        // Check if this fluid matches the predicate
        // In Apoli, fluid predicates have a test method
        try {
            // Get the fluid state at the position
            net.minecraft.fluid.FluidState fluidState = world.getFluidState(pos);
            if (fluidState.isEmpty()) {
                return true; // No fluid is always safe
            }

            // For simplicity, we'll check if the fluid is water or lava
            // In a real implementation, you'd need to parse the fluid predicate
            net.minecraft.fluid.Fluid fluid = fluidState.getFluid();

            // Default unsafe fluids (when no condition is specified)
            if (fluid == net.minecraft.fluid.Fluids.WATER ||
                    fluid == net.minecraft.fluid.Fluids.FLOWING_WATER ||
                    fluid == net.minecraft.fluid.Fluids.LAVA ||
                    fluid == net.minecraft.fluid.Fluids.FLOWING_LAVA) {
                return false;
            }

            return true; // Other fluids are safe by default
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Error checking liquid safety", e);
            return false; // On error, assume unsafe
        }
    }

    private static boolean isBlockUnsafeLiquid(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return !isLiquidSafe(data, world, pos);
        }
        return false;
    }

    private static boolean isOverLiquidSurface(SerializableData.Instance data, ServerWorld world, int x, int z) {
        // Check if the top block at this position is liquid
        BlockPos topPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        BlockState state = world.getBlockState(topPos);
        return !state.getFluidState().isEmpty();
    }

    private static int findLiquidSurface(ServerWorld world, int x, int z, SerializableData.Instance data) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(x, world.getTopY(), z);

        // Find the top of the world
        for (int y = world.getTopY(); y > world.getBottomY(); y--) {
            mutablePos.setY(y);
            BlockState state = world.getBlockState(mutablePos);
            if (!state.isAir()) {
                // Found non-air block, check if it's liquid
                if (!state.getFluidState().isEmpty()) {
                    // Found liquid, return the surface level (liquid block + 1)
                    return y + 1;
                } else {
                    // Found solid block, no liquid surface here
                    return -1;
                }
            }
        }
        return -1;
    }

    private static int findTrueLiquidSurface(ServerWorld world, int x, int z) {
        // More accurate liquid surface finder that checks for sky visibility
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(x, world.getTopY(), z);

        for (int y = world.getTopY(); y > world.getBottomY(); y--) {
            mutablePos.setY(y);
            BlockState state = world.getBlockState(mutablePos);

            // Check if this block is liquid
            if (!state.getFluidState().isEmpty()) {
                // Check if the position above is air (true liquid surface)
                BlockPos abovePos = mutablePos.up();
                if (world.getBlockState(abovePos).isAir()) {
                    return y + 1; // Platform should be placed here
                }
            }
        }
        return -1;
    }

    // =============== MODIFIED POSITION SEARCH HELPER METHODS ===============
    private static Vec3d findSafeHeightPosition(SerializableData.Instance data, ServerWorld world, int x, int z, String mode, double preferredY, boolean strictHeight) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        if (mode.equals(HEIGHT_UNEXPOSED)) {
            // Search downward from preferred Y for cave position
            int startY = Math.min((int) preferredY, worldTop - 10);
            for (int y = startY; y >= worldBottom; y--) {
                BlockPos testPos = new BlockPos(x, y, z);
                if (isPositionSafeForEntity(data, world, x, y, z) &&
                        !world.isSkyVisible(testPos)) {
                    // Check for unsafe liquids
                    if (isBlockUnsafeLiquid(data, world, testPos) ||
                            isBlockUnsafeLiquid(data, world, testPos.down())) {
                        continue; // Skip positions in unsafe liquids
                    }
                    return new Vec3d(x + 0.5, y, z + 0.5);
                }
            }

            // If strict height is enabled, don't fall back to surface
            if (strictHeight) {
                return null;
            }

            // Fallback to surface (last resort for unexposed)
            return getSurfacePosition(data, world, x, z);
        } else if (mode.equals(HEIGHT_RELATIVE)) {
            // Search around preferred Y
            int centerY = (int) preferredY;
            for (int offset = 0; offset < 64; offset++) {
                // Check above
                int testYAbove = centerY + offset;
                if (testYAbove <= worldTop) {
                    BlockPos testPos = new BlockPos(x, testYAbove, z);
                    if (isPositionSafeForEntity(data, world, x, testYAbove, z)) {
                        // Check for unsafe liquids
                        if (!isBlockUnsafeLiquid(data, world, testPos) &&
                                !isBlockUnsafeLiquid(data, world, testPos.down())) {
                            return new Vec3d(x + 0.5, testYAbove, z + 0.5);
                        }
                    }
                }

                // Check below
                int testYBelow = centerY - offset;
                if (testYBelow >= worldBottom) {
                    BlockPos testPos = new BlockPos(x, testYBelow, z);
                    if (isPositionSafeForEntity(data, world, x, testYBelow, z)) {
                        // Check for unsafe liquids
                        if (!isBlockUnsafeLiquid(data, world, testPos) &&
                                !isBlockUnsafeLiquid(data, world, testPos.down())) {
                            return new Vec3d(x + 0.5, testYBelow, z + 0.5);
                        }
                    }
                }
            }
            // Fallback to surface
            return getSurfacePosition(data, world, x, z);
        } else {
            // EXPOSED (default)
            Vec3d surfacePos = getSurfacePosition(data, world, x, z);

            // If strict height is enabled, check if this is truly exposed
            if (strictHeight && surfacePos != null) {
                BlockPos testPos = new BlockPos((int) surfacePos.x, (int) surfacePos.y, (int) surfacePos.z);
                if (!world.isSkyVisible(testPos.down())) { // Check the block position, not the entity position
                    // Not truly exposed to sky
                    // Check if we're over liquid - if so, this is considered "exposed" for our purposes
                    if (!isOverLiquidSurface(data, world, x, z)) {
                        // Not over liquid either, search upward for truly exposed position
                        for (int y = (int) surfacePos.y; y <= worldTop; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (isPositionSafeForEntity(data, world, x, y, z) && world.isSkyVisible(pos)) {
                                return new Vec3d(x + 0.5, y, z + 0.5);
                            }
                        }
                        return null; // No truly exposed position found
                    }
                    // Over liquid - this is acceptable for "exposed"
                }
            }

            return surfacePos;
        }
    }

    private static Vec3d getSurfacePosition(SerializableData.Instance data, ServerWorld world, int x, int z) {
        try {
            // First, check if we're over liquid
            int liquidSurfaceY = findTrueLiquidSurface(world, x, z);
            if (liquidSurfaceY != -1) {
                // We're over liquid, return position above liquid
                return new Vec3d(x + 0.5, liquidSurfaceY, z + 0.5);
            }

            // Not over liquid, find normal surface
            BlockPos surfacePos = new BlockPos(x, world.getTopY(), z);

            // Scan from top to bottom for solid ground
            for (int y = world.getTopY(); y > world.getBottomY(); y--) {
                surfacePos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(surfacePos);
                BlockState aboveState = world.getBlockState(surfacePos.up());

                // Check if this is solid ground with air above
                if (state.isSolidBlock(world, surfacePos) && aboveState.isAir()) {
                    // Found dry land
                    return new Vec3d(x + 0.5, y + 1, z + 0.5);
                }
            }

            // Fallback to world surface heightmap
            int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            return new Vec3d(x + 0.5, surfaceY, z + 0.5);
        } catch (Exception e) {
            return new Vec3d(x + 0.5, world.getSeaLevel(), z + 0.5);
        }
    }

    private static Vec3d getOppositeHeightFallback(SerializableData.Instance data, ServerWorld world, int x, int z, String originalMode, double preferredY) {
        if (originalMode.equals(HEIGHT_EXPOSED)) {
            // Fallback to unexposed
            return findSafeHeightPosition(data, world, x, z, HEIGHT_UNEXPOSED, preferredY, false);
        } else if (originalMode.equals(HEIGHT_UNEXPOSED)) {
            // Fallback to exposed (surface)
            return getSurfacePosition(data, world, x, z);
        } else {
            // For relative mode, just try surface
            return getSurfacePosition(data, world, x, z);
        }
    }

    private static boolean isPositionSafeForEntity(SerializableData.Instance data, ServerWorld world, int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) return false;

        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockPos groundPos = new BlockPos(x, y - 1, z);

        // Check for unsafe liquids
        if (isBlockUnsafeLiquid(data, world, feetPos) ||
                isBlockUnsafeLiquid(data, world, headPos) ||
                isBlockUnsafeLiquid(data, world, groundPos)) {
            return false;
        }

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        // Check if blocks are passable
        boolean feetSafe = feetState.isAir() || !feetState.isOpaque();
        boolean headSafe = headState.isAir() || !headState.isOpaque();
        boolean groundSolid = groundState.isSolidBlock(world, groundPos) || y <= world.getBottomY() + 1;

        return feetSafe && headSafe && groundSolid;
    }

    private static boolean isPositionActuallySafe(SerializableData.Instance data, ServerWorld world, Vec3d pos) {
        return isPositionSafeForEntity(data, world, (int) pos.x, (int) pos.y, (int) pos.z);
    }

    private static Vec3d findSafePositionExpandingSearch(SerializableData.Instance data, ServerWorld world, int centerX, int centerZ,
                                                         String heightMode, double preferredY,
                                                         int maxRadius, int maxAttempts, boolean strictHeight) {
        // Generate exponential radius steps: 1, 2, 4, 8, 16, 32...
        List<Integer> radiusSteps = new ArrayList<>();
        for (int radius = 1; radius <= maxRadius; radius *= 2) {
            radiusSteps.add(radius);
            if (radiusSteps.size() >= 8) break; // Max 8 radius steps
        }

        // Add max radius as final step if not already included
        if (!radiusSteps.contains(maxRadius)) {
            radiusSteps.add(maxRadius);
        }

        int totalAttempts = 0;

        // Try each radius step
        for (int radius : radiusSteps) {
            if (totalAttempts >= maxAttempts) break;

            // Calculate number of points to test at this radius
            int circumference = (int) (2 * Math.PI * radius);
            int points = Math.min(circumference / 4, 16); // Scale with radius, max 16
            points = Math.max(points, 4); // Minimum 4 points

            // Test points around the circle
            for (int i = 0; i < points; i++) {
                if (totalAttempts >= maxAttempts) break;

                double angle = 2 * Math.PI * i / points;
                int x = centerX + (int) (radius * Math.cos(angle));
                int z = centerZ + (int) (radius * Math.sin(angle));

                Vec3d testPos = findSafeHeightPosition(data, world, x, z, heightMode, preferredY, strictHeight);
                if (testPos != null && isPositionActuallySafe(data, world, testPos)) {
                    return testPos;
                }

                totalAttempts++;
            }

            // For non-EXPOSED modes, also check different Y levels
            if (!heightMode.equals(HEIGHT_EXPOSED) && totalAttempts < maxAttempts) {
                for (int yOffset = -8; yOffset <= 8; yOffset += 4) {
                    if (totalAttempts >= maxAttempts) break;

                    double testY = preferredY + yOffset;
                    // Test 4 cardinal directions at this Y level
                    for (int i = 0; i < 4; i++) {
                        double angle = Math.PI * i / 2;
                        int x = centerX + (int) (radius * Math.cos(angle));
                        int z = centerZ + (int) (radius * Math.sin(angle));

                        Vec3d testPos = findSafeHeightPosition(data, world, x, z, heightMode, testY, strictHeight);
                        if (testPos != null && isPositionActuallySafe(data, world, testPos)) {
                            return testPos;
                        }
                        totalAttempts++;
                    }
                }
            }
        }

        return null;
    }

    private static Vec3d generatePlatformAtPosition(SerializableData.Instance data, ServerWorld world,
                                                    int centerX, int centerZ, String heightMode,
                                                    double preferredY, boolean strictHeight, boolean forcePlatform) {
        // Get platform configuration
        Identifier platformBlockId = data.getId("platform_block");
        BlockState platformBlock = platformBlockId != null ?
                world.getRegistryManager().get(RegistryKeys.BLOCK).get(platformBlockId).getDefaultState() :
                Blocks.OBSIDIAN.getDefaultState();

        int platformSize = Math.min(data.getInt("platform_size"), 16);
        if (platformSize <= 0) platformSize = 3;

        String platformShape = data.getString("platform_shape");
        if (platformShape == null) platformShape = SHAPE_CIRCLE;

        // Find Y position for platform
        int platformY = findPlatformY(data, world, centerX, centerZ, preferredY, heightMode, strictHeight, forcePlatform);

        // Check if platform would be placed in unsafe liquid
        BlockPos platformCenter = new BlockPos(centerX, platformY, centerZ);
        if (isBlockUnsafeLiquid(data, world, platformCenter) ||
                isBlockUnsafeLiquid(data, world, platformCenter.down())) {
            // We're placing platform on liquid surface, adjust Y
            int liquidSurfaceY = findTrueLiquidSurface(world, centerX, centerZ);
            if (liquidSurfaceY != -1) {
                platformY = liquidSurfaceY - 1; // Platform goes at liquid surface level
            }
        }

        // Generate the platform
        generatePlatform(world, centerX, platformY, centerZ, platformSize, platformShape, platformBlock);

        // Calculate safe position on platform
        Vec3d platformPos = calculatePlatformPosition(world, centerX, platformY, centerZ, platformSize, platformShape);

        return platformPos;
    }

    private static int findPlatformY(SerializableData.Instance data, ServerWorld world, int x, int z, double preferredY, String heightMode, boolean strictHeight, boolean forcePlatform) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        // First, check if we're over liquid
        boolean overLiquid = isOverLiquidSurface(data, world, x, z);

        if (heightMode.equals(HEIGHT_EXPOSED)) {
            if (overLiquid || forcePlatform) {
                // Over liquid or forced platform - place at liquid surface
                int liquidSurfaceY = findTrueLiquidSurface(world, x, z);
                if (liquidSurfaceY != -1) {
                    return liquidSurfaceY - 1; // Platform at liquid surface
                }
            }

            // For exposed, place at surface
            Vec3d surfacePos = getSurfacePosition(data, world, x, z);
            if (surfacePos != null) {
                int surfaceY = (int) surfacePos.y - 1; // Platform goes at feet level

                // If strict height, ensure it's truly exposed
                if (strictHeight) {
                    BlockPos testPos = new BlockPos(x, surfaceY, z);
                    if (!world.isSkyVisible(testPos) && !overLiquid) {
                        // Not exposed and not over liquid
                        // Try to find exposed position
                        for (int y = surfaceY; y <= worldTop; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (world.isSkyVisible(pos)) {
                                return y;
                            }
                        }
                        // Couldn't find exposed position
                        return world.getSeaLevel(); // Fallback
                    }
                }
                return surfaceY;
            }
            return world.getSeaLevel();
        } else if (heightMode.equals(HEIGHT_UNEXPOSED)) {
            // For unexposed, try to find cave position
            int startY = Math.min((int) preferredY, worldTop - 10);
            for (int y = startY; y >= worldBottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.isSkyVisible(pos)) {
                    // Found unexposed position
                    return y;
                }
            }

            // If strict height, don't fall back to surface
            if (strictHeight) {
                return world.getSeaLevel(); // Couldn't find unexposed position
            }

            // Fallback to surface (last resort)
            Vec3d surfacePos = getSurfacePosition(data, world, x, z);
            return surfacePos != null ? (int) surfacePos.y - 1 : world.getSeaLevel();
        } else {
            // HEIGHT_RELATIVE or default
            Vec3d surfacePos = getSurfacePosition(data, world, x, z);
            return surfacePos != null ? (int) surfacePos.y - 1 : world.getSeaLevel();
        }
    }

    private static void generatePlatform(ServerWorld world, int centerX, int centerY, int centerZ,
                                         int size, String shape, BlockState block) {
        switch (shape) {
            case SHAPE_SQUARE:
                generateSquarePlatform(world, centerX, centerY, centerZ, size, block);
                break;
            case SHAPE_CROSS:
                generateCrossPlatform(world, centerX, centerY, centerZ, size, block);
                break;
            case SHAPE_PLATFORM_ONLY:
                world.setBlockState(new BlockPos(centerX, centerY, centerZ), block);
                break;
            case SHAPE_SAFE_ROOM:
                generateSafeRoom(world, centerX, centerY, centerZ, size, block);
                break;
            case SHAPE_CIRCLE:
            default:
                generateCircularPlatform(world, centerX, centerY, centerZ, size, block);
                break;
        }
    }

    private static void generateCircularPlatform(ServerWorld world, int centerX, int centerY, int centerZ,
                                                 int radius, BlockState block) {
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radiusSq) {
                    world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ + dz), block);
                }
            }
        }
    }

    private static void generateSquarePlatform(ServerWorld world, int centerX, int centerY, int centerZ,
                                               int radius, BlockState block) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ + dz), block);
            }
        }
    }

    private static void generateCrossPlatform(ServerWorld world, int centerX, int centerY, int centerZ,
                                              int radius, BlockState block) {
        // Horizontal line
        for (int dx = -radius; dx <= radius; dx++) {
            world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ), block);
        }
        // Vertical line
        for (int dz = -radius; dz <= radius; dz++) {
            world.setBlockState(new BlockPos(centerX, centerY, centerZ + dz), block);
        }
    }

    private static void generateSafeRoom(ServerWorld world, int centerX, int centerY, int centerZ,
                                         int size, BlockState block) {
        // Floor
        generateSquarePlatform(world, centerX, centerY, centerZ, size, block);
        // Ceiling
        generateSquarePlatform(world, centerX, centerY + 3, centerZ, size, block);
        // Walls
        for (int y = centerY + 1; y <= centerY + 2; y++) {
            for (int d = -size; d <= size; d++) {
                world.setBlockState(new BlockPos(centerX + size, y, centerZ + d), block);
                world.setBlockState(new BlockPos(centerX - size, y, centerZ + d), block);
                world.setBlockState(new BlockPos(centerX + d, y, centerZ + size), block);
                world.setBlockState(new BlockPos(centerX + d, y, centerZ - size), block);
            }
        }
    }

    private static Vec3d calculatePlatformPosition(ServerWorld world, int centerX, int centerY, int centerZ,
                                                   int size, String shape) {
        if (shape.equals(SHAPE_SAFE_ROOM)) {
            return new Vec3d(centerX + 0.5, centerY + 1, centerZ + 0.5);
        }
        return new Vec3d(centerX + 0.5, centerY + 1, centerZ + 0.5);
    }

    private static Vec3d getWorldSpawnFallback(SerializableData.Instance data, ServerWorld world, MinecraftServer server) {
        // Try target world spawn first
        BlockPos spawnPos = world.getSpawnPos();
        Vec3d spawnVec = Vec3d.ofBottomCenter(spawnPos);
        if (isPositionActuallySafe(data, world, spawnVec)) {
            return spawnVec;
        }

        // Fall back to overworld spawn
        ServerWorld overworld = server.getOverworld();
        BlockPos overworldSpawn = overworld.getSpawnPos();
        return Vec3d.ofBottomCenter(overworldSpawn);
    }

    // =============== STEP 5: TELEPORTATION ===============
    private static boolean teleportEntityAndMount(Entity entity, ServerWorld targetWorld,
                                                  Vec3d position, boolean bringMount, Entity mount) {
        try {
            // Teleport mount first if applicable
            if (bringMount && mount != null) {
                mount.stopRiding();
                mount.teleport(
                        targetWorld,
                        position.x,
                        position.y,
                        position.z,
                        java.util.Set.of(),
                        mount.getYaw(),
                        mount.getPitch()
                );
            }

            // Teleport main entity
            if (entity instanceof ServerPlayerEntity player) {
                TeleportHelper.teleportPlayer(player, targetWorld, position, player.getYaw(), player.getPitch());
            } else {
                entity.teleport(
                        targetWorld,
                        position.x,
                        position.y,
                        position.z,
                        java.util.Set.of(),
                        entity.getYaw(),
                        entity.getPitch()
                );
            }

            // Re-mount if applicable (for non-player entities)
            if (bringMount && mount != null && !(entity instanceof ServerPlayerEntity)) {
                targetWorld.getServer().execute(() -> {
                    if (entity.isAlive() && mount.isAlive()) {
                        mount.startRiding(entity, true);
                    }
                });
            }
            return true;
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Teleportation failed", e);
            return false;
        }
    }

    // =============== UNIFIED ERROR HANDLER ===============
    private static void handleError(SerializableData.Instance data, Entity entity, ErrorType errorType,
                                    String errorMessage, Vec3d attemptedPos, RegistryKey<World> targetDimension) {
        // 1. Log the error
        FrostedLib.LOGGER.error("[DimensionShift] {}: {}", errorType, errorMessage);

        // 2. Send message to player if enabled
        if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
            String customMsg = data.getString("error_message");
            String messageToSend;
            if (customMsg != null && !customMsg.isEmpty()) {
                messageToSend = customMsg;
            } else {
                // Default messages based on error type
                switch (errorType) {
                    case VALIDATION_ERROR:
                        messageToSend = "Teleport configuration error.";
                        break;
                    case BIOME_NOT_FOUND:
                        messageToSend = "Could not find the target biome!";
                        break;
                    case STRUCTURE_NOT_FOUND:
                        messageToSend = "Could not find the target structure!";
                        break;
                    case NO_SAFE_POSITION:
                        messageToSend = "No safe location to teleport to!";
                        break;
                    case DIMENSION_NOT_FOUND:
                        messageToSend = "Target dimension is not accessible!";
                        break;
                    case TELEPORT_FAILED:
                        messageToSend = "The teleport failed!";
                        break;
                    case STRICT_HEIGHT_VIOLATION:
                        messageToSend = "Could not find position at required height!";
                        break;
                    case RUNTIME_EXCEPTION:
                    default:
                        messageToSend = "An unexpected error occurred.";
                        break;
                }
            }
            player.sendMessage(Text.literal(messageToSend), false);
        }

        // 3. Execute the on_error action if configured
        if (data.isPresent("on_error")) {
            try {
                io.github.apace100.apoli.power.factory.action.ActionFactory<Entity>.Instance errorAction = data.get("on_error");
                errorAction.accept(entity);
            } catch (Exception e) {
                FrostedLib.LOGGER.error("Failed to execute 'on_error' action", e);
            }
        }
    }

    // =============== FACTORY REGISTRATION ===============
    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "dimension_shift"),
                new SerializableData()
                        // Core Targeting
                        .add("target_dimension", SerializableDataTypes.IDENTIFIER, null)
                        .add("target_position", SerializableDataTypes.STRING, MODE_RELATIVE)

                        // Position Mode Parameters
                        .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0)
                        .add("biome_id", SerializableDataTypes.IDENTIFIER, null)
                        .add("biome_search_radius", SerializableDataTypes.INT, 64)
                        .add("structure_id", SerializableDataTypes.IDENTIFIER, null)
                        .add("structure_search_radius", SerializableDataTypes.INT, 64)
                        .add("prefer_spawn_point", SerializableDataTypes.BOOLEAN, true)

                        // Safety & Search
                        .add("target_height", SerializableDataTypes.STRING, HEIGHT_EXPOSED)
                        .add("strict_height", SerializableDataTypes.BOOLEAN, false)
                        .add("search_radius", SerializableDataTypes.INT, 32)
                        .add("max_search_attempts", SerializableDataTypes.INT, 50)
                        .add("random_limit", SerializableDataTypes.DOUBLE, 0.0)

                        // Liquid Safety
                        .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                        .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)

                        // Platform Generation
                        .add("generate_platform", SerializableDataTypes.BOOLEAN, false)
                        .add("platform_block", SerializableDataTypes.IDENTIFIER, Identifier.of("minecraft", "obsidian"))
                        .add("platform_size", SerializableDataTypes.INT, 1)
                        .add("platform_shape", SerializableDataTypes.STRING, SHAPE_SQUARE)

                        // Companions & Feedback
                        .add("bring_mount", SerializableDataTypes.BOOLEAN, true)
                        .add("show_message", SerializableDataTypes.BOOLEAN, false)
                        .add("error_message", SerializableDataTypes.STRING, null)

                        // Error Handling
                        .add("on_error", ApoliDataTypes.ENTITY_ACTION, null),

                DimensionShiftAction::action
        );
    }
}