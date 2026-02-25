package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.Objects;

public class PositionFinder {
    private static final String HEIGHT_EXPOSED = "exposed";
    private static final String HEIGHT_UNEXPOSED = "unexposed";
    private static final String HEIGHT_RELATIVE = "relative";
    private static final String HEIGHT_FIXED = "fixed";

    private static final int MAX_SEARCH_RADIUS = 128;
    private static final int VERTICAL_SEARCH_RANGE = 64;
    private static final int PLATFORM_HEIGHT_ABOVE_VOID = 10;

    private final PlatformGenerator platformGenerator = new PlatformGenerator();

    /* ------------------------------------------------------------ */
    /*  Main Entry Point                                            */
    /* ------------------------------------------------------------ */

    public Vec3d findSafePosition(SerializableData.Instance data, Entity entity,
                                  ServerWorld world, int centerX, int centerZ) {

        // Get configuration with defaults
        String heightMode = data.getString("target_height");
        if (heightMode == null || heightMode.isEmpty()) {
            heightMode = HEIGHT_EXPOSED;
        }

        double preferredY = resolvePreferredY(data, entity, heightMode);
        boolean strictHeight = data.getBoolean("strict_height");
        boolean generatePlatform = data.getBoolean("generate_platform");
        boolean forcePlatform = data.getBoolean("force_platform");
        int searchRadius = Math.min(data.getInt("search_radius"), MAX_SEARCH_RADIUS);
        int maxAttempts = data.getInt("max_search_attempts");

        WorldBorder worldBorder = world.getWorldBorder();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // STAGE 1: Exact position
        mutablePos.set(centerX, (int) preferredY, centerZ);
        if (worldBorder.contains(mutablePos)) {
            Vec3d exactPos = findPositionAtColumn(data, world, centerX, centerZ,
                    heightMode, preferredY, strictHeight, entity);
            if (exactPos != null) {
                if (generatePlatform && forcePlatform) {
                    return platformGenerator.generatePlatformAtPosition(data, world, (int) exactPos.x, (int) exactPos.z, (int) exactPos.y);
                }
                else {
                    return exactPos;
                }
            }
        }

        // STAGE 2: Expanding search
        Vec3d foundPos = searchExpandingSquare(data, world, centerX, centerZ,
                heightMode, preferredY, strictHeight, searchRadius, maxAttempts, entity);
        if (foundPos != null) {
            if (generatePlatform && forcePlatform) {
                return platformGenerator.generatePlatformAtPosition(data, world, (int) foundPos.x, (int) foundPos.z, (int) foundPos.y);
            }
            else {
                return foundPos;
            }
        }

        // STAGE 3: Platform generation
        if (generatePlatform) {
            boolean overVoid = isColumnEmpty(world, centerX, centerZ);
            boolean overLiquid = isOverLiquidColumn(data, world, centerX, centerZ);

            if (overVoid || overLiquid) {
                return generateEmergencyPlatform(data, world, centerX, centerZ, overVoid, entity);
            }
        }

        // STAGE 4: Fallback
        if (!strictHeight) {
            Vec3d fallbackPosition = getFallbackPosition(data, world, centerX, centerZ, heightMode, preferredY, entity);
            if (fallbackPosition != null) {
                if (generatePlatform && forcePlatform) {
                    return platformGenerator.generatePlatformAtPosition(data, world, (int) fallbackPosition.x, (int) fallbackPosition.z, (int) fallbackPosition.y);
                }
                else {
                    return fallbackPosition;
                }
            }
        }

        return null;
    }

    /* ------------------------------------------------------------ */
    /*  Core Search Algorithms                                      */
    /* ------------------------------------------------------------ */

