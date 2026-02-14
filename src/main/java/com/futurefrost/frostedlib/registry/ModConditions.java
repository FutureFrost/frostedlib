package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.condition.*;
import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModConditions {

    public static void init() {

        Registry.register(
                ApoliRegistries.ENTITY_CONDITION,
                Identifier.of(FrostedLib.MOD_ID, "has_position"),
                HasPositionCondition.getFactory()
        );

        FrostedLib.LOGGER.info("has_position condition registered");

    }
}