package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.*;

public class PositionFinder {
    private static final String HEIGHT_EXPOSED = "exposed";
    private static final String HEIGHT_UNEXPOSED = "unexposed";
    private static final String HEIGHT_RELATIVE = "relative";
    private static final String HEIGHT_FIXED = "fixed";

    private static final int MAX_SEARCH_RADIUS = 128;
    private static final int VERTICAL_SEARCH_RANGE = 64;
    private static final int PLATFORM_HEIGHT_ABOVE_VOID = 10;

    // Platform generator instance
    private final PlatformGenerator platformGenerator = new PlatformGenerator();

    /* ------------------------------------------------------------ */
    /*  Main entry point                                            */
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
        int searchRadius = Math.min(data.getInt("search_radius"), MAX_SEARCH_RADIUS);
        int maxAttempts = data.getInt("max_search_attempts");
        boolean liquidsSafe = data.getBoolean("liquids_safe");

        WorldBorder worldBorder = world.getWorldBorder();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // STAGE 1: Try exact position first
        mutablePos.set(centerX, (int)preferredY, centerZ);
        if (worldBorder.contains(mutablePos)) {
            Vec3d exactPos = findPositionAtColumn(data, world, centerX, centerZ,
                    heightMode, preferredY, strictHeight);
            if (exactPos != null) {
                return exactPos;
            }
        }

        // STAGE 2: Expanding square search
        Vec3d foundPos = searchExpandingSquare(data, world, centerX, centerZ,
                heightMode, preferredY, strictHeight,
                searchRadius, maxAttempts);
        if (foundPos != null) {
            return foundPos;
        }

        // STAGE 3: Platform generation if configured
        if (generatePlatform) {
            boolean overVoid = isColumnEmpty(world, centerX, centerZ);
            boolean overLiquid = isOverLiquidColumn(world, centerX, centerZ);

            if (overVoid || (overLiquid && !liquidsSafe)) {
                return generateEmergencyPlatform(data, world, centerX, centerZ, overVoid);
            }
        }

        // STAGE 4: Fallback strategies
        if (!strictHeight) {
            return getFallbackPosition(data, world, centerX, centerZ, heightMode, preferredY);
        }

