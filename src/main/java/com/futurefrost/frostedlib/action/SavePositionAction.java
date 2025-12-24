package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SavePositionAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        String positionId = data.getString("position_id");
        boolean showMessage = data.getBoolean("show_message");

        // Create position data from current entity location
        PositionData pos = PositionData.fromEntity(entity);

        // Save to appropriate component based on entity type
        if (entity instanceof ServerPlayerEntity player) {
            // Save to player data
            PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);
            playerData.savePosition(positionId, pos);

            if (showMessage) {
                player.sendMessage(
                        Text.literal("Saved position '" + positionId + "' at " +
                                String.format("%.1f, %.1f, %.1f", entity.getX(), entity.getY(), entity.getZ()) +
                                " in " + entity.getWorld().getRegistryKey().getValue()),
                        false
                );
            }
        } else {
            // Save to entity data
            EntityDataComponent entityData = ModComponents.ENTITY_DATA.get(entity);
            entityData.savePosition(positionId, pos);

            if (showMessage) {
                // For non-player entities, log to console
                FrostedLib.LOGGER.info("Saved position '{}' for entity {} at {} in {}",
                        positionId,
                        entity.getName().getString(),
                        String.format("%.1f, %.1f, %.1f", entity.getX(), entity.getY(), entity.getZ()),
                        entity.getWorld().getRegistryKey().getValue()
                );
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "save_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING)
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),  // Default: false (silent)
                SavePositionAction::action
        );
    }
}