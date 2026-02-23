package com.futurefrost.frostedlib.action;

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
        DATA = new SerializableData()
                // Core Targeting
                .add("target_height", SerializableDataTypes.STRING, "fixed")  // Default to "fixed" for fixed teleport
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                // Fixed teleport specific fields
                .add("target_x", SerializableDataTypes.DOUBLE)
                .add("target_y", SerializableDataTypes.DOUBLE, 64.0)  // Default to sea level if not specified
                .add("target_z", SerializableDataTypes.DOUBLE);
    }

    @Override
    protected Vec3d calculateTargetPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        double targetX = data.getDouble("target_x");
        double targetY = data.getDouble("target_y");  // Get target_y (has default value)
        double targetZ = data.getDouble("target_z");

        // Always return the exact target position
        // The PositionFinder will use target_height="fixed" to try to get as close to targetY as possible
        return new Vec3d(targetX, targetY, targetZ);
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "fixed_teleport"),
                DATA,
                (data, entity) -> new FixedTeleportAction().execute(data, entity)
        );
    }
}