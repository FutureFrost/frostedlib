package com.futurefrost.frostedlib.power;

import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.util.ComponentAccessor;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import net.minecraft.entity.LivingEntity;

public abstract class BaseFrostedPower extends Power implements Active {
    private Key key;

    public BaseFrostedPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @Override
    public void onUse() {
        // This will be called when the key is pressed
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public void setKey(Key key) {
        this.key = key;
    }

    // Helper method to get player's data component
    protected PlayerDataComponent getPlayerData() {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
            return ComponentAccessor.getPlayerData(player); // Use the accessor
        }
        return null;
    }
}