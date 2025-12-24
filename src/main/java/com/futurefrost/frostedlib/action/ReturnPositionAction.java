package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import com.futurefrost.frostedlib.util.TeleportHelper;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

public class ReturnPositionAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            FrostedLib.LOGGER.warn("return_pos action can only be used on players");
            return;
        }

        String positionId = data.getString("position_id");
        PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);
        Optional<PositionData> optionalPos = playerData.getPosition(positionId);

        if (optionalPos.isPresent()) {
            // Teleport to saved position
            PositionData pos = optionalPos.get();
            MinecraftServer server = player.getServer();

            if (server == null) return;

            ServerWorld targetWorld = server.getWorld(pos.dimension());
            if (targetWorld == null) {
                FrostedLib.LOGGER.error("Target world not found: " + pos.dimension());
                executeFailsafe(data, player);
                return;
            }

            boolean success = TeleportHelper.teleportPlayer(
                    player,
                    targetWorld,
                    pos.toVec3d(),
                    pos.yaw(),
                    pos.pitch()
            );

            if (success && data.getBoolean("show_message")) {
                player.sendMessage(
                        Text.literal("Returned to position '" + positionId + "' at " +
                                String.format("%.1f, %.1f, %.1f", pos.x(), pos.y(), pos.z()) +
                                " in " + pos.dimension().getValue()),
                        false
                );
            }
        } else {
            // Execute failsafe
            executeFailsafe(data, player);
        }
    }

    private static void executeFailsafe(SerializableData.Instance data, ServerPlayerEntity player) {
        // Get the failsafe action
        io.github.apace100.apoli.power.factory.action.ActionFactory<Entity>.Instance failsafeAction =
                data.get("failsafe");

        if (failsafeAction != null) {
            // Execute the custom failsafe action
            failsafeAction.accept(player);
        } else {
            // Default failsafe: use the existing fake_respawn logic directly
            executeDefaultFakeRespawn(player, data.getBoolean("show_message"));
        }

        // Show position not found message if enabled
        if (data.getBoolean("show_message")) {
            player.sendMessage(
                    Text.literal("Position '" + data.getString("position_id") + "' not found. Executing failsafe."),
                    false
            );
        }
    }

    private static void executeDefaultFakeRespawn(ServerPlayerEntity player, boolean showMessage) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Simplified fake_respawn logic (always goes to world spawn for failsafe)
        ServerWorld overworld = server.getOverworld();
        Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

        boolean success = TeleportHelper.teleportPlayer(
                player,
                overworld,
                spawnPos,
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

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "return_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING)
                        .add("failsafe", ApoliDataTypes.ENTITY_ACTION, null)  // Customizable failsafe
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),
                ReturnPositionAction::action
        );
    }
}