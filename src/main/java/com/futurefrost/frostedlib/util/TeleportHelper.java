package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class TeleportHelper {

    // Safely teleports a player to a position
    public static boolean teleportPlayer(ServerPlayerEntity player, ServerWorld targetWorld, Vec3d targetPos, float yaw, float pitch) {
        if (player == null || targetWorld == null) {
            return false;
        }

        try {
            player.teleport(
                    targetWorld,
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    java.util.Set.of(),
                    yaw,
                    pitch
            );
            return true;
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Failed to teleport player", e);
            return false;
        }
    }
}