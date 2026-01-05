package com.futurefrost.frostedlib.util;

import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKeys;

import java.util.Objects;

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
                Objects.requireNonNull(world.getRegistryManager().get(RegistryKeys.BLOCK).get(platformBlockId)).getDefaultState() :
                Blocks.OBSIDIAN.getDefaultState();

        int platformSize = Math.min(data.getInt("platform_size"), 16);
        if (platformSize <= 0) platformSize = 3;

        String platformShape = data.getString("platform_shape");
        if (platformShape == null) platformShape = SHAPE_CIRCLE;

        // Find Y position for platform
        int platformY = findPlatformY(world, centerX, centerZ, preferredY, heightMode, strictHeight, forcePlatform);

        // Generate the platform
        generatePlatform(world, centerX, platformY, centerZ, platformSize, platformShape, platformBlock);

        // Calculate safe position on platform
        return calculatePlatformPosition(centerX, platformY, centerZ);
    }

    private int findPlatformY(ServerWorld world, int x, int z,
                              double preferredY, String heightMode, boolean strictHeight, boolean forcePlatform) {
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY();

        // First, check if we're over liquid
        PositionFinder positionFinder = new PositionFinder();
        boolean overLiquid = positionFinder.isOverLiquidSurface(world, x, z);

        if (heightMode.equals(HEIGHT_EXPOSED)) {
            if (overLiquid || forcePlatform) {
                // Over liquid or forced platform - place at liquid surface
                int liquidSurfaceY = PositionFinder.findTrueLiquidSurface(world, x, z);
                if (liquidSurfaceY != -1) {
                    return liquidSurfaceY - 1; // Platform at liquid surface
                }
            }

            // For exposed, place at surface
            Vec3d surfacePos = positionFinder.getSurfacePosition(world, x, z);
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
            Vec3d surfacePos = positionFinder.getSurfacePosition(world, x, z);
            return (int) surfacePos.y - 1;
        } else {
            // HEIGHT_RELATIVE or default
            Vec3d surfacePos = positionFinder.getSurfacePosition(world, x, z);
            return (int) surfacePos.y - 1;
        }
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

    private Vec3d calculatePlatformPosition(int centerX, int centerY, int centerZ) {
        return new Vec3d(centerX + 0.5, centerY + 1, centerZ + 0.5);
    }
}