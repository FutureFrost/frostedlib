package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.RegistryKeys;

import java.util.*;

public class PositionFinder {

    private static final String HEIGHT_EXPOSED = "exposed";
    private static final String HEIGHT_UNEXPOSED = "unexposed";
    private static final String HEIGHT_RELATIVE = "relative";
    private static final String HEIGHT_FIXED = "fixed";

    public Vec3d findSafePosition(SerializableData.Instance data, Entity entity,
                                  ServerWorld world, int centerX, int centerZ) {
        String heightMode = data.getString("target_height");
        if (heightMode == null) {
            // Should not happen as each action sets a default
            heightMode = HEIGHT_EXPOSED;
        }

        // Get preferred Y based on height mode
        double preferredY;
        if (heightMode.equals(HEIGHT_FIXED)) {
            // For fixed mode, use target_y as preferred Y
            preferredY = data.getDouble("target_y");
        } else if (heightMode.equals(HEIGHT_RELATIVE)) {
            // For relative mode, check if target_y is specified
            Double targetY = data.get("target_y");
            if (targetY != null) {
                preferredY = targetY;
            } else {
                preferredY = entity.getY();
            }
        } else {
            // For exposed/unexposed, use entity's current Y
            preferredY = entity.getY();
        }

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

        // STAGE 3: Platform generation
        boolean isOverLiquid = isOverLiquidSurface(data, world, centerX, centerZ);
        if (generatePlatform || isOverLiquid) {
            PlatformGenerator platformGenerator = new PlatformGenerator();
            Vec3d platformPos = platformGenerator.generatePlatformAtPosition(data, world, centerX, centerZ,
                    heightMode, preferredY, strictHeight, isOverLiquid);
            if (platformPos != null) return platformPos;
        }

        // STAGE 4: Final fallbacks
        if (strictHeight) {
            return null; // Can't find position with strict height requirements
        }

        // STAGE 5: Non-strict fallback to opposite height
        return getOppositeHeightFallback(data, world, centerX, centerZ, heightMode, preferredY);
    }

    public Vec3d findSafeHeightPosition(SerializableData.Instance data, ServerWorld world,
                                        int x, int z, String mode, double preferredY, boolean strictHeight) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        if (mode.equals(HEIGHT_FIXED)) {
            // FIXED mode: try to get as close to preferredY (target_y) as possible
            int targetY = (int) preferredY;

            // First try the exact target Y
            if (isPositionSafeForEntity(data, world, x, targetY, z)) {
                // Check for unsafe liquids
                BlockPos testPos = new BlockPos(x, targetY, z);
                if (isBlockUnsafeLiquid(data, world, testPos) ||
                        isBlockUnsafeLiquid(data, world, testPos.down())) {
                    // Position is in unsafe liquid, continue searching
                } else {
                    return new Vec3d(x + 0.5, targetY, z + 0.5);
                }
            }

            // Search upward and downward from target Y
            for (int offset = 1; offset < 64; offset++) {
                // Try above
                int yAbove = targetY + offset;
                if (yAbove <= worldTop) {
                    BlockPos testPos = new BlockPos(x, yAbove, z);
                    if (isPositionSafeForEntity(data, world, x, yAbove, z)) {
                        // Check for unsafe liquids
                        if (!isBlockUnsafeLiquid(data, world, testPos) &&
                                !isBlockUnsafeLiquid(data, world, testPos.down())) {
                            return new Vec3d(x + 0.5, yAbove, z + 0.5);
                        }
                    }
                }

                // Try below
                int yBelow = targetY - offset;
                if (yBelow >= worldBottom) {
                    BlockPos testPos = new BlockPos(x, yBelow, z);
                    if (isPositionSafeForEntity(data, world, x, yBelow, z)) {
                        // Check for unsafe liquids
                        if (!isBlockUnsafeLiquid(data, world, testPos) &&
                                !isBlockUnsafeLiquid(data, world, testPos.down())) {
                            return new Vec3d(x + 0.5, yBelow, z + 0.5);
                        }
                    }
                }
            }

            // If strict height is enabled and we can't find position near targetY, fail
            if (strictHeight) {
                return null;
            }

            // Otherwise fall back to surface
            return getSurfacePosition(data, world, x, z);

        } else if (mode.equals(HEIGHT_UNEXPOSED)) {
            // UNEXPOSED mode: Search downward from preferred Y for cave position
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
            // RELATIVE mode: Search around preferred Y
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
            // EXPOSED mode (default)
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

    private Vec3d getSurfacePosition(SerializableData.Instance data, ServerWorld world, int x, int z) {
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
            int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            return new Vec3d(x + 0.5, surfaceY, z + 0.5);
        } catch (Exception e) {
            return new Vec3d(x + 0.5, world.getSeaLevel(), z + 0.5);
        }
    }

    private Vec3d findSafePositionExpandingSearch(SerializableData.Instance data, ServerWorld world, int centerX, int centerZ,
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

            // For fixed and relative modes, also check different Y levels
            if ((heightMode.equals(HEIGHT_FIXED) || heightMode.equals(HEIGHT_RELATIVE)) && totalAttempts < maxAttempts) {
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

    private Vec3d getOppositeHeightFallback(SerializableData.Instance data, ServerWorld world,
                                            int x, int z, String originalMode, double preferredY) {
        if (originalMode.equals(HEIGHT_EXPOSED) || originalMode.equals(HEIGHT_FIXED)) {
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

    private boolean isPositionSafeForEntity(SerializableData.Instance data, ServerWorld world,
                                            int x, int y, int z) {
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

    private boolean isPositionActuallySafe(SerializableData.Instance data, ServerWorld world, Vec3d pos) {
        return isPositionSafeForEntity(data, world, (int) pos.x, (int) pos.y, (int) pos.z);
    }

    private boolean isBlockUnsafeLiquid(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return !isLiquidSafe(data, world, pos);
        }
        return false;
    }

    private boolean isLiquidSafe(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        boolean liquidsSafe = data.getBoolean("liquids_safe");
        if (liquidsSafe) {
            return true; // All liquids are safe
        }

        // Get the fluid predicate from data
        io.github.apace100.calio.data.SerializableData.Instance fluidPredicate = data.get("liquid_condition");
        if (fluidPredicate == null) {
            return false; // No specific condition, all liquids are unsafe
        }

        // Check if this fluid matches the predicate
        try {
            net.minecraft.fluid.FluidState fluidState = world.getFluidState(pos);
            if (fluidState.isEmpty()) {
                return true; // No fluid is always safe
            }

            net.minecraft.fluid.Fluid fluid = fluidState.getFluid();

            // Default unsafe fluids (when no condition is specified)
            if (fluid == Fluids.WATER ||
                    fluid == Fluids.FLOWING_WATER ||
                    fluid == Fluids.LAVA ||
                    fluid == Fluids.FLOWING_LAVA) {
                return false;
            }

            return true; // Other fluids are safe by default
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Error checking liquid safety", e);
            return false; // On error, assume unsafe
        }
    }

    public boolean isOverLiquidSurface(SerializableData.Instance data, ServerWorld world, int x, int z) {
        // Check if the top block at this position is liquid
        BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        BlockState state = world.getBlockState(topPos);
        return !state.getFluidState().isEmpty();
    }

    private int findTrueLiquidSurface(ServerWorld world, int x, int z) {
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
}