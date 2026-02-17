package com.futurefrost.frostedlib.registry;

import com.futurefrost.frostedlib.FrostedLib;
import com.futurefrost.frostedlib.action.*;
import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModActions {

    public static void init() {

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "fake_respawn"),
                FakeRespawnAction.getFactory()
        );

        FrostedLib.LOGGER.info("fake_respawn registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "save_pos"),
                SavePositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("save_pos registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "return_pos"),
                ReturnPositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("return_pos registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "remove_pos"),
                RemovePositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("remove_pos registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "copy_pos"),
                CopyPositionAction.getFactory()
        );

        FrostedLib.LOGGER.info("copy_pos registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "relative_teleport"),
                RelativeTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("relative_teleport registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "fixed_teleport"),
                FixedTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("fixed_teleport registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "biome_teleport"),
                BiomeTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("biome_teleport registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "structure_teleport"),
                StructureTeleportAction.getFactory()
        );

        FrostedLib.LOGGER.info("structure_teleport registered");

        Registry.register(
                ApoliRegistries.ENTITY_ACTION,
                Identifier.of(FrostedLib.MOD_ID, "action_at_pos"),
                ActionAtPosAction.getFactory()
        );

        FrostedLib.LOGGER.info("action_at_pos registered");
    }
}