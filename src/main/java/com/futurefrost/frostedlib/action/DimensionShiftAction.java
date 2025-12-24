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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class DimensionShiftAction {

    // Enum for target position mode
    public static final String MODE_RELATIVE = "relative";
    public static final String MODE_FIXED = "fixed";

    // Enum for target height mode
    public static final String HEIGHT_EXPOSED = "exposed";
    public static final String HEIGHT_UNEXPOSED = "unexposed";
    public static final String HEIGHT_RELATIVE = "relative";

    // Enum for platform shape
    public static final String SHAPE_CIRCLE = "circle";
    public static final String SHAPE_SQUARE = "square";
    public static final String SHAPE_CROSS = "cross";
    public static final String SHAPE_PLATFORM_ONLY = "platform_only";
    public static final String SHAPE_SAFE_ROOM = "safe_room";

    public static void action(SerializableData.Instance data, Entity entity) {
        if (entity.getServer() == null) return;

        try {
            // 1. Identify target dimension (default to current dimension)
            RegistryKey<World> targetDimension = getTargetDimension(data, entity);
            ServerWorld targetWorld = entity.getServer().getWorld(targetDimension);

            if (targetWorld == null) {
                logError(entity, "Target dimension not found: " + targetDimension.getValue());
                return;
            }

            // 2. Identify target position
            Vec3d baseTargetPos = calculateBaseTargetPosition(data, entity, targetWorld);

            // 3. Apply random offset
            Vec3d randomizedPos = applyRandomOffset(data, baseTargetPos, targetWorld);

            // 4. Find safe position with multi-stage search
            Vec3d finalPosition = findSafePositionWithFallbacks(
                    data, entity, targetWorld,
                    (int)randomizedPos.x, (int)randomizedPos.z
            );

            // 5. Check if we're actually changing dimensions
            boolean isChangingDimension = !entity.getWorld().getRegistryKey().equals(targetDimension);

            // 6. Bring mount along if applicable
            Entity mount = entity.getVehicle();
            boolean bringMount = data.getBoolean("bring_mount") && mount != null;

            // 7. Teleport entity (and mount)
            teleportEntityAndMount(entity, targetWorld, finalPosition, bringMount, mount);

            // 8. Send feedback if enabled
            if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
                String message = isChangingDimension ?
                        "Dimension shifted to " + targetDimension.getValue() :
                        "Teleported within " + targetDimension.getValue();
                player.sendMessage(Text.literal(message), false);
            }

        } catch (Exception e) {
            FrostedLib.LOGGER.error("Error in dimension_shift action", e);
            if (entity instanceof ServerPlayerEntity player) {
                player.sendMessage(Text.literal("Dimension shift failed!"), false);
            }
        }
    }

    // =============== POSITION SEARCH SYSTEM ===============

    // Multi-stage position search with all fallbacks
    private static Vec3d findSafePositionWithFallbacks(SerializableData.Instance data, Entity entity,
                                                       ServerWorld world, int centerX, int centerZ) {
        String heightMode = data.getString("target_height");
        if (heightMode == null) heightMode = HEIGHT_EXPOSED;

        double preferredY = entity.getY();

        // STAGE 2: Multi-stage expanding search for safe position
        int maxSearchRadius = Math.min(data.getInt("search_radius"), 128); // Cap at 128 for performance
        if (maxSearchRadius <= 0) maxSearchRadius = 32;

        int maxSearchAttempts = data.getInt("max_search_attempts");
        if (maxSearchAttempts <= 0) maxSearchAttempts = 50;

        // Try exact position first (fastest check)
        Optional<Vec3d> exactPos = tryFindSafePositionAt(world, centerX, centerZ, heightMode, preferredY);
        if (exactPos.isPresent()) {
            return exactPos.get();
        }

        // Expanding search with exponential steps
        Optional<Vec3d> expandingPos = findSafePositionExpandingSearch(
                world, centerX, centerZ, heightMode, preferredY, maxSearchRadius, maxSearchAttempts
        );

        if (expandingPos.isPresent()) {
            return expandingPos.get();
        }

        // STAGE 3: Platform generation as fallback
        boolean generatePlatform = data.getBoolean("generate_platform");
        if (generatePlatform) {
            Optional<Vec3d> platformPos = generatePlatformAtPosition(
                    data, world, centerX, centerZ, heightMode, preferredY
            );
            if (platformPos.isPresent()) {
                return platformPos.get();
            }
        }

        // STAGE 4: Final fallbacks
        return getWorldSpawnFallback(world, entity.getServer());
    }

    // Try find safe position at specific X,Z
    private static Optional<Vec3d> tryFindSafePositionAt(ServerWorld world, int x, int z,
                                                         String heightMode, double preferredY) {
        Vec3d pos = findSafeHeightPosition(world, x, z, heightMode, preferredY);
        if (isPositionActuallySafe(world, pos)) {
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    // Expanding search with attempt limits and exponential radius steps
    private static Optional<Vec3d> findSafePositionExpandingSearch(ServerWorld world, int centerX, int centerZ,
                                                                   String heightMode, double preferredY,
                                                                   int maxRadius, int maxAttempts) {

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
            int circumference = (int)(2 * Math.PI * radius);
            int points = Math.min(circumference / 4, 16); // Scale with radius, max 16
            points = Math.max(points, 4); // Minimum 4 points

            // Test points around the circle
            for (int i = 0; i < points; i++) {
                if (totalAttempts >= maxAttempts) break;

                double angle = 2 * Math.PI * i / points;
                int x = centerX + (int)(radius * Math.cos(angle));
                int z = centerZ + (int)(radius * Math.sin(angle));

                Optional<Vec3d> testPos = tryFindSafePositionAt(world, x, z, heightMode, preferredY);
                if (testPos.isPresent()) {
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
                        int x = centerX + (int)(radius * Math.cos(angle));
                        int z = centerZ + (int)(radius * Math.sin(angle));

                        Optional<Vec3d> testPos = tryFindSafePositionAt(world, x, z, heightMode, testY);
                        if (testPos.isPresent()) {
                            return testPos;
                        }
                        totalAttempts++;
                    }
                }
            }
        }

        return Optional.empty();
    }

    // Get positions at a specific radius (circle approximation)
    private static List<BlockPos> getPositionsAtRadius(int centerX, int centerZ, int radius) {
        List<BlockPos> positions = new ArrayList<>();

        if (radius == 0) {
            positions.add(new BlockPos(centerX, 0, centerZ));
            return positions;
        }

        // Bresenham's circle algorithm
        int x = radius;
        int z = 0;
        int decisionOver2 = 1 - x;

        while (z <= x) {
            positions.add(new BlockPos(centerX + x, 0, centerZ + z));
            positions.add(new BlockPos(centerX - x, 0, centerZ + z));
            positions.add(new BlockPos(centerX + x, 0, centerZ - z));
            positions.add(new BlockPos(centerX - x, 0, centerZ - z));
            positions.add(new BlockPos(centerX + z, 0, centerZ + x));
            positions.add(new BlockPos(centerX - z, 0, centerZ + x));
            positions.add(new BlockPos(centerX + z, 0, centerZ - x));
            positions.add(new BlockPos(centerX - z, 0, centerZ - x));

            z++;
            if (decisionOver2 <= 0) {
                decisionOver2 += 2 * z + 1;
            } else {
                x--;
                decisionOver2 += 2 * (z - x) + 1;
            }
        }

        return positions;
    }

    // =============== PLATFORM GENERATION SYSTEM ===============

    private static Optional<Vec3d> generatePlatformAtPosition(SerializableData.Instance data, ServerWorld world,
                                                              int centerX, int centerZ, String heightMode,
                                                              double preferredY) {
        // Get platform configuration
        Identifier platformBlockId = data.getId("platform_block");
        BlockState platformBlock = platformBlockId != null ?
                world.getRegistryManager().get(RegistryKeys.BLOCK).get(platformBlockId).getDefaultState() :
                Blocks.OBSIDIAN.getDefaultState();

        int platformSize = Math.min(data.getInt("platform_size"), 16); // Cap size
        if (platformSize <= 0) platformSize = 3;

        String platformShape = data.getString("platform_shape");
        if (platformShape == null) platformShape = SHAPE_CIRCLE;

        // Find Y position for platform
        int platformY = findPlatformY(world, centerX, centerZ, preferredY, heightMode);

        // Generate the platform
        generatePlatform(world, centerX, platformY, centerZ, platformSize, platformShape, platformBlock);

        // Calculate safe position on platform
        Vec3d platformPos = calculatePlatformPosition(world, centerX, platformY, centerZ, platformSize, platformShape);

        return Optional.of(platformPos);
    }

    private static int findPlatformY(ServerWorld world, int x, int z, double preferredY, String heightMode) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        // Try to find existing solid ground
        for (int y = (int)preferredY; y >= worldBottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isSolidBlock(world, pos) && y < worldTop - 2) {
                return y + 1; // Platform on top of solid ground
            }
        }

        // If no ground found, create in void at appropriate height
        if (heightMode.equals(HEIGHT_UNEXPOSED)) {
            return Math.max(worldBottom + 10, (int)preferredY);
        } else if (heightMode.equals(HEIGHT_RELATIVE)) {
            return Math.max(worldBottom + 5, Math.min((int)preferredY, worldTop - 5));
        } else {
            return world.getSeaLevel();
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

    // =============== CORE TELEPORTATION LOGIC ===============

    private static RegistryKey<World> getTargetDimension(SerializableData.Instance data, Entity entity) {
        if (data.isPresent("target_dimension")) {
            Identifier dimId = data.getId("target_dimension");
            return RegistryKey.of(RegistryKeys.WORLD, dimId);
        }
        // Default: Current dimension
        return entity.getWorld().getRegistryKey();
    }

    private static Vec3d calculateBaseTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        String mode = data.getString("target_position");
        if (mode == null) mode = MODE_RELATIVE;

        if (mode.equals(MODE_FIXED)) {
            BlockPos spawnPos = targetWorld.getSpawnPos();
            return new Vec3d(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        } else {
            // RELATIVE mode
            double scale = data.getDouble("scale_factor");
            double scaledX = entity.getX() * scale;
            double scaledZ = entity.getZ() * scale;
            return new Vec3d(scaledX, entity.getY(), scaledZ);
        }
    }

    private static Vec3d applyRandomOffset(SerializableData.Instance data, Vec3d basePos, ServerWorld world) {
        double randomLimit = data.getDouble("random_limit");
        if (randomLimit <= 0) return basePos;

        Random random = new Random();
        double offsetX = (random.nextDouble() * 2 - 1) * randomLimit;
        double offsetZ = (random.nextDouble() * 2 - 1) * randomLimit;
        return new Vec3d(basePos.x + offsetX, basePos.y, basePos.z + offsetZ);
    }

    private static Vec3d findSafeHeightPosition(ServerWorld world, int x, int z,
                                                String mode, double preferredY) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        if (mode.equals(HEIGHT_UNEXPOSED)) {
            // Search downward from preferred Y for cave position
            int startY = Math.min((int)preferredY, worldTop - 10);
            for (int y = startY; y >= worldBottom; y--) {
                if (isPositionSafeForEntity(world, x, y, z) &&
                        !world.isSkyVisible(new BlockPos(x, y, z))) {
                    return new Vec3d(x + 0.5, y, z + 0.5);
                }
            }
            // Fallback to surface
            return getSurfacePosition(world, x, z);
        } else if (mode.equals(HEIGHT_RELATIVE)) {
            // Search around preferred Y
            int centerY = (int)preferredY;
            for (int offset = 0; offset < 64; offset++) {
                // Check above
                int testYAbove = centerY + offset;
                if (testYAbove <= worldTop && isPositionSafeForEntity(world, x, testYAbove, z)) {
                    return new Vec3d(x + 0.5, testYAbove, z + 0.5);
                }

                // Check below
                int testYBelow = centerY - offset;
                if (testYBelow >= worldBottom && isPositionSafeForEntity(world, x, testYBelow, z)) {
                    return new Vec3d(x + 0.5, testYBelow, z + 0.5);
                }
            }
            // Fallback to surface
            return getSurfacePosition(world, x, z);
        } else {
            // EXPOSED (default)
            return getSurfacePosition(world, x, z);
        }
    }

    private static Vec3d getSurfacePosition(ServerWorld world, int x, int z) {
        try {
            int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            return new Vec3d(x + 0.5, surfaceY, z + 0.5);
        } catch (Exception e) {
            return new Vec3d(x + 0.5, world.getSeaLevel(), z + 0.5);
        }
    }

    private static boolean isPositionSafeForEntity(ServerWorld world, int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) return false;

        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockPos groundPos = new BlockPos(x, y - 1, z);

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        // Check if blocks are passable
        boolean feetSafe = feetState.isAir() || !feetState.isOpaque();
        boolean headSafe = headState.isAir() || !headState.isOpaque();
        boolean groundSolid = groundState.isSolidBlock(world, groundPos) || y <= world.getBottomY() + 1;

        return feetSafe && headSafe && groundSolid;
    }

    private static boolean isPositionActuallySafe(ServerWorld world, Vec3d pos) {
        return isPositionSafeForEntity(world, (int)pos.x, (int)pos.y, (int)pos.z);
    }

    private static Vec3d getWorldSpawnFallback(ServerWorld world, MinecraftServer server) {
        // Try target world spawn first
        BlockPos spawnPos = world.getSpawnPos();
        if (isPositionActuallySafe(world, Vec3d.ofBottomCenter(spawnPos))) {
            return Vec3d.ofBottomCenter(spawnPos);
        }

        // Fall back to overworld spawn
        ServerWorld overworld = server.getOverworld();
        BlockPos overworldSpawn = overworld.getSpawnPos();
        return Vec3d.ofBottomCenter(overworldSpawn);
    }

    private static void teleportEntityAndMount(Entity entity, ServerWorld targetWorld,
                                               Vec3d position, boolean bringMount, Entity mount) {
        // Teleport mount first if bringing it along
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
            TeleportHelper.teleportPlayer(
                    player,
                    targetWorld,
                    position,
                    player.getYaw(),
                    player.getPitch()
            );
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
    }

    private static void logError(Entity entity, String message) {
        FrostedLib.LOGGER.error(message);
        if (entity instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("Error: " + message), false);
        }
    }

    // =============== FACTORY REGISTRATION ===============

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "dimension_shift"),
                new SerializableData()
                        // Target dimension (defaults to current dimension)
                        .add("target_dimension", SerializableDataTypes.IDENTIFIER, null)

                        // Position mode and settings
                        .add("target_position", SerializableDataTypes.STRING, MODE_RELATIVE)
                        .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0)

                        // Height mode
                        .add("target_height", SerializableDataTypes.STRING, HEIGHT_EXPOSED)

                        // Search limits (PERFORMANCE CONTROL)
                        .add("search_radius", SerializableDataTypes.INT, 32)
                        .add("max_search_attempts", SerializableDataTypes.INT, 50)

                        // Random offset
                        .add("random_limit", SerializableDataTypes.DOUBLE, 0.0)

                        // Platform generation
                        .add("generate_platform", SerializableDataTypes.BOOLEAN, false)
                        .add("platform_block", SerializableDataTypes.IDENTIFIER,
                                new Identifier("minecraft", "obsidian"))
                        .add("platform_size", SerializableDataTypes.INT, 1)
                        .add("platform_shape", SerializableDataTypes.STRING, SHAPE_SQUARE)

                        // Mount handling
                        .add("bring_mount", SerializableDataTypes.BOOLEAN, true)

                        // Feedback
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),

                DimensionShiftAction::action
        );
    }
}