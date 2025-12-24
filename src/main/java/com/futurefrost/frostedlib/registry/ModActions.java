package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.action.DimensionShiftAction;
import com.futurefrost.frostedlib.action.FakeRespawnAction;
import com.futurefrost.frostedlib.action.ReturnPositionAction;
import com.futurefrost.frostedlib.action.SavePositionAction;
import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModActions {

    public static void init() {

        FrostedLib.LOGGER.info("Registering fake_respawn...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "fake_respawn"),
                FakeRespawnAction.getFactory()
        );

        FrostedLib.LOGGER.info("Registering save_pos...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "save_pos"),
                SavePositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("Registering return_pos...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "return_pos"),
                ReturnPositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("Registering dimension_shift...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "dimension_shift"),
                DimensionShiftAction.getFactory()
        );

        FrostedLib.LOGGER.info("Registered FrostedLib actions");
    }
}