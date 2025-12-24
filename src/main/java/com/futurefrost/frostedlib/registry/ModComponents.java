package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.EntityDataComponentImpl;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponentImpl;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public class ModComponents implements EntityComponentInitializer {
    // Player data component
    public static final ComponentKey<PlayerDataComponent> PLAYER_DATA =
            ComponentRegistry.getOrCreate(
                    Identifier.of(FrostedLib.MOD_ID, "player_data"),
                    PlayerDataComponent.class
            );

    // Entity data component (for all entities)
    public static final ComponentKey<EntityDataComponent> ENTITY_DATA =
            ComponentRegistry.getOrCreate(
                    Identifier.of(FrostedLib.MOD_ID, "entity_data"),
                    EntityDataComponent.class
            );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // Register player data for players
        registry.registerForPlayers(
                PLAYER_DATA,
                player -> new PlayerDataComponentImpl(),
                RespawnCopyStrategy.ALWAYS_COPY
        );

        // Register entity data for ALL entities
        registry.registerFor(Entity.class, ENTITY_DATA, entity -> new EntityDataComponentImpl());
    }

    public static void init() {
        // Initialization happens via EntityComponentInitializer interface
    }
}