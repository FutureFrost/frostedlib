package com.futurefrost.frostedlib;

import com.futurefrost.frostedlib.command.FrostedCommands;
import com.futurefrost.frostedlib.registry.ModActions;
import com.futurefrost.frostedlib.registry.ModComponents;
import com.futurefrost.frostedlib.registry.ModPowers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrostedLib implements ModInitializer {
	public static final String MOD_ID = "frostedlib";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("=== FrostedLib Initialization ===");

		// Initialize components (player data storage)
		try {
			ModComponents.init();
			LOGGER.info("Components Initialized");
		} catch (Exception e) {
			LOGGER.error("Failed to Initialize Components", e);
		}

		// Register power types
		try {
			ModPowers.init();
			LOGGER.info("Power Types Registered");
		} catch (Exception e) {
			LOGGER.error("Failed to Register Power Types", e);
		}

		// Register action types
		try {
			ModActions.init();
			LOGGER.info("Action Types Registered");
		} catch (Exception e) {
			LOGGER.error("Failed to Register Action Types", e);
		}

		// Register commands HERE instead of in server initializer
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LOGGER.info("Registering FrostedLib Commands on Client!");
			FrostedCommands.register(dispatcher);
		});

		LOGGER.info("==== FrostedLib Initialized ====");
	}
}