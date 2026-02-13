package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.gen.structure.Structure;
import com.mojang.datafixers.util.Pair;

import java.util.Optional;

public class StructureTeleportAction extends BaseTeleportAction {

    private static final SerializableData DATA;

    static {
        DATA = createCommonDataWithoutHeightDefault()
                .add("target_height", SerializableDataTypes.STRING, "exposed") // Add default for structure
                .add("strict_height", SerializableDataTypes.BOOLEAN, false)
                // Structure specific fields
                .add("structure_id", SerializableDataTypes.IDENTIFIER)
                .add("chunk_search_radius", SerializableDataTypes.INT, 100)
                .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0);
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld world) {
        Identifier structureId = data.getId("structure_id");
        if (structureId == null) {
            throw new IllegalArgumentException("Parameter 'structure_id' must be specified for structure teleport");
        }

        int searchRadius = data.getInt("chunk_search_radius");
        double scaleFactor = data.getDouble("scale_factor");

        // Calculate search start position using scale factor.
        BlockPos searchStartPos = calculateScaledSearchPosition(entity, world, scaleFactor);

        // Get the structure position closest to scaled position
        Optional<Pair<BlockPos, Structure>> structureResult = getStructurePos(world, structureId, searchStartPos, searchRadius);

        if (structureResult.isEmpty()) {
            throw new RuntimeException("Could not find structure: " + structureId +
                    " within " + searchRadius + " chunks of position " + searchStartPos.toShortString() +
                    " in dimension " + world.getRegistryKey().getValue());
        }

        Pair<BlockPos, Structure> structurePair = structureResult.get();
        BlockPos structurePos = structurePair.getFirst();
        Structure structure = structurePair.getSecond();

        // Get the actual structure start
        ChunkPos structureChunkPos = new ChunkPos(structurePos.getX() >> 4, structurePos.getZ() >> 4);
        StructureStart structureStart = world.getStructureAccessor().getStructureStart(
                ChunkSectionPos.from(structureChunkPos, 0),
                structure,
                world.getChunk(structurePos)
        );

        if (structureStart == null || !structureStart.hasChildren()) {
            throw new RuntimeException("Structure found but not generated at position: " + structurePos.toShortString());
        }

        // Get the center of the structure's bounding box
        BlockPos structureCenter = new BlockPos(structureStart.getBoundingBox().getCenter());

        // Return the structure center - PositionFinder will handle the safe position search
        // with proper height mode configuration
        return new Vec3d(structureCenter.getX(), 64, structureCenter.getZ()); // Y=64 as reference point
    }

    private Optional<Pair<BlockPos, Structure>> getStructurePos(ServerWorld world, Identifier structureId,
                                                                BlockPos searchCenter, int radius) {
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        RegistryEntryList<Structure> structureRegistryEntryList = null;

        // First try to get the structure by key
        RegistryKey<Structure> structureKey = RegistryKey.of(RegistryKeys.STRUCTURE, structureId);
        var entry = structureRegistry.getEntry(structureKey);
        if (entry.isPresent()) {
            structureRegistryEntryList = RegistryEntryList.of(entry.get());
        }

        // If not found by key, try by tag
        if (structureRegistryEntryList == null) {
            TagKey<Structure> structureTag = TagKey.of(RegistryKeys.STRUCTURE, structureId);
            var entryList = structureRegistry.getEntryList(structureTag);
            if (entryList.isPresent()) {
                structureRegistryEntryList = entryList.get();
            }
        }

        if (structureRegistryEntryList == null) {
            return Optional.empty();
        }

        // Use the chunk generator to locate the structure
        com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>> structurePos = world
                .getChunkManager()
                .getChunkGenerator()
                .locateStructure(
                        world,
                        structureRegistryEntryList,
                        searchCenter,
                        radius,
                        false
                );

        if (structurePos == null) {
            return Optional.empty();
        }

        return Optional.of(new Pair<>(structurePos.getFirst(), structurePos.getSecond().value()));
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "structure_teleport"),
                DATA,
                (data, entity) -> new StructureTeleportAction().execute(data, entity)
        );
    }
}