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
        // Start with common data but override target_height default
        DATA = new SerializableData()
                // Core Targeting
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
                .add("target_height", SerializableDataTypes.STRING, "fixed")  // Default to "fixed" for fixed teleport
                .add("strict_height", SerializableDataTypes.BOOLEAN, false)
                .add("liquids_safe", SerializableDataTypes.BOOLEAN, false)
                .add("liquid_condition", ApoliDataTypes.BLOCK_CONDITION, null)
                .add("error_message", SerializableDataTypes.STRING, null)
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

    @Override
    protected Vec3d calculateSearchStartPosition(SerializableData.Instance data, Entity entity, ServerWorld targetWorld) {
        // For fixed teleport, the search start is the exact target position
        return calculateTargetPosition(data, entity, targetWorld);
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