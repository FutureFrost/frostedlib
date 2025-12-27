package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.action.*;
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

        FrostedLib.LOGGER.info("fake_respawn registered!");

        FrostedLib.LOGGER.info("Registering save_pos...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "save_pos"),
                SavePositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("save_pos registered!");

        FrostedLib.LOGGER.info("Registering return_pos...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "return_pos"),
                ReturnPositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("return_pos registered!");

        FrostedLib.LOGGER.info("Registering relative_teleport...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "relative_teleport"),
                RelativeTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("relative_teleport registered!");

        FrostedLib.LOGGER.info("Registering fixed_teleport...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "fixed_teleport"),
                FixedTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("fixed_teleport registered!");

        FrostedLib.LOGGER.info("Registering biome_teleport...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "biome_teleport"),
                BiomeTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("biome_teleport registered!");

        FrostedLib.LOGGER.info("Registering structure_teleport...");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "structure_teleport"),
                StructureTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("structure_teleport registered!");

        FrostedLib.LOGGER.info("Registered FrostedLib actions");
    }
}