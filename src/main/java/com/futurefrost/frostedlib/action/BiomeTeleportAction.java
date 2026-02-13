package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
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
        DATA = createCommonDataWithoutHeightDefault()
                .add("target_height", SerializableDataTypes.STRING, "exposed") // Add default for biome
                .add("strict_height", SerializableDataTypes.BOOLEAN, false)
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

        // Return the biome position
        return new Vec3d(biomePos.getX(), 64, biomePos.getZ()); // Y=64 as reference point
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "biome_teleport"),
                DATA,
                (data, entity) -> new BiomeTeleportAction().execute(data, entity)
        );
    }
}