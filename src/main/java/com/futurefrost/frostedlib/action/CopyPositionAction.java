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

import java.util.Optional;

public class CopyPositionAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        String sourceId = data.getString("source_id");
        String targetId = data.getString("target_id");
        boolean overwrite = data.getBoolean("overwrite");
        boolean showMessage = data.getBoolean("show_message");

        Optional<PositionData> sourcePosition;

        // Get source position from appropriate component
        if (entity instanceof ServerPlayerEntity player) {
            PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);
            sourcePosition = playerData.getPosition(sourceId);
        } else {
            EntityDataComponent entityData = ModComponents.ENTITY_DATA.get(entity);
            sourcePosition = entityData.getPosition(sourceId);
        }

        if (sourcePosition.isEmpty()) {
            if (showMessage) {
                String errorMsg = "Cannot copy: No saved position found with ID '" + sourceId + "'";
                if (entity instanceof ServerPlayerEntity player) {
                    player.sendMessage(Text.literal(errorMsg), false);
                } else {
                    FrostedLib.LOGGER.warn("Failed to copy position for entity {}: {}",
                            entity.getName().getString(), errorMsg);
                }
            }
            return;
        }

        // Check if target exists and handle overwrite flag
        if (entity instanceof ServerPlayerEntity player) {
            PlayerDataComponent playerData = ModComponents.PLAYER_DATA.get(player);

            if (!overwrite && playerData.getPosition(targetId).isPresent()) {
                if (showMessage) {
                    player.sendMessage(
                            Text.literal("Cannot copy: Position '" + targetId + "' already exists. Use overwrite=true to replace."),
                            false
                    );
                }
                return;
            }

            playerData.savePosition(targetId, sourcePosition.get());

            if (showMessage) {
                player.sendMessage(
                        Text.literal("Copied position '" + sourceId + "' to '" + targetId + "'"),
                        false
                );
            }
        } else {
            EntityDataComponent entityData = ModComponents.ENTITY_DATA.get(entity);

            if (!overwrite && entityData.getPosition(targetId).isPresent()) {
                if (showMessage) {
                    FrostedLib.LOGGER.info("Cannot copy position '{}' to '{}' for entity {}: Target already exists",
                            sourceId, targetId, entity.getName().getString());
                }
                return;
            }

            entityData.savePosition(targetId, sourcePosition.get());

            if (showMessage) {
                FrostedLib.LOGGER.info("Copied position '{}' to '{}' for entity {}",
                        sourceId, targetId, entity.getName().getString());
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "copy_pos"),
                new SerializableData()
                        .add("source_id", SerializableDataTypes.STRING)
                        .add("target_id", SerializableDataTypes.STRING)
                        .add("overwrite", SerializableDataTypes.BOOLEAN, true)  // Default: overwrite existing
                        .add("show_message", SerializableDataTypes.BOOLEAN, false),
                CopyPositionAction::action
        );
    }
}