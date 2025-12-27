package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class FixedTeleportAction extends BaseTeleportAction {

    private static final SerializableData DATA;

    static {
        // Start with common data
        DATA = createCommonData()
                // Add specific fields for fixed teleport
                .add("target_x", SerializableDataTypes.DOUBLE)
                .add("target_y", SerializableDataTypes.DOUBLE)
                .add("target_z", SerializableDataTypes.DOUBLE);
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        return new Vec3d(
                data.getDouble("target_x"),
                data.getDouble("target_y"),
                data.getDouble("target_z")
        );
    }

    @Override
    protected SerializableData getData() {
        return DATA;
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "fixed_teleport"),
                DATA,
                (data, entity) -> new FixedTeleportAction().execute(data, entity)
        );
    }
}