    private Vec3d searchExpandingSquare(SerializableData.Instance data, ServerWorld world,
                                        int centerX, int centerZ, String heightMode,
                                        double preferredY, boolean strictHeight,
                                        int maxRadius, int maxAttempts, Entity entity) {

        WorldBorder worldBorder = world.getWorldBorder();
        int attempts = 0;

        for (int radius = 1; radius <= maxRadius && attempts < maxAttempts; radius *= 2) {
            for (BlockPos pos : BlockPos.iterateInSquare(new BlockPos(centerX, 0, centerZ),
                    radius, Direction.EAST, Direction.SOUTH)) {

                if (attempts >= maxAttempts) break;

                int x = pos.getX();
                int z = pos.getZ();

                // World border check
                if (!worldBorder.contains(x, (int) preferredY, z)) {
                    continue;
                }

                Vec3d candidate = findPositionAtColumn(data, world, x, z,
                        heightMode, preferredY, strictHeight, entity);
                if (candidate != null) {
                    return candidate;
                }

                attempts++;
            }
        }

        return null;
    }

    private Vec3d findPositionAtColumn(SerializableData.Instance data, ServerWorld world,
                                       int x, int z, String heightMode,
                                       double preferredY, boolean strictHeight, Entity entity) {

        return switch (heightMode) {
            case HEIGHT_FIXED -> findPositionAtFixedY(data, world, x, z, (int) preferredY, strictHeight, entity);
            case HEIGHT_RELATIVE -> findPositionAroundY(data, world, x, z, (int) preferredY, strictHeight, entity);
            case HEIGHT_UNEXPOSED -> findUnexposedPosition(data, world, x, z, (int) preferredY, strictHeight, entity);
            default -> findExposedPosition(data, world, x, z, strictHeight, entity);
        };
    }

    /* ------------------------------------------------------------ */
    /*  Height mode implementations                                 */
    /* ------------------------------------------------------------ */

    private Vec3d findPositionAtFixedY(SerializableData.Instance data, ServerWorld world,
                                       int x, int z, int targetY, boolean strictHeight, Entity entity) {

        // Check if position is valid at exactly targetY
        if (isPositionValid(data, world, x, targetY, z, entity)) {
            return toCenterVec3d(x, targetY, z);
        }

        if (strictHeight) {
            return null;
        }

        // Search vertically around targetY
        return searchVertically(data, world, x, z, targetY, entity);
    }

    private Vec3d findPositionAroundY(SerializableData.Instance data, ServerWorld world,
                                      int x, int z, int referenceY, boolean strictHeight, Entity entity) {

        // Start from referenceY and search both directions
        Vec3d found = searchVertically(data, world, x, z, referenceY, entity);
        if (found != null) {
            return found;
        }

        if (strictHeight) {
            return null;
        }

        // Fallback to surface position
        return findExposedPosition(data, world, x, z, false, entity);
    }

    private Vec3d findUnexposedPosition(SerializableData.Instance data, ServerWorld world,
                                        int x, int z, int startY, boolean strictHeight, Entity entity) {

        int bottomY = world.getBottomY();
        int searchY = Math.min(startY, world.getTopY() - 1);

        // Search downward for first unexposed valid position
        for (int y = searchY; y >= bottomY; y--) {
            if (!isSkyAbove(world, new BlockPos(x, y, z))) {
                if (isPositionValid(data, world, x, y, z, entity)) {
                    return toCenterVec3d(x, y, z);
                }
            }
        }

        if (strictHeight) {
            return null;
        }

        return searchVertically(data, world, x, z, startY, entity);
    }

    private Vec3d findExposedPosition(SerializableData.Instance data, ServerWorld world,
                                      int x, int z, boolean strictHeight, Entity entity) {

        // Get surface position
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);

        // Check surface position
        if (isSkyAbove(world, surfacePos) && isPositionValid(data, world, x, surfaceY, z, entity)) {
            return toCenterVec3d(x, surfaceY, z);
        }

