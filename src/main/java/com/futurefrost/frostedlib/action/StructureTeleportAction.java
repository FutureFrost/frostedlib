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
        // Start with common data
        DATA = createCommonData()
                // Add specific fields for structure teleport
                .add("structure_id", SerializableDataTypes.IDENTIFIER)
                .add("chunk_search_radius", SerializableDataTypes.INT, 64)
                .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0)
                .add("target_y", SerializableDataTypes.DOUBLE, null);
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
        TagKey<Structure> structureTagKey = TagKey.of(RegistryKeys.STRUCTURE, structureId);

        BlockPos structurePos = world.locateStructure(
                structureTagKey,
                entity.getBlockPos(),
                searchRadius,
                false
        );

        if (structurePos == null) {
            throw new RuntimeException("Could not find structure: " + structureId + " within " + searchRadius + " chunks.");
        }

        return new Vec3d(structurePos.getX() + 0.5, structurePos.getY(), structurePos.getZ() + 0.5);
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