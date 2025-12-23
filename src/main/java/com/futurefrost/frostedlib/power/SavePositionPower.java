package com.futurefrost.frostedlib.power;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.util.ComponentAccessor;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class SavePositionPower extends BaseFrostedPower {
    private final String positionId;

    public SavePositionPower(PowerType<?> type, LivingEntity entity, String positionId) {
        super(type, entity);
        this.positionId = positionId;
    }

    @Override
    public void onUse() {
        if (this.entity instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            PlayerDataComponent data = ComponentAccessor.getPlayerData((net.minecraft.entity.player.PlayerEntity) entity);
            if (data != null) {
                PositionData pos = PositionData.fromPlayer(serverPlayer);
                data.savePosition(positionId, pos);

                // Send feedback to player
                serverPlayer.sendMessage(
                        net.minecraft.text.Text.literal("Saved position '" + positionId + "'"),
                        false
                );
            }
        }
    }

    // Factory for creating this power from JSON
    public static PowerFactory createFactory() {
        return new PowerFactory<>(
                Identifier.of("frostedlib", "save_pos"),
                new SerializableData()
                        .add("position_id", SerializableDataTypes.STRING),
                data -> (type, entity) -> new SavePositionPower(
                        type,
                        entity,
                        data.getString("position_id")
                )
        ).allowCondition();
    }
}