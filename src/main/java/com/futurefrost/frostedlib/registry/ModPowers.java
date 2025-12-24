package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.power.*;
import io.github.apace100.apoli.registry.ApoliRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModPowers {

    public static void init() {
        // Register save position power
        Registry.register(
                ApoliRegistries.POWER_FACTORY,
                Identifier.of(FrostedLib.MOD_ID, "save_pos"),
                SavePositionPower.createFactory()
        );

        // Register return position power
        Registry.register(
                ApoliRegistries.POWER_FACTORY,
                Identifier.of(FrostedLib.MOD_ID, "return_pos"),
                ReturnPositionPower.createFactory()
        );

        FrostedLib.LOGGER.info("Registered FrostedLib Power Types");
    }
}