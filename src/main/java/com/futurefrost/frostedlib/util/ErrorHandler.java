package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

public class ErrorHandler {

    public enum ErrorType {
        VALIDATION_ERROR,
        BIOME_NOT_FOUND,
        STRUCTURE_NOT_FOUND,
        NO_SAFE_POSITION,
        DIMENSION_NOT_FOUND,
        TELEPORT_FAILED,
        RUNTIME_EXCEPTION,
        STRICT_HEIGHT_VIOLATION
    }

    public void handleError(SerializableData.Instance data, Entity entity, ErrorType errorType,
                            String errorMessage) {
        // 1. Log the error
        FrostedLib.LOGGER.error("[Teleport] {}: {}", errorType, errorMessage);

        // 2. Send message to player if enabled
        if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
            String customMsg = data.getString("error_message");
            String messageToSend = getString(errorType, customMsg);
            player.sendMessage(Text.literal(messageToSend), false);
        }

        // 3. Execute the on_error action if configured
        if (data.isPresent("on_error")) {
            try {
                io.github.apace100.apoli.power.factory.action.ActionFactory<Entity>.Instance errorAction = data.get("on_error");
                errorAction.accept(entity);
            } catch (Exception e) {
                FrostedLib.LOGGER.error("Failed to execute 'on_error' action", e);
            }
        }
    }

    private static @NotNull String getString(ErrorType errorType, String customMsg) {
        String messageToSend;
        if (customMsg != null && !customMsg.isEmpty()) {
            messageToSend = customMsg;
        } else {
            // Default messages based on error type
            messageToSend = switch (errorType) {
                case VALIDATION_ERROR -> "Teleport configuration error.";
                case BIOME_NOT_FOUND -> "Could not find the target biome!";
                case STRUCTURE_NOT_FOUND -> "Could not find the target structure!";
                case NO_SAFE_POSITION -> "No safe location to teleport to!";
                case DIMENSION_NOT_FOUND -> "Target dimension is not accessible!";
                case TELEPORT_FAILED -> "The teleport failed!";
                case STRICT_HEIGHT_VIOLATION -> "Could not find position at required height!";
                default -> "An unexpected error occurred.";
            };
        }
        return messageToSend;
    }

    // Convenience methods
    public void handleDimensionNotFound(SerializableData.Instance data, Entity entity, RegistryKey<World> dimensionKey) {
        handleError(data, entity, ErrorType.DIMENSION_NOT_FOUND,
                "Target dimension not found or not loaded: " + dimensionKey.getValue()
        );
    }

    public void handleNoSafePosition(SerializableData.Instance data, Entity entity) {
        handleError(data, entity, ErrorType.NO_SAFE_POSITION,
                "No safe teleport position found after all fallbacks."
        );
    }

    public void handleTeleportFailed(SerializableData.Instance data, Entity entity) {
        handleError(data, entity, ErrorType.TELEPORT_FAILED,
                "Failed to teleport entity to final position."
        );
    }

    public void handleRuntimeError(SerializableData.Instance data, Entity entity, Exception e) {
        FrostedLib.LOGGER.error("Unexpected error in teleport action", e);
        handleError(data, entity, ErrorType.RUNTIME_EXCEPTION,
                "Unexpected error: " + e.getMessage());
    }

    public void handleValidationError(SerializableData.Instance data, Entity entity, String message) {
        handleError(data, entity, ErrorType.VALIDATION_ERROR, message);
    }

    public void handleBiomeNotFound(SerializableData.Instance data, Entity entity, String biomeId) {
        handleError(data, entity, ErrorType.BIOME_NOT_FOUND,
                "Could not find biome: " + biomeId);
    }

    public void handleStructureNotFound(SerializableData.Instance data, Entity entity, String structureId) {
        handleError(data, entity, ErrorType.STRUCTURE_NOT_FOUND,
                "Could not find structure: " + structureId);
    }
}