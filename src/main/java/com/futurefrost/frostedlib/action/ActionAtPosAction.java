package com.futurefrost.frostedlib.action;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.util.NbtHelper;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.function.Consumer;

public class ActionAtPosAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        if (entity.getWorld().isClient) return;

        Consumer<Entity> entityAction = data.get("action");
        if (entityAction == null) return;

        boolean usePositionOffset = data.getBoolean("position_offset");
        boolean useRotationOffset = data.getBoolean("rotation_offset");
        String targetId = data.getString("target_id");

        Optional<PositionData> targetPos = NbtHelper.getSavedPosition(entity, targetId);

        if (targetPos.isEmpty()) {
            FrostedLib.LOGGER.warn("Target position '{}' not found for entity {}",
                    targetId, entity.getName().getString());
            return;
        }

        // Store original values
        Vec3d originalPos = entity.getPos();
        float originalYaw = entity.getYaw();
        float originalPitch = entity.getPitch();
        Vec3d originalVelocity = entity.getVelocity();

        try {
            // Update position/rotation if needed
            if (usePositionOffset) {
                entity.setPosition(targetPos.get().x(), targetPos.get().y(), targetPos.get().z());
            }

            if (useRotationOffset) {
                entity.setYaw(targetPos.get().yaw());
                entity.setPitch(targetPos.get().pitch());
            }

            // Execute the action
            entityAction.accept(entity);

        } finally {
            // Restore everything
            entity.setPosition(originalPos.x, originalPos.y, originalPos.z);
            entity.setYaw(originalYaw);
            entity.setPitch(originalPitch);
            entity.setVelocity(originalVelocity);
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of("frostedlib", "action_at_pos"),
                new SerializableData()
                        .add("action", ApoliDataTypes.ENTITY_ACTION)
                        .add("target_id", SerializableDataTypes.STRING)
                        .add("position_offset", SerializableDataTypes.BOOLEAN, true)
                        .add("rotation_offset", SerializableDataTypes.BOOLEAN, true),
                ActionAtPosAction::action
        );
    }
}