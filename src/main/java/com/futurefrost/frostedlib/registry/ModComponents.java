package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.PlayerDataComponentImpl;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

public class ModComponents implements EntityComponentInitializer {
    public static final ComponentKey<PlayerDataComponentImpl> PLAYER_DATA =
            ComponentRegistry.getOrCreate(
                    Identifier.of(FrostedLib.MOD_ID, "player_data"),
                    PlayerDataComponentImpl.class  // <-- This must be the implementation class
            );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(
                PLAYER_DATA,
                player -> new PlayerDataComponentImpl(),
                RespawnCopyStrategy.ALWAYS_COPY
        );
    }

    public static void init() {
    }
}