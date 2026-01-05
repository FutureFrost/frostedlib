package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.FrostedLib;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

public class PositionFinder {

    private static final String HEIGHT_EXPOSED = "exposed";
    private static final String HEIGHT_UNEXPOSED = "unexposed";
    private static final String HEIGHT_RELATIVE = "relative";
    private static final String HEIGHT_FIXED = "fixed";

    /* ------------------------------------------------------------ */
    /*  Public entry point                                          */
    /* ------------------------------------------------------------ */

    public Vec3d findSafePosition(SerializableData.Instance data, Entity entity,
                                  ServerWorld world, int centerX, int centerZ) {

        String heightMode = Objects.requireNonNullElse(
                data.getString("target_height"),
                HEIGHT_EXPOSED
        );

        double preferredY = resolvePreferredY(data, entity, heightMode);
        boolean strictHeight = data.getBoolean("strict_height");
        boolean generatePlatform = data.getBoolean("generate_platform");

        // STAGE 1 – exact position
        Vec3d exact = findSafeHeightPosition(
                data, world, centerX, centerZ,
                heightMode, preferredY, strictHeight
        );
        if (exact != null && isPositionActuallySafe(data, world, exact)) {
            return exact;
        }

        // STAGE 2 – expanding search
        Vec3d expanding = findSafePositionExpandingSearch(
                data, world, centerX, centerZ,
                heightMode, preferredY,
                Math.min(data.getInt("search_radius"), 128),
                data.getInt("max_search_attempts"),
                strictHeight
        );
        if (expanding != null) return expanding;

        // STAGE 3 – platform
        boolean overLiquid = isOverLiquidSurface(world, centerX, centerZ);
        if (generatePlatform || overLiquid) {
            PlatformGenerator generator = new PlatformGenerator();
            Vec3d platform = generator.generatePlatformAtPosition(
                    data, world, centerX, centerZ,
                    heightMode, preferredY, strictHeight, overLiquid
            );
            if (platform != null) return platform;
        }

        // STAGE 4 / 5 – fallbacks
        if (strictHeight) return null;
        return getOppositeHeightFallback(
                data, world, centerX, centerZ,
                heightMode, preferredY
        );
    }

    /* ------------------------------------------------------------ */
    /*  Height mode handling                                        */
    /* ------------------------------------------------------------ */

    public Vec3d findSafeHeightPosition(SerializableData.Instance data,
                                        ServerWorld world,
                                        int x, int z,
                                        String mode,
                                        double preferredY,
                                        boolean strictHeight) {

        int bottom = world.getBottomY();
        int top = world.getTopY();

        return switch (mode) {

            case HEIGHT_FIXED -> {
                Vec3d found = searchVerticallyAroundY(
                        data, world, x, z,
                        (int) preferredY,
                        bottom, top
                );
                yield (found != null || strictHeight)
                        ? found
                        : getSurfacePosition(world, x, z);
            }

            case HEIGHT_RELATIVE -> {
                Vec3d found = searchVerticallyAroundY(
                        data, world, x, z,
                        (int) preferredY,
                        bottom, top
                );
                yield found != null ? found : getSurfacePosition(world, x, z);
            }

            case HEIGHT_UNEXPOSED -> {
                Vec3d found = searchDownwardForUnexposed(
                        data, world, x, z,
                        Math.min((int) preferredY, top - 10),
                        bottom
                );
                yield (found != null || strictHeight)
                        ? found
                        : getSurfacePosition(world, x, z);
            }

            default -> findExposedPosition(
                    data, world, x, z, strictHeight
            );
        };
    }

    /* ------------------------------------------------------------ */
    /*  Extracted helpers                                           */
    /* ------------------------------------------------------------ */

    private double resolvePreferredY(SerializableData.Instance data, Entity entity, String mode) {
        if (HEIGHT_FIXED.equals(mode)) {
            return data.getDouble("target_y");
        }
        if (HEIGHT_RELATIVE.equals(mode)) {
            Double y = data.get("target_y");
            return Objects.requireNonNullElseGet(y, entity::getY);
        }
        return entity.getY();
    }

    private Vec3d searchVerticallyAroundY(SerializableData.Instance data,
                                          ServerWorld world,
                                          int x, int z,
                                          int centerY,
                                          int bottom,
                                          int top) {

        for (int offset = 0; offset < 64; offset++) {
            int up = centerY + offset;
            int down = centerY - offset;

            if (up <= top) {
                Vec3d pos = tryCreateSafePosition(data, world, x, up, z);
                if (pos != null) return pos;
            }

            if (down >= bottom) {
                Vec3d pos = tryCreateSafePosition(data, world, x, down, z);
                if (pos != null) return pos;
            }
        }
        return null;
    }

