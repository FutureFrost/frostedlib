package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

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
                            String errorMessage, Vec3d attemptedPos, RegistryKey<World> targetDimension) {
        // 1. Log the error
        FrostedLib.LOGGER.error("[Teleport] {}: {}", errorType, errorMessage);

        // 2. Send message to player if enabled
        if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
            String customMsg = data.getString("error_message");
            String messageToSend;
            if (customMsg != null && !customMsg.isEmpty()) {
                messageToSend = customMsg;
            } else {
                // Default messages based on error type
                switch (errorType) {
                    case VALIDATION_ERROR:
                        messageToSend = "Teleport configuration error.";
                        break;
                    case BIOME_NOT_FOUND:
                        messageToSend = "Could not find the target biome!";
                        break;
                    case STRUCTURE_NOT_FOUND:
                        messageToSend = "Could not find the target structure!";
                        break;
                    case NO_SAFE_POSITION:
                        messageToSend = "No safe location to teleport to!";
                        break;
                    case DIMENSION_NOT_FOUND:
                        messageToSend = "Target dimension is not accessible!";
                        break;
                    case TELEPORT_FAILED:
                        messageToSend = "The teleport failed!";
                        break;
                    case STRICT_HEIGHT_VIOLATION:
                        messageToSend = "Could not find position at required height!";
                        break;
                    case RUNTIME_EXCEPTION:
                    default:
                        messageToSend = "An unexpected error occurred.";
                        break;
                }
            }
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

    // Convenience methods
    public void handleDimensionNotFound(SerializableData.Instance data, Entity entity, RegistryKey<World> dimensionKey) {
        handleError(data, entity, ErrorType.DIMENSION_NOT_FOUND,
                "Target dimension not found or not loaded: " + dimensionKey.getValue(),
                null, dimensionKey);
    }

    public void handleNoSafePosition(SerializableData.Instance data, Entity entity,
                                     Vec3d attemptedPos, RegistryKey<World> targetDimension) {
        handleError(data, entity, ErrorType.NO_SAFE_POSITION,
                "No safe teleport position found after all fallbacks.",
                attemptedPos, targetDimension);
    }

    public void handleTeleportFailed(SerializableData.Instance data, Entity entity,
                                     Vec3d position, RegistryKey<World> targetDimension) {
        handleError(data, entity, ErrorType.TELEPORT_FAILED,
                "Failed to teleport entity to final position.",
                position, targetDimension);
    }

    public void handleRuntimeError(SerializableData.Instance data, Entity entity, Exception e) {
        FrostedLib.LOGGER.error("Unexpected error in teleport action", e);
        handleError(data, entity, ErrorType.RUNTIME_EXCEPTION,
                "Unexpected error: " + e.getMessage(), null, null);
    }

    public void handleValidationError(SerializableData.Instance data, Entity entity, String message) {
        handleError(data, entity, ErrorType.VALIDATION_ERROR, message, null, null);
    }

    public void handleBiomeNotFound(SerializableData.Instance data, Entity entity, String biomeId) {
        handleError(data, entity, ErrorType.BIOME_NOT_FOUND,
                "Could not find biome: " + biomeId, null, null);
    }

    public void handleStructureNotFound(SerializableData.Instance data, Entity entity, String structureId) {
        handleError(data, entity, ErrorType.STRUCTURE_NOT_FOUND,
                "Could not find structure: " + structureId, null, null);
    }
}