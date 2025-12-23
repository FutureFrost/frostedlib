package com.futurefrost.frostedlib;

import com.futurefrost.frostedlib.command.FrostedCommands;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class FrostedLibServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        // Register commands on server startup
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FrostedCommands.register(dispatcher);
        });
    }
}