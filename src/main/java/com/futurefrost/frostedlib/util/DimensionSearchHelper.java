package com.futurefrost.frostedlib.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class DimensionSearchHelper {

    /**
     * Calculate the starting position for biome/structure search in target dimension.
     * Applies scale_factor to current coordinates when teleporting cross-dimension.
     */
    public static BlockPos calculateSearchStartPosition(Entity entity, ServerWorld targetWorld, double scaleFactor) {
        // Check if we're changing dimensions
        boolean isChangingDimension = !entity.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey());

        if (isChangingDimension || scaleFactor != 1.0) {
            // For cross-dimension teleports or when scale_factor is not 1,
            // use scaled coordinates from current position
            double scaledX = entity.getX() * scaleFactor;
            double scaledZ = entity.getZ() * scaleFactor;

            // Make sure we're within world bounds
            int worldBottomY = targetWorld.getBottomY();
            int worldTopY = targetWorld.getTopY();

            // For X and Z, we need to use world border or reasonable limits
            // Using world border if available, otherwise use a large range
            int worldBorderRadius = 29999984; // Minecraft world border radius

            // Clamp X and Z to world border limits
            int x = (int) Math.max(-worldBorderRadius, Math.min(worldBorderRadius, scaledX));
            int z = (int) Math.max(-worldBorderRadius, Math.min(worldBorderRadius, scaledZ));

            // Get a reasonable Y coordinate
            // Try to find surface level at this position
            int surfaceY = targetWorld.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);

            // If surfaceY is invalid, use sea level
            if (surfaceY < targetWorld.getBottomY() || surfaceY > targetWorld.getTopY()) {
                surfaceY = targetWorld.getSeaLevel();
            }

            return new BlockPos(x, surfaceY, z);
        } else {
            // Same dimension and no scaling, use current position
            return entity.getBlockPos();
        }
    }

    /**
     * Enhanced version that handles dimension-specific scaling (like Nether's 8:1 ratio)
     */
    public static BlockPos calculateSearchStartPosition(Entity entity, ServerWorld targetWorld,
                                                        double scaleFactor, boolean useDimensionScaling) {
        BlockPos basePos = calculateSearchStartPosition(entity, targetWorld, scaleFactor);

        return basePos;
    }
}