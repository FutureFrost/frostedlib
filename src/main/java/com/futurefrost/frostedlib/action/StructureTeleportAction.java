package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;

public class StructureTeleportAction extends BaseTeleportAction {

    private static final SerializableData DATA;

    static {
        // Start with common data with "exposed" default
        DATA = createCommonDataWithExposedDefault()
                // Add specific fields for structure teleport
                .add("structure_id", SerializableDataTypes.IDENTIFIER)
                .add("chunk_search_radius", SerializableDataTypes.INT, 64)
                .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0)
                .add("target_y", SerializableDataTypes.DOUBLE, null);  // Optional
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld world) {
        Identifier structureId = data.getId("structure_id");
        if (structureId == null) {
            throw new IllegalArgumentException("Parameter 'structure_id' must be specified for structure teleport");
        }

        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Structure structure = structureRegistry.get(structureId);

        if (structure == null) {
            throw new RuntimeException("Structure not found in registry: " + structureId);
        }

        int searchRadius = data.getInt("chunk_search_radius");
        double scaleFactor = data.getDouble("scale_factor");

        // Calculate search start position using scale factor
        BlockPos searchStartPos = calculateScaledSearchPosition(entity, world, scaleFactor);

        TagKey<Structure> structureTagKey = TagKey.of(RegistryKeys.STRUCTURE, structureId);

        // Search for structure from the scaled position in the target dimension
        BlockPos structurePos = world.locateStructure(
                structureTagKey,
                searchStartPos,
                searchRadius,
                false
        );

        if (structurePos == null) {
            // Try alternative search method
            structurePos = findStructureAlternative(world, structureId, searchStartPos, searchRadius);

            if (structurePos == null) {
                throw new RuntimeException("Could not find structure: " + structureId +
                        " within " + searchRadius + " chunks of position " + searchStartPos.toShortString() +
                        " in dimension " + world.getRegistryKey().getValue());
            }
        }

        // Return structure position
        return new Vec3d(structurePos.getX() + 0.5, structurePos.getY(), structurePos.getZ() + 0.5);
    }

    private BlockPos findStructureAlternative(ServerWorld world, Identifier structureId, BlockPos searchStart, int radius) {
        // Alternative search method if the primary one fails
        try {
            // Try with a different tag format
            TagKey<Structure> altTag = TagKey.of(RegistryKeys.STRUCTURE,
                    new Identifier(structureId.getNamespace(), structureId.getPath() + "s"));

            return world.locateStructure(altTag, searchStart, radius, false);
        } catch (Exception e) {
            return null;
        }
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