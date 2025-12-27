package com.futurefrost.frostedlib.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class RelativeTeleportAction extends BaseTeleportAction {

    private static final SerializableData DATA;

    static {
        // Start with common data
        DATA = createCommonData()
                // Add specific fields for relative teleport
                .add("scale_factor", SerializableDataTypes.DOUBLE, 1.0)
                .add("target_y", SerializableDataTypes.DOUBLE, null);
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        double scale = data.getDouble("scale_factor");
        double targetY = entity.getY();

        // Check if target_y is specified for fixed height mode
        if (data.isPresent("target_y")) {
            targetY = data.getDouble("target_y");
        }

        return new Vec3d(
                entity.getX() * scale,
                targetY,
                entity.getZ() * scale
        );
    }

    @Override
    protected SerializableData getData() {
        return DATA;
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "relative_teleport"),
                DATA,
                (data, entity) -> new RelativeTeleportAction().execute(data, entity)
        );
    }
}