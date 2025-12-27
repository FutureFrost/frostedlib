package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.util.TeleportHelper;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class MountHandler {

    public boolean teleportWithMount(Entity entity, ServerWorld targetWorld,
                                     Vec3d position, boolean bringMount) {
        try {
            Entity mount = bringMount ? entity.getVehicle() : null;

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
}