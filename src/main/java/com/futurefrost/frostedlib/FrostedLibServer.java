package com.futurefrost.frostedlib;

import com.futurefrost.frostedlib.command.FrostedCommands;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class FrostedLibServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FrostedLib.LOGGER.info("FrostedLib Server Initializer Running!");

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FrostedLib.LOGGER.info("Registering FrostedLib Commands on Server!...");
            FrostedCommands.register(dispatcher);
        });
    }
}