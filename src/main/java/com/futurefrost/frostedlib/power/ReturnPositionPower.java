package com.futurefrost.frostedlib.power;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.util.ComponentAccessor;
import com.futurefrost.frostedlib.util.TeleportHelper;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class ReturnPositionPower extends BaseFrostedPower {
    private final String positionId;

    public ReturnPositionPower(PowerType<?> type, LivingEntity entity, String positionId) {
        super(type, entity);
        this.positionId = positionId;
    }

    @Override
    public void onUse() {
        if (this.entity instanceof ServerPlayerEntity serverPlayer) {
            PlayerDataComponent data = ComponentAccessor.getPlayerData((net.minecraft.entity.player.PlayerEntity) entity);
            if (data != null) {
                Optional<PositionData> optionalPos = data.getPosition(positionId);

                if (optionalPos.isPresent()) {
                    PositionData pos = optionalPos.get();
                    teleportToPosition(serverPlayer, pos);
                } else {
                    // Failsafe: teleport to world spawn
                    ServerWorld overworld = serverPlayer.getServer().getOverworld();
                    Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

                    TeleportHelper.teleportPlayer(
                            serverPlayer,
                            overworld,
                            spawnPos,
                            serverPlayer.getYaw(),
                            serverPlayer.getPitch()
                    );

                    serverPlayer.sendMessage(
                            net.minecraft.text.Text.literal("Position '" + positionId + "' not found. Teleported to spawn."),
                            false
                    );
                }
            }
        }
    }

    private void teleportToPosition(ServerPlayerEntity player, PositionData pos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerWorld targetWorld = server.getWorld(pos.dimension());
        if (targetWorld == null) {
            FrostedLib.LOGGER.error("Target world not found: " + pos.dimension());
            return;
        }

        boolean success = TeleportHelper.teleportPlayer(
                player,
                targetWorld,
                pos.toVec3d(),
                pos.yaw(),
                pos.pitch()
        );

        if (success) {
            player.sendMessage(
                    net.minecraft.text.Text.literal("Returned to position '" + positionId + "'"),
                    false
            );
        }
    }

    public static PowerFactory createFactory() {
        return new PowerFactory<>(
                Identifier.of("frostedlib", "return_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING),
                data -> (type, entity) -> new ReturnPositionPower(
                        type,
                        entity,
                        data.getString("position_id")
                )
        ).allowCondition();
    }
}