        if (strictHeight) {
            // Search upward for exposed position
            for (int y = surfaceY; y <= world.getTopY(); y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isSkyAbove(world, pos) && isPositionValid(data, world, x, y, z, entity)) {
                    return toCenterVec3d(x, y, z);
                }
            }
        }

        return null;
    }

    private Vec3d searchVertically(SerializableData.Instance data, ServerWorld world,
                                   int x, int z, int centerY, Entity entity) {

        int topY = world.getTopY();
        int bottomY = world.getBottomY();

        // Search outward from center
        for (int offset = 0; offset <= VERTICAL_SEARCH_RANGE; offset++) {
            // Try above
            int yAbove = centerY + offset;
            if (yAbove <= topY && isPositionValid(data, world, x, yAbove, z, entity)) {
                return toCenterVec3d(x, yAbove, z);
            }

            // Try below (skip offset 0)
            if (offset > 0) {
                int yBelow = centerY - offset;
                if (yBelow >= bottomY && isPositionValid(data, world, x, yBelow, z, entity)) {
                    return toCenterVec3d(x, yBelow, z);
                }
            }
        }

        return null;
    }

    /* ------------------------------------------------------------ */
    /*  Position Validation                                         */
    /* ------------------------------------------------------------ */

    private boolean isPositionValid(SerializableData.Instance data, ServerWorld world,
                                    int x, int y, int z, Entity entity) {

        // Basic bounds check
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }

        // Create entity hitbox at this position
        Box entityHitbox = createEntityHitbox(x + 0.5, y, z + 0.5, entity);

        // Check all requirements in optimal order
        return world.getWorldBorder().contains(entityHitbox)
                && !isColumnEmpty(world, x, z)
                && !containsUnsafeLiquids(data, world, entityHitbox)
                && isHitboxVolumeEmpty(world, entityHitbox, entity)
                && hasSolidGround(world, entityHitbox)
                && isWithinStructure(data, x, y, z, entity);
    }

    private boolean isWithinStructure(SerializableData.Instance data,
                                      int x, int y, int z, Entity entity) {
        // Check if structure validation is required
        if (!data.isPresent("structure_id") && !data.isPresent("strict_structure")) {
            return true; // No structure requirement
        }

        boolean strictStructure = data.getBoolean("strict_structure");
        if (!strictStructure) {
            return true; // Not strict, so any position is acceptable
        }

        StructureStart cachedStart = data.get("cached_structure_start");
        if (cachedStart == null) {
            return true; // No cached structure start
        }

        // Create the entity's full hitbox at this position
        Box entityHitbox = createEntityHitbox(x + 0.5, y, z + 0.5, entity);

        // Check if the entity's hitbox intersects with any piece of the structure
        for (StructurePiece piece : cachedStart.getChildren()) {
            Box pieceBox = new Box(
                    piece.getBoundingBox().getMinX(),
                    piece.getBoundingBox().getMinY(),
                    piece.getBoundingBox().getMinZ(),
                    piece.getBoundingBox().getMaxX(),
                    piece.getBoundingBox().getMaxY(),
                    piece.getBoundingBox().getMaxZ()
            );

            // Check if the entity's full hitbox intersects with this structure piece
            if (entityHitbox.intersects(pieceBox)) {
                return true; // Position is within structure
            }
        }

        return false; // Position is not within any structure piece
    }

    private boolean isHitboxVolumeEmpty(ServerWorld world, Box hitbox, Entity entity) {
        // Calculate the integer bounds we need to check
        int minX = MathHelper.floor(hitbox.minX);
        int maxX = MathHelper.floor(hitbox.maxX);
        int minY = MathHelper.floor(hitbox.minY);
        int maxY = MathHelper.floor(hitbox.maxY);
        int minZ = MathHelper.floor(hitbox.minZ);
        int maxZ = MathHelper.floor(hitbox.maxZ);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        ShapeContext shapeContext = ShapeContext.of(entity);

        // Only check blocks that intersect the hitbox
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = world.getBlockState(mutablePos);

                    // Skip air and replaceable blocks immediately
                    if (state.isAir() || state.isReplaceable()) {
                        continue;
                    }

                    // Get collision shape and check intersection
                    VoxelShape collisionShape = state.getCollisionShape(world, mutablePos, shapeContext);
                    if (!collisionShape.isEmpty()) {
                        // Only check intersection if there's actual collision
                        Box blockBox = collisionShape.getBoundingBox().offset(mutablePos);
                        if (hitbox.intersects(blockBox)) {
                            return false; // Block would collide
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean hasSolidGround(ServerWorld world, Box hitbox) {
        int minX = MathHelper.floor(hitbox.minX);
        int maxX = MathHelper.floor(hitbox.maxX);
        int minZ = MathHelper.floor(hitbox.minZ);
        int maxZ = MathHelper.floor(hitbox.maxZ);
        int groundY = MathHelper.floor(hitbox.minY) - 1;

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // Check if any block under the footprint provides support
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                mutablePos.set(x, groundY, z);
                if (world.getBlockState(mutablePos).isSolidBlock(world, mutablePos)) {
                    return true; // Found solid ground
                }
            }
        }

        return false;
    }

    private boolean containsUnsafeLiquids(SerializableData.Instance data, ServerWorld world, Box hitbox) {
        // Quick early exit if no liquid conditions are configured
        boolean liquidsSafe = data.getBoolean("liquids_safe");
        ConditionFactory<FluidState>.Instance liquidCondition = data.get("liquid_condition");

        if (liquidCondition == null && liquidsSafe) {
            return false; // All liquids are safe, no need to check
        }

        // Only check if we need to validate liquids
        int minX = MathHelper.floor(hitbox.minX);
        int maxX = MathHelper.floor(hitbox.maxX);
        int minY = MathHelper.floor(hitbox.minY);
        int maxY = MathHelper.floor(hitbox.maxY);
        int minZ = MathHelper.floor(hitbox.minZ);
        int maxZ = MathHelper.floor(hitbox.maxZ);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (isLiquidUnsafe(data, world, mutablePos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isLiquidUnsafe(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        boolean hasFluid = !fluidState.isEmpty();

        if (!hasFluid) {
            return false;
        }

        ConditionFactory<FluidState>.Instance liquidCondition = data.get("liquid_condition");
        boolean liquidsSafe = data.getBoolean("liquids_safe");

        // CASE 1: No liquid condition configured
        // if liquids_safe is false, any liquid is unsafe
        // If liquids_safe is true, all liquids are safe
        if (liquidCondition == null) {
            return !liquidsSafe;
        }

        // CASE 2: Liquid condition IS configured
        // if liquids_safe is false, only condition-specified liquids are unsafe
        // If liquids_safe is true, only condition-specified liquids are safe
        try {
            boolean conditionMatches = liquidCondition.test(fluidState);
            if (liquidsSafe) {
                return !conditionMatches;
            } else {
                return conditionMatches;
            }
        } catch (Exception e) {
            FrostedLib.LOGGER.warn("Failed to evaluate liquid condition at {}: {}", pos, e.getMessage());
            return !liquidsSafe; // Fallback to CASE 1 behavior
        }
    }

    private boolean isColumnEmpty(ServerWorld world, int x, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, world.getBottomY(), z);

        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            mutable.setY(y);
            if (world.getBlockState(mutable).isSolidBlock(world, mutable)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOverLiquidColumn(SerializableData.Instance data, ServerWorld world, int x, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, world.getTopY(), z);

        for (int y = world.getTopY(); y >= world.getBottomY(); y--) {
            mutable.setY(y);
            if (!world.getFluidState(mutable).isEmpty() &&
                    isLiquidUnsafe(data, world, mutable)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSkyAbove(ServerWorld world, BlockPos pos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());

        for (int y = pos.getY() + 1; y <= world.getTopY(); y++) {
            mutable.setY(y);
            BlockState state = world.getBlockState(mutable);

            // Skip transparent/non-blocking blocks
            if (state.isAir() || state.isReplaceable() || !state.isOpaque()) {
                continue;
            }

            // Solid/opaque blocks block the sky
            if (state.isSolidBlock(world, mutable) || state.isOpaqueFullCube(world, mutable)) {
                return false;
            }
        }

        return true;
    }

    /* ------------------------------------------------------------ */
    /*  Platform Generation                                         */
    /* ------------------------------------------------------------ */

    private Vec3d generateEmergencyPlatform(SerializableData.Instance data,
                                            ServerWorld world, int x, int z,
                                            boolean overVoid, Entity entity) {

        int platformY = overVoid ?
                Math.max(world.getBottomY() + PLATFORM_HEIGHT_ABOVE_VOID, world.getSeaLevel()) :
                findLiquidSurface(data, world, x, z);

        // Ensure platform is within world bounds
        platformY = MathHelper.clamp(platformY,
                world.getBottomY() + 1,
                world.getTopY() - 1);

        if (!isPlatformPositionValid(world, x, platformY, z, entity)) {
            return null; // Position not suitable for platform generation
        }

        FrostedLib.LOGGER.info("Generated platform at [{}, {}, {}] in dimension {}",
                x, platformY, z, world.getRegistryKey().getValue());

        // Use the platformGenerator to create the platform
        return platformGenerator.generatePlatformAtPosition(data, world, x, z, platformY);
    }

    private boolean isPlatformPositionValid(ServerWorld world, int x, int y, int z, Entity entity) {
        // Basic bounds check
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }

        // Create entity hitbox at this position
        Box entityHitbox = createEntityHitbox(x + 0.5, y, z + 0.5, entity);

        // Check world border
        if (!world.getWorldBorder().contains(entityHitbox)) {
            return false;
        }

        // Check that hitbox volume is clear
        return isHitboxVolumeEmpty(world, entityHitbox, entity);
    }

    private int findLiquidSurface(SerializableData.Instance data, ServerWorld world, int x, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, world.getTopY(), z);
        int highestUnsafeY = Integer.MIN_VALUE;

        for (int y = world.getTopY(); y >= world.getBottomY(); y--) {
            mutable.setY(y);
            FluidState fluidState = world.getFluidState(mutable);

            if (!fluidState.isEmpty() && isLiquidUnsafe(data, world, mutable)) {
                // Check if block above is air (liquid surface)
                if (world.getBlockState(mutable.up()).isAir()) {
                    highestUnsafeY = Math.max(highestUnsafeY, y);
                }
            }
        }

        return highestUnsafeY;
    }

    /* ------------------------------------------------------------ */
    /*  Helper methods                                              */
    /* ------------------------------------------------------------ */

    private Box createEntityHitbox(double centerX, double feetY, double centerZ, Entity entity) {
        var dimensions = entity.getDimensions(entity.getPose());
        float radius = dimensions.width / 2.0f;
        float height = dimensions.height;

        return new Box(
                centerX - radius, feetY,
                centerZ - radius,
                centerX + radius, feetY + height,
                centerZ + radius
        );
    }

    private double resolvePreferredY(SerializableData.Instance data, Entity entity, String mode) {
        return switch (mode) {
            case HEIGHT_FIXED -> data.getDouble("target_y");
            case HEIGHT_RELATIVE -> {
                Double y = data.get("target_y");
                yield Objects.requireNonNullElseGet(y, entity::getY);
            }
            default -> entity.getY();
        };
    }

    private Vec3d getFallbackPosition(SerializableData.Instance data, ServerWorld world,
                                      int x, int z, String originalMode,
                                      double preferredY, Entity entity) {

        if (originalMode.equals(HEIGHT_EXPOSED) || originalMode.equals(HEIGHT_FIXED)) {
            // Try unexposed as fallback
            return findUnexposedPosition(data, world, x, z, (int) preferredY, false, entity);
        } else {
            // Try exposed as fallback
            return findExposedPosition(data, world, x, z, false, entity);
        }
    }

    private Vec3d toCenterVec3d(int x, int y, int z) {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }
}