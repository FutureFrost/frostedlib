package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import com.mojang.datafixers.util.Pair;

import java.util.Optional;

public class BiomeTeleportAction extends BaseTeleportAction {

    private static final SerializableData DATA;

    static {
        DATA = new SerializableData()
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
                // REMOVED: target_height, strict_height for biome teleport
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)
                .add("error_message", SerializableDataTypes.STRING, null)
                // Biome specific fields
                .add("biome_id", SerializableDataTypes.IDENTIFIER)
                .add("chunk_search_radius", SerializableDataTypes.INT, 6400)
                .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0);
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld world) {
        Identifier biomeId = data.getId("biome_id");
        if (biomeId == null) {
            throw new IllegalArgumentException("Parameter 'biome_id' must be specified for biome teleport");
        }

        Registry<Biome> biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);

        Optional<RegistryEntry.Reference<Biome>> biomeEntry = biomeRegistry.getEntry(biomeKey);

        if (biomeEntry.isEmpty()) {
            throw new IllegalArgumentException("Biome not found in registry: " + biomeId);
        }

        RegistryEntry<Biome> targetBiome = biomeEntry.get();
        int searchRadius = data.getInt("chunk_search_radius");
        double scaleFactor = data.getDouble("scale_factor");

        // Calculate search start position using scale factor
        BlockPos searchStartPos = calculateScaledSearchPosition(entity, world, scaleFactor);

        // Search for biome
        Pair<BlockPos, RegistryEntry<Biome>> biomeResult = world.locateBiome(
                biome -> biome.equals(targetBiome),
                searchStartPos,
                searchRadius,
                64,
                64
        );

        if (biomeResult == null) {
            throw new RuntimeException("Could not find biome: " + biomeId +
                    " within radius " + searchRadius + " in dimension " + world.getRegistryKey().getValue());
        }

        BlockPos biomePos = biomeResult.getFirst();

        // Find a safe position at this biome location
        BlockPos safePos = findSafePositionInBiome(world, biomePos);

        if (safePos != null) {
            return new Vec3d(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        }

        // Fallback to biome position
        return new Vec3d(biomePos.getX() + 0.5, biomePos.getY(), biomePos.getZ() + 0.5);
    }

    private BlockPos findSafePositionInBiome(ServerWorld world, BlockPos biomePos) {
        // Search for a safe surface position at or near the biome location
        int searchRadius = 16;

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue; // Only check perimeter
                    }

                    int x = biomePos.getX() + dx;
                    int z = biomePos.getZ() + dz;

                    // Find surface position at this XZ
                    BlockPos surfacePos = findSurfacePosition(world, x, z);
                    if (surfacePos != null && isPositionSafeForEntity(world, surfacePos)) {
                        return surfacePos;
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findSurfacePosition(ServerWorld world, int x, int z) {
        // Find the highest solid block with air above
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(x, world.getTopY(), z);

        for (int y = world.getTopY(); y > world.getBottomY(); y--) {
            mutablePos.setY(y);
            BlockState state = world.getBlockState(mutablePos);
            BlockState aboveState = world.getBlockState(mutablePos.up());

            if (state.isSolidBlock(world, mutablePos) && aboveState.isAir()) {
                return mutablePos.up(); // Return position above solid block
            }
        }

        return null;
    }

    private boolean isPositionSafeForEntity(ServerWorld world, BlockPos pos) {
        BlockPos feetPos = pos;
        BlockPos headPos = pos.up();
        BlockPos groundPos = pos.down();

        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        BlockState groundState = world.getBlockState(groundPos);

        boolean feetSafe = feetState.isAir() || !feetState.isOpaque();
        boolean headSafe = headState.isAir() || !headState.isOpaque();
        boolean groundSolid = groundState.isSolidBlock(world, groundPos);

        return feetSafe && headSafe && groundSolid;
    }

    @Override
    protected Vec3d calculateSearchStartPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        double scaleFactor = data.getDouble("scale_factor");
        BlockPos scaledPos = calculateScaledSearchPosition(entity, targetWorld, scaleFactor);
        return Vec3d.ofCenter(scaledPos);
    }

    @Override
    protected SerializableData getData() {
        return DATA;
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "biome_teleport"),
                DATA,
                (data, entity) -> new BiomeTeleportAction().execute(data, entity)
        );
    }
}