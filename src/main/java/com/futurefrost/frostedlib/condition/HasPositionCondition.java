package com.futurefrost.frostedlib.condition;

import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Optional;

public class HasPositionCondition {

    public static boolean condition(SerializableData.Instance data, Entity entity) {
        String positionId = data.getString("position_id");
        boolean checkDimension = data.getBoolean("check_dimension");

        Optional<PositionData> savedPosition = ModComponents.PLAYER_DATA.maybeGet(entity)
                .map(component -> component.getPosition(positionId))
                .orElseGet(() ->
                        ModComponents.ENTITY_DATA.maybeGet(entity).flatMap(component -> component.getPosition(positionId))
                );

        // First check: does the position exist at all?
        if (savedPosition.isEmpty()) {
            return false;
        }

        // If we're not checking dimension, position exists so return true
        if (!checkDimension) {
            return true;
        }

        // Check dimension if specified
        PositionData pos = savedPosition.get();

        // Get the target dimension
        Identifier targetDimId = data.get("target_dimension");

        if (targetDimId != null) {
            RegistryKey<World> targetKey = RegistryKey.of(RegistryKeys.WORLD, targetDimId);
            return pos.dimension().equals(targetKey);
        } else {
            // Check if it's in the same dimension as the entity
            return pos.dimension().equals(entity.getWorld().getRegistryKey());
        }
    }

    public static ConditionFactory<Entity> getFactory() {
        return new ConditionFactory<>(
                Identifier.of("frostedlib", "has_position"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING)
                        .add("check_dimension", SerializableDataTypes.BOOLEAN, false)
                        .add("target_dimension", SerializableDataTypes.IDENTIFIER, null),
                HasPositionCondition::condition
        );
    }
}