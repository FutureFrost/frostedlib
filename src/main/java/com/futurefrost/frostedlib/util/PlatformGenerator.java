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

    public Vec3d generatePlatformAtPosition(SerializableData.Instance data, ServerWorld world,
                                            int centerX, int centerZ, int platformY) {
        // Get platform configuration
        Identifier platformBlockId = data.getId("platform_block");
        BlockState platformBlock = platformBlockId != null ?
                Objects.requireNonNull(world.getRegistryManager().get(RegistryKeys.BLOCK).get(platformBlockId)).getDefaultState() :
                Blocks.OBSIDIAN.getDefaultState();

        int platformSize = Math.min(data.getInt("platform_size"), 16);
        if (platformSize <= 0) platformSize = 3;

        String platformShape = data.getString("platform_shape");
        if (platformShape == null) platformShape = SHAPE_CIRCLE;

        // Generate the platform
        generatePlatform(world, centerX, platformY, centerZ, platformSize, platformShape, platformBlock);

        // Calculate safe position on platform
        return calculatePlatformPosition(centerX, platformY, centerZ);
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

    private void generateCircularPlatform(ServerWorld world, int centerX, int centerY,
                                          int centerZ, int radius, BlockState block) {
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radiusSq) {
                    world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ + dz), block);
                }
            }
        }
    }

    private void generateSquarePlatform(ServerWorld world, int centerX, int centerY,
                                        int centerZ, int radius, BlockState block) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ + dz), block);
            }
        }
    }

    private void generateCrossPlatform(ServerWorld world, int centerX, int centerY,
                                       int centerZ, int radius, BlockState block) {
        // Horizontal line
        for (int dx = -radius; dx <= radius; dx++) {
            world.setBlockState(new BlockPos(centerX + dx, centerY, centerZ), block);
        }
        // Vertical line
        for (int dz = -radius; dz <= radius; dz++) {
            world.setBlockState(new BlockPos(centerX, centerY, centerZ + dz), block);
        }
    }

    private void generateSafeRoom(ServerWorld world, int centerX, int centerY,
                                  int centerZ, int size, BlockState block) {
        int height = size * 2;

        // Floor
        generateSquarePlatform(world, centerX, centerY, centerZ, size, block);

        // Ceiling
        generateSquarePlatform(world, centerX, centerY + height, centerZ, size, block);

        // Walls
        for (int y = centerY + 1; y < centerY + height; y++) {
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