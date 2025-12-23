package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.registry.ModComponents;
import net.minecraft.entity.player.PlayerEntity;

public class ComponentAccessor {
    public static PlayerDataComponent getPlayerData(PlayerEntity player) {
        // Get the component and cast it to the interface
        return ModComponents.PLAYER_DATA.get(player);
    }
}