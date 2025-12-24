package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.util.TeleportHelper;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

public class FakeRespawnAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) return;

        boolean prioritizeSetSpawn = data.getBoolean("prioritize_set_spawn");
        boolean showMessage = data.getBoolean("show_message");

        ServerWorld targetWorld;
        Vec3d spawnLocation;
        String spawnType;

        if (entity instanceof ServerPlayerEntity player) {
            // PLAYER LOGIC
            RegistryKey<World> spawnDimension = player.getSpawnPointDimension();
            BlockPos spawnBlockPos = player.getSpawnPointPosition();

            if (prioritizeSetSpawn) {
                spawnType = "set spawn";
                boolean hasValidSetSpawn = false;
                targetWorld = null;

                if (spawnBlockPos != null && spawnDimension != null) {
                    targetWorld = server.getWorld(spawnDimension);

                    if (targetWorld != null) {
                        if (spawnDimension == World.OVERWORLD) {
                            BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                            boolean isBed = blockState.getBlock() instanceof BedBlock;
                            boolean isWorldSpawn = spawnBlockPos.equals(targetWorld.getSpawnPos());
                            hasValidSetSpawn = isBed && !isWorldSpawn;

                            if (!hasValidSetSpawn) {
                                spawnType = "world spawn (no valid bed)";
                            }
                        } else if (spawnDimension == World.NETHER) {
                            BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                            boolean isRespawnAnchor = blockState.getBlock() instanceof RespawnAnchorBlock;
                            boolean isCharged = isRespawnAnchor && blockState.get(RespawnAnchorBlock.CHARGES) > 0;
                            hasValidSetSpawn = isRespawnAnchor && isCharged;

                            if (!hasValidSetSpawn) {
                                spawnType = "world spawn (no valid anchor)";
                            }
                        } else {
                            hasValidSetSpawn = true;
                        }
                    }
                }

                if (!hasValidSetSpawn || targetWorld == null) {
                    targetWorld = server.getOverworld();
                    spawnBlockPos = targetWorld.getSpawnPos();
                    spawnType = "world spawn";
                }

                if (hasValidSetSpawn && spawnBlockPos != null) {
                    Optional<Vec3d> safeSpawn = net.minecraft.entity.player.PlayerEntity.findRespawnPosition(
                            targetWorld,
                            spawnBlockPos,
                            player.getSpawnAngle(),
                            false,
                            true
                    );

                    if (safeSpawn.isPresent()) {
                        spawnLocation = safeSpawn.get();

                        if (spawnDimension == World.OVERWORLD) {
                            BlockState checkState = targetWorld.getBlockState(spawnBlockPos);
                            if (!(checkState.getBlock() instanceof BedBlock)) {
                                targetWorld = server.getOverworld();
                                spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
                                spawnType = "world spawn (bed missing)";
                            }
                        } else if (spawnDimension == World.NETHER) {
                            BlockState checkState = targetWorld.getBlockState(spawnBlockPos);
                            if (!(checkState.getBlock() instanceof RespawnAnchorBlock) ||
                                    checkState.get(RespawnAnchorBlock.CHARGES) <= 0) {
                                targetWorld = server.getOverworld();
                                spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
                                spawnType = "world spawn (anchor missing/uncharged)";
                            }
                        }
                    } else {
                        targetWorld = server.getOverworld();
                        spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
                        spawnType = "world spawn (spawn obstructed)";
                    }
                } else {
                    spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
                }

                // Teleport player
                boolean success = TeleportHelper.teleportPlayer(
                        player,
                        targetWorld,
                        spawnLocation,
                        player.getSpawnAngle(),
                        0.0f
                );

                if (success && showMessage) {
                    player.sendMessage(
                            Text.literal("Teleported to " + spawnType +
                                    " at " + String.format("%.1f, %.1f, %.1f", spawnLocation.x, spawnLocation.y, spawnLocation.z) +
                                    " in " + targetWorld.getRegistryKey().getValue()),
                            false
                    );
                }
            } else {
                // World spawn only for player
                targetWorld = server.getOverworld();
                spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());

                boolean success = TeleportHelper.teleportPlayer(
                        player,
                        targetWorld,
                        spawnLocation,
                        player.getYaw(),
                        player.getPitch()
                );

                if (success && showMessage) {
                    player.sendMessage(
                            Text.literal("Teleported to world spawn"),
                            false
                    );
                }
            }
        } else {
            // NON-PLAYER ENTITY LOGIC
            // For non-players, we can either:
            // 1. Teleport to world spawn
            // 2. Teleport to entity's home/starting position
            // 3. Do nothing

            targetWorld = server.getOverworld();
            spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());

            // Simple teleport for non-player entities
            entity.teleport(
                    targetWorld,
                    spawnLocation.x,
                    spawnLocation.y,
                    spawnLocation.z,
                    java.util.Set.of(),
                    entity.getYaw(),
                    entity.getPitch()
            );

            // Can't send message to non-player entities
            if (showMessage) {
                FrostedLib.LOGGER.info("Teleported non-player entity to world spawn");
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "fake_respawn"),
                new SerializableData()
                        .add("prioritize_set_spawn", SerializableDataTypes.BOOLEAN, true)
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),
                FakeRespawnAction::action
        );
    }
}