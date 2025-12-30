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

import java.util.Random;

public abstract class BaseTeleportAction {

    // Method to create common data for all teleport actions.
    protected static SerializableData createCommonDataWithoutHeightDefault() {
        return new SerializableData()
                .add("target_dimension", SerializableDataTypes.IDENTIFIER, null)
                .add("bring_mount", SerializableDataTypes.BOOLEAN, true)
                .add("generate_platform", SerializableDataTypes.BOOLEAN, false)
                .add("max_search_attempts", SerializableDataTypes.INT, 50)
                .add("on_error", ApoliDataTypes.ENTITY_ACTION, null)
                .add("platform_block", SerializableDataTypes.IDENTIFIER, Identifier.of("minecraft", "obsidian"))
                .add("platform_shape", SerializableDataTypes.STRING, "square")
                .add("platform_size", SerializableDataTypes.INT, 3)
                .add("random_offset", SerializableDataTypes.DOUBLE, 0.0)
                .add("search_radius", SerializableDataTypes.INT, 32)
                .add("show_message", SerializableDataTypes.BOOLEAN, false)
                .add("target_height", SerializableDataTypes.STRING, null)
                .add("strict_height", SerializableDataTypes.BOOLEAN, false)
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)
                .add("error_message", SerializableDataTypes.STRING, null);
    }

    // Method for actions that want "exposed" as default (relative teleport)
    protected static SerializableData createCommonDataWithExposedDefault() {
        return createCommonDataWithoutHeightDefault()
                .add("target_height", SerializableDataTypes.STRING, "exposed");  // Add default
    }

    // Method for fixed teleport with "fixed" as default
    protected static SerializableData createCommonDataWithFixedDefault() {
        return createCommonDataWithoutHeightDefault()
                .add("target_height", SerializableDataTypes.STRING, "fixed");  // Add default
    }

    protected final ErrorHandler errorHandler;
    protected final PositionFinder positionFinder;
    protected final PlatformGenerator platformGenerator;
    protected final MountHandler mountHandler;

    public BaseTeleportAction() {
        this.errorHandler = new ErrorHandler();
        this.positionFinder = new PositionFinder();
        this.platformGenerator = new PlatformGenerator();
        this.mountHandler = new MountHandler();
    }

    // Template method pattern - subclasses implement specific logic
    protected abstract Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld);

    protected abstract Vec3d calculateSearchStartPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld);

    protected abstract SerializableData getData();

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

                // 3. Check if we need height adjustment (only for relative/fixed teleports)
                String className = this.getClass().getSimpleName();
                Vec3d finalPosition;

                if (className.contains("StructureTeleportAction") || className.contains("BiomeTeleportAction")) {
                    // For structure/biome teleports, use position as-is (should already be safe)
                    finalPosition = basePosition;
                } else {
                    // For relative/fixed teleports, apply height adjustment if specified
                    finalPosition = applyTargetHeightAsSecondary(data, entity, targetWorld, basePosition);
                }

                // 4. Apply random offset (if any)
                Vec3d randomizedPosition = applyRandomOffset(data, finalPosition);

                // 5. Find safe position (with height already considered for relative/fixed)
                Vec3d safePosition;

                if (className.contains("StructureTeleportAction") || className.contains("BiomeTeleportAction")) {
                    // Structure/biome teleports should already have safe positions
                    safePosition = randomizedPosition;
                } else {
                    // Relative/fixed teleports need to find safe position
                    safePosition = positionFinder.findSafePosition(
                            data, entity, targetWorld,
                            (int) randomizedPosition.x, (int) randomizedPosition.z
                    );
                }

                if (safePosition == null) {
                    errorHandler.handleNoSafePosition(data, entity, basePosition, targetWorld.getRegistryKey());
                    return;
                }

                // 6. Handle teleport with mount
                boolean success = mountHandler.teleportWithMount(entity, targetWorld, safePosition,
                        data.getBoolean("bring_mount"));

                if (!success) {
                    errorHandler.handleTeleportFailed(data, entity, safePosition, targetWorld.getRegistryKey());
                    return;
                }

                // 7. Show success message
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
        return entity.getServer().getWorld(dimensionKey);
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

        // Get a reasonable Y level for searching
        int searchY = targetWorld.getSeaLevel();

        return new BlockPos((int) scaledX, searchY, (int) scaledZ);
    }

    // Default implementation for search start position (for relative teleport)
    protected Vec3d defaultSearchStartPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        double scale = data.getDouble("scale_factor");
        return new Vec3d(
                entity.getX() * scale,
                entity.getY(),
                entity.getZ() * scale
        );
    }

    // Apply target_height as secondary condition (only for relative/fixed teleports)
    protected Vec3d applyTargetHeightAsSecondary(SerializableData.Instance data, Entity entity,
                                                 ServerWorld targetWorld, Vec3d basePosition) {
        String heightMode = data.getString("target_height");
        if (heightMode == null || heightMode.isEmpty()) {
            return basePosition; // No height mode specified, keep original position
        }

        // Get the position finder to find a safe position with the specified height mode
        // but constrained to the XZ coordinates of the base position
        int centerX = (int) basePosition.x;
        int centerZ = (int) basePosition.z;

        // Calculate preferred Y based on mode
        double preferredY;
        if (heightMode.equals("fixed")) {
            preferredY = data.getDouble("target_y");
        } else if (heightMode.equals("relative")) {
            Double targetY = data.get("target_y");
            preferredY = targetY != null ? targetY : entity.getY();
        } else {
            preferredY = entity.getY();
        }

        boolean strictHeight = data.getBoolean("strict_height");

        // Search for a safe position with the desired height mode
        Vec3d heightAdjustedPos = positionFinder.findSafeHeightPosition(
                data, targetWorld, centerX, centerZ, heightMode, preferredY, strictHeight
        );

        // If we found a position with the desired height, use it
        if (heightAdjustedPos != null &&
                isPositionActuallySafe(data, targetWorld, heightAdjustedPos)) {
            return heightAdjustedPos;
        }

        // Otherwise try expanding search
        Vec3d expandedPos = findHeightAdjustedPositionWithSearch(
                data, entity, targetWorld, centerX, centerZ, heightMode, preferredY, strictHeight
        );

        return expandedPos != null ? expandedPos : basePosition;
    }

    private Vec3d findHeightAdjustedPositionWithSearch(SerializableData.Instance data, Entity entity,
                                                       ServerWorld world, int centerX, int centerZ,
                                                       String heightMode, double preferredY, boolean strictHeight) {
        int maxSearchRadius = Math.min(data.getInt("search_radius"), 32);
        int maxSearchAttempts = Math.min(data.getInt("max_search_attempts"), 20);

        int totalAttempts = 0;

        // Generate search pattern: start close and expand
        for (int radius = 1; radius <= maxSearchRadius && totalAttempts < maxSearchAttempts; radius *= 2) {
            int points = Math.min(radius * 4, 16);

            for (int i = 0; i < points && totalAttempts < maxSearchAttempts; i++) {
                double angle = 2 * Math.PI * i / points;
                int x = centerX + (int) (radius * Math.cos(angle));
                int z = centerZ + (int) (radius * Math.sin(angle));

                Vec3d testPos = positionFinder.findSafeHeightPosition(
                        data, world, x, z, heightMode, preferredY, strictHeight
                );

                if (testPos != null && isPositionActuallySafe(data, world, testPos)) {
                    return testPos;
                }

                totalAttempts++;
            }
        }

        return null;
    }

    private boolean isPositionActuallySafe(SerializableData.Instance data, ServerWorld world, Vec3d pos) {
        return positionFinder.isPositionActuallySafe(data, world, pos);
    }
}