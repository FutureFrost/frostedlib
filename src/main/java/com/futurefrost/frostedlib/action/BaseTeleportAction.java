package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.util.*;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BlockState;
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
                .add("bring_mount", SerializableDataTypes.BOOLEAN, true)
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
                .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)
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
    protected final MountHandler mountHandler;

    public BaseTeleportAction() {
        this.errorHandler = new ErrorHandler();
        this.positionFinder = new PositionFinder();
        this.platformGenerator = new PlatformGenerator();
        this.mountHandler = new MountHandler();
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
                Vec3d safePosition = findSafePositionWithHeight(data, entity, targetWorld, randomizedPosition);

                if (safePosition == null) {
                    errorHandler.handleNoSafePosition(data, entity);
                    return;
                }

                // 5. Handle teleport with mount
                boolean success = mountHandler.teleportWithMount(entity, targetWorld, safePosition,
                        data.getBoolean("bring_mount"));

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

        // Get a reasonable Y level for searching
        int searchY = targetWorld.getSeaLevel();

        return new BlockPos((int) scaledX, searchY, (int) scaledZ);
    }

    // Find safe position with height consideration
    private Vec3d findSafePositionWithHeight(SerializableData.Instance data, Entity entity,
                                             ServerWorld targetWorld, Vec3d basePosition) {

        String className = this.getClass().getSimpleName();

        // For structure/biome teleports, we need to handle height differently
        // since these teleports provide specific positions that we want to respect
        if (className.contains("StructureTeleportAction") || className.contains("BiomeTeleportAction")) {
            // For structure/biome teleports, we need to check if the provided position is safe
            // If not, we should search around it but maintain the Y level if possible

            // First check if the exact position is safe
            if (isPositionSafeForEntity(targetWorld,
                    new BlockPos((int)basePosition.x, (int)basePosition.y, (int)basePosition.z))) {
                return basePosition;
            }

            // If not safe, search vertically around the given Y
            int centerX = (int) basePosition.x;
            int centerZ = (int) basePosition.z;
            int targetY = (int) basePosition.y;

            // Try to find a safe position at this X,Z with height adjustment
            return findHeightAdjustedPosition(data, targetWorld, centerX, centerZ, targetY);
        } else {
            // For relative/fixed teleports, use the full PositionFinder logic
            int centerX = (int) basePosition.x;
            int centerZ = (int) basePosition.z;

            return positionFinder.findSafePosition(data, entity, targetWorld, centerX, centerZ);
        }
    }

    // Find height-adjusted position (for structure/biome teleports)
    private Vec3d findHeightAdjustedPosition(SerializableData.Instance data, ServerWorld world,
                                             int centerX, int centerZ, int targetY) {

        // First try the exact Y
        if (isPositionSafeForEntity(world, new BlockPos(centerX, targetY, centerZ))) {
            return new Vec3d(centerX + 0.5, targetY, centerZ + 0.5);
        }

        // Search vertically around target Y
        for (int offset = 1; offset <= 32; offset++) {
            // Try above
            int yAbove = targetY + offset;
            if (yAbove < world.getTopY() &&
                    isPositionSafeForEntity(world, new BlockPos(centerX, yAbove, centerZ))) {
                return new Vec3d(centerX + 0.5, yAbove, centerZ + 0.5);
            }

            // Try below
            int yBelow = targetY - offset;
            if (yBelow >= world.getBottomY() &&
                    isPositionSafeForEntity(world, new BlockPos(centerX, yBelow, centerZ))) {
                return new Vec3d(centerX + 0.5, yBelow, centerZ + 0.5);
            }
        }

        // If vertical search fails, try expanding horizontally
        return searchHorizontallyForSafePosition(data, world, centerX, centerZ, targetY);
    }

    private Vec3d searchHorizontallyForSafePosition(SerializableData.Instance data, ServerWorld world,
                                                    int centerX, int centerZ, int targetY) {

        int maxSearchRadius = Math.min(data.getInt("search_radius"), 32);
        int maxSearchAttempts = Math.min(data.getInt("max_search_attempts"), 20);
        int totalAttempts = 0;

        for (int radius = 1; radius <= maxSearchRadius && totalAttempts < maxSearchAttempts; radius *= 2) {
            int points = Math.min(radius * 4, 16);

            for (int i = 0; i < points && totalAttempts < maxSearchAttempts; i++) {
                double angle = 2 * Math.PI * i / points;
                int x = centerX + (int) (radius * Math.cos(angle));
                int z = centerZ + (int) (radius * Math.sin(angle));

                // Try at target Y first
                if (isPositionSafeForEntity(world, new BlockPos(x, targetY, z))) {
                    return new Vec3d(x + 0.5, targetY, z + 0.5);
                }

                // Try vertically around target Y
                for (int offset = 1; offset <= 8; offset++) {
                    int yAbove = targetY + offset;
                    if (yAbove < world.getTopY() &&
                            isPositionSafeForEntity(world, new BlockPos(x, yAbove, z))) {
                        return new Vec3d(x + 0.5, yAbove, z + 0.5);
                    }

                    int yBelow = targetY - offset;
                    if (yBelow >= world.getBottomY() &&
                            isPositionSafeForEntity(world, new BlockPos(x, yBelow, z))) {
                        return new Vec3d(x + 0.5, yBelow, z + 0.5);
                    }
                }

                totalAttempts++;
            }
        }

        return null;
    }

    public boolean isPositionSafeForEntity(ServerWorld world, BlockPos pos) {
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) {
            return false;
        }

        BlockPos feetPos = pos;
        BlockPos headPos = pos.up();
        BlockPos groundPos = pos.down();

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        // Check for void
        boolean hasSolidGround = false;
        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            if (world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isSolidBlock(world,
                    new BlockPos(pos.getX(), y, pos.getZ()))) {
                hasSolidGround = true;
                break;
            }
        }
        if (!hasSolidGround) return false;

        // Feet and head must be passable
        boolean feetPassable = feetState.isAir() || feetState.isReplaceable() ||
                !feetState.isOpaqueFullCube(world, feetPos);
        boolean headPassable = headState.isAir() || headState.isReplaceable() ||
                !headState.isOpaqueFullCube(world, headPos);

        // Ground must be solid
        boolean groundSolid = groundState.isSolidBlock(world, groundPos);

        return feetPassable && headPassable && groundSolid;
    }
}