package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import com.futurefrost.frostedlib.util.TeleportHelper;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.Set;

public class ReturnPositionAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        String positionId = data.getString("position_id");
        boolean showMessage = data.getBoolean("show_message");

        // Get position data from appropriate component
        Optional<PositionData> optionalPos = Optional.empty();

        if (entity instanceof ServerPlayerEntity player) {
            PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);
            optionalPos = playerData.getPosition(positionId);
        } else {
            EntityDataComponent entityData = ModComponents.ENTITY_DATA.get(entity);
            optionalPos = entityData.getPosition(positionId);
        }

        if (optionalPos.isPresent()) {
            // Teleport to saved position
            PositionData pos = optionalPos.get();
            MinecraftServer server = entity.getServer();

            if (server == null) return;

            ServerWorld targetWorld = server.getWorld(pos.dimension());
            if (targetWorld == null) {
                FrostedLib.LOGGER.error("Target world not found: " + pos.dimension());
                executeFailsafe(data, entity);
                return;
            }

            boolean success;

            if (entity instanceof ServerPlayerEntity player) {
                success = TeleportHelper.teleportPlayer(
                        player,
                        targetWorld,
                        pos.toVec3d(),
                        pos.yaw(),
                        pos.pitch()
                );

                if (success && showMessage) {
                    player.sendMessage(
                            Text.literal("Returned to position '" + positionId + "' at " +
                                    String.format("%.1f, %.1f, %.1f", pos.x(), pos.y(), pos.z()) +
                                    " in " + pos.dimension().getValue()),
                            false
                    );
                }
            } else {
                // Teleport non-player entity
                entity.teleport(
                        targetWorld,
                        pos.x(),
                        pos.y(),
                        pos.z(),
                        Set.of(),
                        pos.yaw(),
                        pos.pitch()
                );
                success = true;

                if (showMessage) {
                    FrostedLib.LOGGER.info("Entity {} teleported to position '{}'",
                            entity.getName().getString(), positionId);
                }
            }
        } else {
            // Execute failsafe
            executeFailsafe(data, entity);
        }
    }

    private static void executeFailsafe(SerializableData.Instance data, Entity entity) {
        String positionId = data.getString("position_id");
        boolean showMessage = data.getBoolean("show_message");

        // Get the failsafe action
        ActionFactory<Entity>.Instance failsafeAction =
                data.get("failsafe");

        if (failsafeAction != null) {
            // Execute the custom failsafe action
            failsafeAction.accept(entity);
        } else {
            // Default failsafe based on entity type
            if (entity instanceof ServerPlayerEntity player) {
                executeDefaultPlayerFailsafe(player, showMessage);
            } else {
                executeDefaultEntityFailsafe(entity, showMessage);
            }
        }

        // Show position not found message if enabled
        if (showMessage && entity instanceof ServerPlayerEntity player) {
            player.sendMessage(
                    Text.literal("Position '" + positionId + "' not found. Executing failsafe."),
                    false
            );
        } else if (showMessage) {
            FrostedLib.LOGGER.info("Position '{}' not found for entity {}. Executing failsafe.",
                    positionId, entity.getName().getString());
        }
    }

    private static void executeDefaultPlayerFailsafe(ServerPlayerEntity player, boolean showMessage) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Player failsafe: check for bed/respawn anchor, then world spawn
        RegistryKey<World> spawnDimension = player.getSpawnPointDimension();
        BlockPos spawnBlockPos = player.getSpawnPointPosition();

        ServerWorld targetWorld = null;
        Vec3d spawnLocation;
        String spawnType = "world spawn";

        boolean hasValidSpawnPoint = false;

        if (spawnBlockPos != null && spawnDimension != null) {
            targetWorld = server.getWorld(spawnDimension);

            if (targetWorld != null) {
                if (spawnDimension == World.OVERWORLD) {
                    BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                    boolean isBed = blockState.getBlock() instanceof BedBlock;
                    boolean isWorldSpawn = spawnBlockPos.equals(targetWorld.getSpawnPos());
                    hasValidSpawnPoint = isBed && !isWorldSpawn;
                    spawnType = "bed spawn";
                } else if (spawnDimension == World.NETHER) {
                    BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                    boolean isRespawnAnchor = blockState.getBlock() instanceof RespawnAnchorBlock;
                    boolean isCharged = isRespawnAnchor && blockState.get(RespawnAnchorBlock.CHARGES) > 0;
                    hasValidSpawnPoint = isRespawnAnchor && isCharged;
                    spawnType = "respawn anchor";
                } else {
                    hasValidSpawnPoint = true;
                    spawnType = "dimension spawn point";
                }
            }
        }

        if (!hasValidSpawnPoint) {
            targetWorld = server.getOverworld();
            spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
            spawnType = "world spawn";
        } else {
            Optional<Vec3d> safeSpawn = PlayerEntity.findRespawnPosition(
                    targetWorld,
                    spawnBlockPos,
                    player.getSpawnAngle(),
                    false,
                    true
            );

            if (safeSpawn.isPresent()) {
                spawnLocation = safeSpawn.get();
            } else {
                targetWorld = server.getOverworld();
                spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
                spawnType = "world spawn (spawn obstructed)";
            }
        }

        boolean success = TeleportHelper.teleportPlayer(
                player,
                targetWorld,
                spawnLocation,
                player.getSpawnAngle(),
                0.0f
        );

        if (success && showMessage) {
            player.sendMessage(
                    Text.literal("Teleported to " + spawnType),
                    false
            );
        }
    }

    private static void executeDefaultEntityFailsafe(Entity entity, boolean showMessage) {
        MinecraftServer server = entity.getServer();
        if (server == null) return;

        // Entity failsafe: simple world spawn teleport
        ServerWorld overworld = server.getOverworld();
        Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

        entity.teleport(
                overworld,
                spawnPos.x,
                spawnPos.y,
                spawnPos.z,
                Set.of(),
                entity.getYaw(),
                entity.getPitch()
        );

        if (showMessage) {
            FrostedLib.LOGGER.info("Entity {} teleported to world spawn",
                    entity.getName().getString());
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "return_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING)
                        .add("failsafe", ApoliDataTypes.ENTITY_ACTION, null)  // Customizable failsafe
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),  // Default: false (silent)
                ReturnPositionAction::action
        );
    }
}