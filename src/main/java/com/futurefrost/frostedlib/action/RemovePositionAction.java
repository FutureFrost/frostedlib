package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.registry.ModComponents;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RemovePositionAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        String positionId = data.getString("position_id");
        boolean showMessage = data.getBoolean("show_message");

        boolean removed;

        // Remove from appropriate component based on entity type
        if (entity instanceof ServerPlayerEntity player) {
            // Remove from player data
            PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);
            removed = playerData.removePosition(positionId);

            if (showMessage) {
                if (removed) {
                    player.sendMessage(
                            Text.literal("Removed saved position '" + positionId + "'"),
                            false
                    );
                } else {
                    player.sendMessage(
                            Text.literal("No saved position found with ID '" + positionId + "'"),
                            false
                    );
                }
            }
        } else {
            // Remove from entity data
            EntityDataComponent entityData = ModComponents.ENTITY_DATA.get(entity);
            removed = entityData.removePosition(positionId);

            if (showMessage) {
                if (removed) {
                    FrostedLib.LOGGER.info("Removed position '{}' for entity {}",
                            positionId, entity.getName().getString());
                } else {
                    FrostedLib.LOGGER.info("No saved position '{}' found for entity {}",
                            positionId, entity.getName().getString());
                }
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "remove_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING)
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),
                RemovePositionAction::action
        );
    }
}