    private Vec3d searchDownwardForUnexposed(SerializableData.Instance data,
                                             ServerWorld world,
                                             int x, int z,
                                             int startY,
                                             int bottom) {

        for (int y = startY; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isSkyVisible(pos)) {
                Vec3d found = tryCreateSafePosition(data, world, x, y, z);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Vec3d findExposedPosition(SerializableData.Instance data,
                                      ServerWorld world,
                                      int x, int z,
                                      boolean strictHeight) {

        Vec3d surface = getSurfacePosition(world, x, z);
        if (!strictHeight) return surface;

        BlockPos pos = new BlockPos((int) surface.x, (int) surface.y - 1, (int) surface.z);
        if (world.isSkyVisible(pos) || isOverLiquidSurface(world, x, z)) {
            return surface;
        }

        for (int y = (int) surface.y; y <= world.getTopY(); y++) {
            if (isPositionSafeForEntity(data, world, x, y, z) &&
                    world.isSkyVisible(new BlockPos(x, y, z))) {
                return vec(x, y, z);
            }
        }
        return null;
    }

    private Vec3d tryCreateSafePosition(SerializableData.Instance data,
                                        ServerWorld world,
                                        int x, int y, int z) {

        if (!isPositionSafeForEntity(data, world, x, y, z)) return null;

        BlockPos pos = new BlockPos(x, y, z);
        if (isBlockUnsafeLiquid(data, world, pos) ||
                isBlockUnsafeLiquid(data, world, pos.down())) {
            return null;
        }
        return vec(x, y, z);
    }

    private Vec3d vec(int x, int y, int z) {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    /* ------------------------------------------------------------ */
    /*  Surface and fallback methods                                */
    /* ------------------------------------------------------------ */

    public Vec3d getSurfacePosition(ServerWorld world, int x, int z) {
        try {
            int liquidY = findTrueLiquidSurface(world, x, z);
            if (liquidY != -1) return vec(x, liquidY, z);

            for (int y = world.getTopY(); y > world.getBottomY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                BlockState above = world.getBlockState(pos.up());

                if (state.isSolidBlock(world, pos) && above.isAir()) {
                    return new Vec3d(x + 0.5, y + 1, z + 0.5);
                }
            }

            int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            return vec(x, surfaceY, z);

        } catch (Exception e) {
            return vec(x, world.getSeaLevel(), z);
        }
    }

    private Vec3d getOppositeHeightFallback(SerializableData.Instance data,
                                            ServerWorld world,
                                            int x, int z,
                                            String originalMode,
                                            double preferredY) {

        if (originalMode.equals(HEIGHT_EXPOSED) || originalMode.equals(HEIGHT_FIXED)) {
            return findSafeHeightPosition(data, world, x, z, HEIGHT_UNEXPOSED, preferredY, false);
        } else {
            return getSurfacePosition(world, x, z);
        }
    }

    /* ------------------------------------------------------------ */
    /*  Expanded search                                             */
    /* ------------------------------------------------------------ */

    private Vec3d findSafePositionExpandingSearch(SerializableData.Instance data, ServerWorld world,
                                                  int centerX, int centerZ,
                                                  String heightMode, double preferredY,
                                                  int maxRadius, int maxAttempts, boolean strictHeight) {

        int attempts = 0;

        for (int radius : getRadiusSteps(maxRadius)) {
            if (attempts >= maxAttempts) break;

            // Search circle points
            attempts += searchCirclePoints(data, world, centerX, centerZ,
                    heightMode, preferredY, radius, maxAttempts - attempts, strictHeight);

            // Search vertical offsets for fixed/relative modes
            if ((heightMode.equals(HEIGHT_FIXED) || heightMode.equals(HEIGHT_RELATIVE)) && attempts < maxAttempts) {
                attempts += searchVerticalOffsets(data, world, centerX, centerZ,
                        heightMode, preferredY, radius, maxAttempts - attempts, strictHeight);
            }
        }

        return null;
    }

    private List<Integer> getRadiusSteps(int maxRadius) {
        List<Integer> steps = new ArrayList<>();
        for (int r = 1; r <= maxRadius; r *= 2) {
            steps.add(r);
            if (steps.size() >= 8) break;
        }
        if (!steps.contains(maxRadius)) steps.add(maxRadius);
        return steps;
    }

    private int searchCirclePoints(SerializableData.Instance data, ServerWorld world,
                                   int centerX, int centerZ,
                                   String heightMode, double preferredY,
                                   int radius, int remainingAttempts, boolean strictHeight) {
        int attempts = 0;
        int points = Math.min((int) (2 * Math.PI * radius) / 4, 16);
        points = Math.max(points, 4);

        for (int i = 0; i < points && attempts < remainingAttempts; i++) {
            double angle = 2 * Math.PI * i / points;
            int x = centerX + (int) (radius * Math.cos(angle));
            int z = centerZ + (int) (radius * Math.sin(angle));

            Vec3d pos = findSafeHeightPosition(data, world, x, z, heightMode, preferredY, strictHeight);
            if (pos != null && isPositionActuallySafe(data, world, pos)) {
                return remainingAttempts; // Found, exit early
            }
            attempts++;
        }
        return attempts;
    }

    private int searchVerticalOffsets(SerializableData.Instance data, ServerWorld world,
                                      int centerX, int centerZ,
                                      String heightMode, double preferredY,
                                      int radius, int remainingAttempts, boolean strictHeight) {
        int attempts = 0;
        for (int yOffset = -8; yOffset <= 8 && attempts < remainingAttempts; yOffset += 4) {
            double testY = preferredY + yOffset;

            for (int i = 0; i < 4 && attempts < remainingAttempts; i++) {
                double angle = Math.PI * i / 2;
                int x = centerX + (int) (radius * Math.cos(angle));
                int z = centerZ + (int) (radius * Math.sin(angle));

                Vec3d pos = findSafeHeightPosition(data, world, x, z, heightMode, testY, strictHeight);
                if (pos != null && isPositionActuallySafe(data, world, pos)) {
                    return remainingAttempts; // Found, exit early
                }
                attempts++;
            }
        }
        return attempts;
    }

    /* ------------------------------------------------------------ */
    /*  Safety checks                                               */
    /* ------------------------------------------------------------ */

    private boolean isPositionSafeForEntity(SerializableData.Instance data, ServerWorld world, int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) return false;

        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = new BlockPos(x, y + 1, z);
        BlockPos ground = new BlockPos(x, y - 1, z);

        if (isBlockUnsafeLiquid(data, world, feet) ||
                isBlockUnsafeLiquid(data, world, head) ||
                isBlockUnsafeLiquid(data, world, ground)) return false;

        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        BlockState groundState = world.getBlockState(ground);

        return (feetState.isAir() || !feetState.isOpaque()) &&
                (headState.isAir() || !headState.isOpaque()) &&
                (groundState.isSolidBlock(world, ground) || y <= world.getBottomY() + 1);
    }

    public boolean isPositionActuallySafe(SerializableData.Instance data, ServerWorld world, Vec3d pos) {
        return isPositionSafeForEntity(data, world, (int) pos.x, (int) pos.y, (int) pos.z);
    }

    private boolean isBlockUnsafeLiquid(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return !isLiquidSafe(data, world, pos);
        }
        return false;
    }

    private boolean isLiquidSafe(SerializableData.Instance data, ServerWorld world, BlockPos pos) {
        boolean liquidsSafe = data.getBoolean("liquids_safe");
        if (liquidsSafe) return true;

        SerializableData.Instance fluidPredicate = data.get("liquid_condition");
        if (fluidPredicate == null) return false;

        try {
            Fluid fluid = world.getFluidState(pos).getFluid();
            return fluid != Fluids.WATER &&
                    fluid != Fluids.FLOWING_WATER &&
                    fluid != Fluids.LAVA &&
                    fluid != Fluids.FLOWING_LAVA;
        } catch (Exception e) {
            FrostedLib.LOGGER.error("Error checking liquid safety", e);
            return false;
        }
    }

    public boolean isOverLiquidSurface(ServerWorld world, int x, int z) {
        BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        BlockState state = world.getBlockState(topPos);
        return !state.getFluidState().isEmpty();
    }

    public static int findTrueLiquidSurface(ServerWorld world, int x, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, world.getTopY(), z);

        for (int y = world.getTopY(); y > world.getBottomY(); y--) {
            mutable.setY(y);
            BlockState state = world.getBlockState(mutable);

            if (!state.getFluidState().isEmpty() && world.getBlockState(mutable.up()).isAir()) {
                return y + 1;
            }
        }
        return -1;
    }
}