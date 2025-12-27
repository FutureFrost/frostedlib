package com.futurefrost.frostedlib.util;

import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKeys;

public class PlatformGenerator {

    private static final String SHAPE_CIRCLE = "circle";
    private static final String SHAPE_SQUARE = "square";
    private static final String SHAPE_CROSS = "cross";
    private static final String SHAPE_PLATFORM_ONLY = "platform_only";
    private static final String SHAPE_SAFE_ROOM = "safe_room";

    private static final String HEIGHT_EXPOSED = "exposed";
    private static final String HEIGHT_UNEXPOSED = "unexposed";

    public Vec3d generatePlatformAtPosition(SerializableData.Instance data, ServerWorld world,
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

        // Generate the platform
        generatePlatform(world, centerX, platformY, centerZ, platformSize, platformShape, platformBlock);

        // Calculate safe position on platform
        return calculatePlatformPosition(world, centerX, platformY, centerZ, platformSize, platformShape);
    }

    private int findPlatformY(SerializableData.Instance data, ServerWorld world, int x, int z,
                              double preferredY, String heightMode, boolean strictHeight, boolean forcePlatform) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        // First, check if we're over liquid
        PositionFinder positionFinder = new PositionFinder();
        boolean overLiquid = positionFinder.isOverLiquidSurface(data, world, x, z);

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
            int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            return new Vec3d(x + 0.5, surfaceY, z + 0.5);
        } catch (Exception e) {
            return new Vec3d(x + 0.5, world.getSeaLevel(), z + 0.5);
        }
    }

    private int findTrueLiquidSurface(ServerWorld world, int x, int z) {
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

    public void generatePlatform(ServerWorld world, int centerX, int centerY, int centerZ,
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

    private void generateCircularPlatform(ServerWorld world, int centerX, int centerY, int centerZ,
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

    private void generateSquarePlatform(ServerWorld world, int centerX, int centerY, int centerZ,
                                        int radius, BlockState block) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ + dz), block);
            }
        }
    }

    private void generateCrossPlatform(ServerWorld world, int centerX, int centerY, int centerZ,
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

    private void generateSafeRoom(ServerWorld world, int centerX, int centerY, int centerZ,
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

    private Vec3d calculatePlatformPosition(ServerWorld world, int centerX, int centerY, int centerZ,
                                            int size, String shape) {
        if (shape.equals(SHAPE_SAFE_ROOM)) {
            return new Vec3d(centerX + 0.5, centerY + 1, centerZ + 0.5);
        }
        return new Vec3d(centerX + 0.5, centerY + 1, centerZ + 0.5);
    }
}