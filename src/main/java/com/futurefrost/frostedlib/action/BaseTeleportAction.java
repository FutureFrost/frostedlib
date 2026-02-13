package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.util.*;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.Random;

public abstract class BaseTeleportAction {

    // Method to create common data for all teleport actions.
    protected static SerializableData createCommonDataWithoutHeightDefault() {
        return new SerializableData()
                .add("target_dimension", SerializableDataTypes.IDENTIFIER, null)
                .add("generate_platform", SerializableDataTypes.BOOLEAN, false)
                .add("max_search_attempts", SerializableDataTypes.INT, 50)
                .add("on_error", ApoliDataTypes.ENTITY_ACTION, null)
                .add("platform_block", SerializableDataTypes.IDENTIFIER, Identifier.of("minecraft", "obsidian"))
                .add("platform_shape", SerializableDataTypes.STRING, "square")
                .add("platform_size", SerializableDataTypes.INT, 2)
                .add("random_offset", SerializableDataTypes.DOUBLE, 0.0)
                .add("search_radius", SerializableDataTypes.INT, 32)
                .add("show_message", SerializableDataTypes.BOOLEAN, false)
                .add("target_height", SerializableDataTypes.STRING, null)
                .add("strict_height", SerializableDataTypes.BOOLEAN, false)
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                .add("liquid_condition", ApoliDataTypes.FLUID_CONDITION, null)
                .add("error_message", SerializableDataTypes.STRING, null);
    }

    // Method for actions that want "exposed" as default (relative teleport)
    protected static SerializableData createCommonDataWithExposedDefault() {
        return createCommonDataWithoutHeightDefault()
                .add("target_height", SerializableDataTypes.STRING, "exposed");  // Add default
    }

    protected final ErrorHandler errorHandler;
    protected final PositionFinder positionFinder;
    protected final PlatformGenerator platformGenerator;

    public BaseTeleportAction() {
        this.errorHandler = new ErrorHandler();
        this.positionFinder = new PositionFinder();
        this.platformGenerator = new PlatformGenerator();
    }

    // Template method pattern - subclasses implement specific logic
    protected abstract Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld);

    public void execute(SerializableData.Instance data, Entity entity) {
        // Return early if on client side
        if (entity.getWorld().isClient) {
            return;
        }

        // Now we know we're on server side, so getServer() should not be null
        if (entity.getServer() == null) {
            // This is unexpected on server side, log warning
            FrostedLib.LOGGER.warn("Entity server is null on server side for teleport action");
            return;
        }

        try {
            if (entity.getServer() == null) {
                errorHandler.handleRuntimeError(data, entity, new RuntimeException("Entity server is null"));
                return;
            }

            try {
                // 1. Get target dimension
                ServerWorld targetWorld = getTargetWorld(data, entity);
                if (targetWorld == null) {
                    RegistryKey<World> dimensionKey = getTargetDimensionKey(data, entity);
                    errorHandler.handleDimensionNotFound(data, entity, dimensionKey);
                    return;
                }

                // 2. Calculate base position (subclass-specific)
                Vec3d basePosition;
                try {
                    basePosition = calculateTargetPosition(data, entity, targetWorld);
                } catch (IllegalArgumentException e) {
                    errorHandler.handleValidationError(data, entity, e.getMessage());
                    return;
                } catch (RuntimeException e) {
                    String errorMsg = e.getMessage();
                    if (errorMsg.contains("biome")) {
                        errorHandler.handleBiomeNotFound(data, entity, errorMsg);
                    } else if (errorMsg.contains("structure")) {
                        errorHandler.handleStructureNotFound(data, entity, errorMsg);
                    } else {
                        errorHandler.handleRuntimeError(data, entity, e);
                    }
                    return;
                }

                // 3. Apply random offset (if any) FIRST, before height adjustment
                Vec3d randomizedPosition = applyRandomOffset(data, basePosition);

                // 4. Get the final safe position using PositionFinder
                Vec3d safePosition = positionFinder.findSafePosition(
                        data,
                        entity,
                        targetWorld,
                        (int) randomizedPosition.x,
                        (int) randomizedPosition.z
                );

                if (safePosition == null) {
                    errorHandler.handleNoSafePosition(data, entity);
                    return;
                }

                // 5. Handle teleport
                boolean success;
                if (entity instanceof ServerPlayerEntity player) {
                    TeleportHelper.teleportPlayer(player, targetWorld, safePosition, player.getYaw(), player.getPitch());
                    success = true;
                } else {
                    try {
                        entity.teleport(
                                targetWorld,
                                safePosition.x,
                                safePosition.y,
                                safePosition.z,
                                java.util.Set.of(),
                                entity.getYaw(),
                                entity.getPitch()
                        );
                        success = true;
                    } catch (Exception e) {
                        FrostedLib.LOGGER.error("Teleportation failed", e);
                        success = false;
                    }
                }

                if (!success) {
                    errorHandler.handleTeleportFailed(data, entity);
                    return;
                }

                // 6. Show success message
                showSuccessMessage(data, entity, targetWorld);

            } catch (Exception e) {
                errorHandler.handleRuntimeError(data, entity, e);
            }
        } catch (Exception e) {
            errorHandler.handleRuntimeError(data, entity, e);
        }
    }

    // Common helper methods
    protected ServerWorld getTargetWorld(SerializableData.Instance data, Entity entity) {
        RegistryKey<World> dimensionKey = getTargetDimensionKey(data, entity);
        return Objects.requireNonNull(entity.getServer()).getWorld(dimensionKey);
    }

    protected RegistryKey<World> getTargetDimensionKey(SerializableData.Instance data, Entity entity) {
        if (data.isPresent("target_dimension")) {
            Identifier dimId = data.getId("target_dimension");
            return RegistryKey.of(RegistryKeys.WORLD, dimId);
        }
        return entity.getWorld().getRegistryKey();
    }

    protected Vec3d applyRandomOffset(SerializableData.Instance data, Vec3d position) {
        double randomLimit = data.getDouble("random_offset");
        if (randomLimit <= 0) return position;

        Random random = new Random();
        double offsetX = (random.nextDouble() * 2 - 1) * randomLimit;
        double offsetZ = (random.nextDouble() * 2 - 1) * randomLimit;
        return new Vec3d(position.x + offsetX, position.y, position.z + offsetZ);
    }

    protected void showSuccessMessage(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        if (data.getBoolean("show_message") && entity instanceof ServerPlayerEntity player) {
            boolean isChangingDimension = !entity.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey());
            String message = isChangingDimension ?
                    "Dimension shifted to " + targetWorld.getRegistryKey().getValue() :
                    "Teleported within " + targetWorld.getRegistryKey().getValue();
            player.sendMessage(Text.literal(message), false);
        }
    }

    // Helper method to calculate scaled position for dimension search
    protected BlockPos calculateScaledSearchPosition(Entity entity, ServerWorld targetWorld, double scaleFactor) {
        // Get entity's current position
        Vec3d currentPos = entity.getPos();

        // Apply scale factor to get position in target dimension
        double scaledX = currentPos.x * scaleFactor;
        double scaledZ = currentPos.z * scaleFactor;

        return new BlockPos((int) scaledX, 64, (int) scaledZ); // Y=64 as reference point
    }
}