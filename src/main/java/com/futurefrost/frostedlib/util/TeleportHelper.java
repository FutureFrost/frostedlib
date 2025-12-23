package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TeleportHelper {

    /**
     * Safely teleports a player to a position
     */
    public static boolean teleportPlayer(ServerPlayerEntity player, ServerWorld targetWorld, Vec3d targetPos, float yaw, float pitch) {
        if (player == null || targetWorld == null) {
            return false;
        }

        try {
            // In Minecraft 1.20+, use the teleport method with world and position
            player.teleport(
                    targetWorld,
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    java.util.Set.of(), // No special flags
                    yaw,
                    pitch
            );

            return true;
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Failed to teleport player", e);
            return false;
        }
    }

    /**
     * Finds a safe Y position at given X,Z coordinates
     * Returns the highest safe position (air at feet and head level)
     */
    public static BlockPos findSafeYPosition(ServerWorld world, int x, int z, int preferredY) {
        // Start search from world height
        int maxY = world.getTopY();
        int minY = world.getBottomY();

        // Try preferred Y first
        if (isPositionSafe(world, x, preferredY, z)) {
            return new BlockPos(x, preferredY, z);
        }

        // Search upwards from preferred Y
        for (int y = preferredY + 1; y <= maxY - 1; y++) {
            if (isPositionSafe(world, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }

        // Search downwards from preferred Y
        for (int y = preferredY - 1; y >= minY + 1; y--) {
            if (isPositionSafe(world, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }

        // If no safe position found, return world spawn
        return world.getSpawnPos();
    }

    /**
     * Checks if a position is safe for a player to stand
     */
    private static boolean isPositionSafe(ServerWorld world, int x, int y, int z) {
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockPos groundPos = new BlockPos(x, y - 1, z);

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        // Feet and head must be air or replaceable
        // In 1.20+, use canPathfindThrough or isReplaceable
        boolean feetSafe = feetState.isAir() || feetState.isReplaceable();
        boolean headSafe = headState.isAir() || headState.isReplaceable();

        // Ground must be solid
        boolean groundSolid = groundState.isSolidBlock(world, groundPos);

        return feetSafe && headSafe && groundSolid;
    }

    /**
     * Gets the surface Y position at given X,Z
     */
    public static int getSurfaceY(ServerWorld world, int x, int z) {
        // Get the highest non-air block at this X,Z
        BlockPos topPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        return topPos.getY() + 1; // +1 to stand on top of the block
    }
}