        return null;
    }

    /* ------------------------------------------------------------ */
    /*  Core search algorithms                                      */
    /* ------------------------------------------------------------ */

    private Vec3d searchExpandingSquare(SerializableData.Instance data, ServerWorld world,
                                        int centerX, int centerZ, String heightMode,
                                        double preferredY, boolean strictHeight,
                                        int maxRadius, int maxAttempts) {

        WorldBorder worldBorder = world.getWorldBorder();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        int attempts = 0;

        // Use expanding square pattern
        for (int radius = 1; radius <= maxRadius && attempts < maxAttempts; radius *= 2) {
            // Iterate in a square around the center point
            for (BlockPos pos : BlockPos.iterateInSquare(new BlockPos(centerX, 0, centerZ),
                    radius, Direction.EAST, Direction.SOUTH)) {

                if (attempts >= maxAttempts) break;

                int x = pos.getX();
                int z = pos.getZ();

                // Check world border
                mutablePos.set(x, (int)preferredY, z);
                if (!worldBorder.contains(mutablePos)) {
                    continue;
                }

                // Try this column
                Vec3d candidate = findPositionAtColumn(data, world, x, z,
                        heightMode, preferredY, strictHeight);
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
                                       double preferredY, boolean strictHeight) {

        return switch (heightMode) {
            case HEIGHT_FIXED -> findPositionAtFixedY(data, world, x, z, (int)preferredY, strictHeight);
            case HEIGHT_RELATIVE -> findPositionAroundY(data, world, x, z, (int)preferredY, strictHeight);
            case HEIGHT_UNEXPOSED -> findUnexposedPosition(data, world, x, z, (int)preferredY, strictHeight);
            default -> findExposedPosition(data, world, x, z, strictHeight);
        };
    }

    /* ------------------------------------------------------------ */
    /*  Height mode implementations                                 */
    /* ------------------------------------------------------------ */

    private Vec3d findPositionAtFixedY(SerializableData.Instance data, ServerWorld world,
                                       int x, int z, int targetY, boolean strictHeight) {

        // Check if position is valid at exactly targetY
        if (isPositionValid(data, world, x, targetY, z)) {
            return toCenterVec3d(x, targetY, z);
        }

        if (strictHeight) {
            return null;
        }

        // Search vertically around targetY
        return searchVertically(data, world, x, z, targetY);
    }

    private Vec3d findPositionAroundY(SerializableData.Instance data, ServerWorld world,
                                      int x, int z, int referenceY, boolean strictHeight) {

        // Start from referenceY and search both directions
        Vec3d found = searchVertically(data, world, x, z, referenceY);
        if (found != null) {
            return found;
        }

        if (strictHeight) {
            return null;
        }

        // Attempt to fall back to surface
        found = getSurfacePosition(world, x, z);
        if (found != null && isPositionValid(data, world, (int)found.x, (int)found.y, (int)found.z)) {
            return found;
        }

        return null;
    }

    private Vec3d findUnexposedPosition(SerializableData.Instance data, ServerWorld world,
                                        int x, int z, int startY, boolean strictHeight) {

        int topY = world.getTopY();
        int bottomY = world.getBottomY();
        int searchStartY = Math.min(startY, topY - 1);

        // Search downward for first unexposed position
        for (int y = searchStartY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(x, y, z);

            // Check if position is not exposed to the sky
            if (!isSkyAbove(world, pos)) {
                if (isPositionValid(data, world, x, y, z)) {
                    return toCenterVec3d(x, y, z);
                }
            }
        }

        if (strictHeight) {
            return null;
        }

        // Fall back to any valid position
        return searchVertically(data, world, x, z, startY);
    }

    private Vec3d findExposedPosition(SerializableData.Instance data, ServerWorld world,
                                      int x, int z, boolean strictHeight) {

        // Get surface position with proper liquid safety
        Vec3d surfacePos = getSurfacePosition(world, x, z);
        if (surfacePos != null) {
            BlockPos pos = new BlockPos((int)surfacePos.x, (int)surfacePos.y, (int)surfacePos.z);

            // Check if position is exposed to the sky
            if (isSkyAbove(world, pos) && isPositionValid(data, world, x, (int)surfacePos.y, z)) {
                return surfacePos;
            }
        }

        if (strictHeight) {
            // Search upward from surface for exposed position
            int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            for (int y = surfaceY; y <= world.getTopY(); y++) {
                BlockPos pos = new BlockPos(x, y, z);

                // Check if position is exposed to the sky
                if (isSkyAbove(world, pos) && isPositionValid(data, world, x, y, z)) {
                    return toCenterVec3d(x, y, z);
                }
            }
        }

        return null;
    }

    private Vec3d searchVertically(SerializableData.Instance data, ServerWorld world,
                                   int x, int z, int centerY) {

        int topY = world.getTopY();
        int bottomY = world.getBottomY();

        // Search in both directions from center
        for (int offset = 0; offset <= PositionFinder.VERTICAL_SEARCH_RANGE; offset++) {
            // Try above
            int yAbove = centerY + offset;
            if (yAbove <= topY && isPositionValid(data, world, x, yAbove, z)) {
                return toCenterVec3d(x, yAbove, z);
            }

            // Try below (skip offset 0 to avoid checking centerY twice)
            if (offset > 0) {
                int yBelow = centerY - offset;
                if (yBelow >= bottomY && isPositionValid(data, world, x, yBelow, z)) {
                    return toCenterVec3d(x, yBelow, z);
                }
            }
        }

        return null;
    }

    /* ------------------------------------------------------------ */
    /*  Position validation                                         */
    /* ------------------------------------------------------------ */

    private boolean isPositionValid(SerializableData.Instance data, ServerWorld world,
                                    int x, int y, int z) {

        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }

        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockPos groundPos = new BlockPos(x, y - 1, z);

        // Check for void
        if (isColumnEmpty(world, x, z)) {
            return false;
        }

        // Check liquids
        boolean liquidsSafe = data.getBoolean("liquids_safe");
        if (!liquidsSafe) {
            if (isUnsafeLiquid(world, feetPos) ||
                    isUnsafeLiquid(world, headPos) ||
                    isUnsafeLiquid(world, groundPos)) {
                return false;
            }
        }

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        // Feet must be passable or replaceable
        if (isBlockNotPassableOrReplaceable(feetState, world, feetPos)) {
            return false;
        }

        // Head must be passable or replaceable
        if (isBlockNotPassableOrReplaceable(headState, world, headPos)) {
            return false;
        }

        // Ground must be solid
        return isSolidGround(groundState, world, groundPos);
    }

    private boolean isColumnEmpty(ServerWorld world, int x, int z) {
        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isSolidBlock(world, pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSolidGround(BlockState state, ServerWorld world, BlockPos pos) {
        // Must be a solid block that can support entities
        return state.isSolidBlock(world, pos);
    }

    private boolean isBlockNotPassableOrReplaceable(BlockState state, ServerWorld world, BlockPos pos) {
        // Check if block is air
        if (state.isAir()) {
            return false;
        }

        // Check if block is replaceable (like tall grass, flowers, etc.)
        if (state.isReplaceable()) {
            return false;
        }

        // Check if block is not a full cube (like fences, slabs, etc.)
        if (!state.isOpaqueFullCube(world, pos)) {
            return false;
        }

        // For non-solid blocks like glass panes, fences, etc.
        return state.isSolidBlock(world, pos);
    }

    private boolean isUnsafeLiquid(ServerWorld world, BlockPos pos) {
        Fluid fluid = world.getFluidState(pos).getFluid();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER ||
                fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    /* ------------------------------------------------------------ */
    /*  Surface and liquid handling                                 */
    /* ------------------------------------------------------------ */

    private Vec3d getSurfacePosition(ServerWorld world, int x, int z) {
        // Get world surface height
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);

        // Find the first air block above solid ground
        for (int y = surfaceY; y >= world.getBottomY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos abovePos = pos.up();

            BlockState state = world.getBlockState(pos);
            BlockState aboveState = world.getBlockState(abovePos);

            if (isSolidGround(state, world, pos) &&
                    (aboveState.isAir() || aboveState.isReplaceable())) {
                return toCenterVec3d(x, y + 1, z);
            }
        }

        return null;
    }

    private Integer findLiquidSurface(ServerWorld world, int x, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, world.getTopY(), z);

        for (int y = world.getTopY(); y >= world.getBottomY(); y--) {
            mutable.setY(y);
            if (!world.getFluidState(mutable).isEmpty() &&
                    world.getBlockState(mutable.up()).isAir()) {
                return y + 1;  // Position above liquid surface
            }
        }

        return null;  // No liquid surface found
    }

    private boolean isOverLiquidColumn(ServerWorld world, int x, int z) {
        Integer liquidSurface = findLiquidSurface(world, x, z);
        return liquidSurface != null;
    }

    private boolean isSkyAbove(ServerWorld world, BlockPos pos) {
        int worldTopY = world.getTopY();
        BlockPos.Mutable mutable = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());

        for (int y = pos.getY() + 1; y <= worldTopY; y++) {
            mutable.setY(y);
            BlockState state = world.getBlockState(mutable);

            // Skip air and obviously non-blocking blocks
            if (state.isAir() || state.isReplaceable()) {
                continue;
            }

            // Skip translucent blocks (glass, leaves, etc.)
            if (!state.isOpaque()) {
                continue;
            }

            // If it's solid or opaque, it blocks the sky
            if (state.isSolidBlock(world, mutable) ||
                    state.isOpaqueFullCube(world, mutable)) {
                return false;
            }
        }

        return true;
    }

    /* ------------------------------------------------------------ */
    /*  Platform generation                                         */
    /* ------------------------------------------------------------ */

    private Vec3d generateEmergencyPlatform(SerializableData.Instance data, ServerWorld world,
                                            int x, int z, boolean overVoid) {

        int platformY;
        if (overVoid) {
            // Platform above void
            platformY = Math.max(world.getBottomY() + PLATFORM_HEIGHT_ABOVE_VOID,
                    world.getSeaLevel());
        } else {
            // Platform above liquid
            Integer liquidSurface = findLiquidSurface(world, x, z);
            platformY = liquidSurface != null ? liquidSurface - 1 : world.getSeaLevel();
        }

        // Ensure platform is within world bounds
        platformY = MathHelper.clamp(platformY,
                world.getBottomY() + 1,
                world.getTopY() - 1);

        // Use the PlatformGenerator to create the platform
        FrostedLib.LOGGER.info("Generated platform at [{}, {}, {}] in dimension {}", x, platformY, z, world.getRegistryKey().getValue());

        return platformGenerator.generatePlatformAtPosition(data, world, x, z, platformY);
    }

    /* ------------------------------------------------------------ */
    /*  Helper methods                                              */
    /* ------------------------------------------------------------ */

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
                                      int x, int z, String originalMode, double preferredY) {

        if (originalMode.equals(HEIGHT_EXPOSED) || originalMode.equals(HEIGHT_FIXED)) {
            // Try unexposed as fallback
            return findUnexposedPosition(data, world, x, z, (int)preferredY, false);
        } else {
            // Try exposed as fallback
            return findExposedPosition(data, world, x, z, false);
        }
    }

    private Vec3d toCenterVec3d(int x, int y, int z) {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }
}