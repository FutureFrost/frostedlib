package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
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
                // REMOVED: target_height, strict_height for structure teleport
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)
                .add("error_message", SerializableDataTypes.STRING, null)
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

        // Find a safe position within the structure bounds
        BlockPos safePos = findSafePositionInStructure(world, structureStart, structureCenter);

        if (safePos != null) {
            return new Vec3d(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        }

        // Fallback to structure center
        return new Vec3d(structureCenter.getX() + 0.5, structureCenter.getY(), structureCenter.getZ() + 0.5);
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
                        searchCenter,  // KEY FIX: Use scaled position
                        radius,
                        false
                );

        if (structurePos == null) {
            return Optional.empty();
        }

        return Optional.of(new Pair<>(structurePos.getFirst(), structurePos.getSecond().value()));
    }

    private BlockPos findSafePositionInStructure(ServerWorld world, StructureStart structureStart, BlockPos center) {
        // Search within the structure's bounding box
        BlockBox bounds = structureStart.getBoundingBox();

        // Start from the center and search outward
        for (int radius = 0; radius < 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue; // Only check the perimeter at each radius
                    }

                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;

                    // Ensure we're within structure bounds
                    if (x < bounds.getMinX() || x > bounds.getMaxX() ||
                            z < bounds.getMinZ() || z > bounds.getMaxZ()) {
                        continue;
                    }

                    // Try different Y levels within structure bounds
                    for (int y = bounds.getMinY(); y <= bounds.getMaxY(); y++) {
                        BlockPos testPos = new BlockPos(x, y, z);

                        // Check if position is safe
                        if (isPositionSafeForEntity(world, testPos)) {
                            return testPos;
                        }
                    }
                }
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
                Identifier.of("frostedlib", "structure_teleport"),
                DATA,
                (data, entity) -> new StructureTeleportAction().execute(data, entity)
        );
    }
}