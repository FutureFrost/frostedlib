package com.futurefrost.frostedlib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponentImpl;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents; // ADD THIS IMPORT
import com.futurefrost.frostedlib.util.TeleportHelper;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;
import java.util.Optional;

public class FrostedCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("frostedlib")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("save")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(FrostedCommands::savePosition)
                        )
                )
                .then(CommandManager.literal("return")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(FrostedCommands::returnToPosition)
                        )
                )
                .then(CommandManager.literal("list")
                        .executes(FrostedCommands::listPositions)
                )
                .then(CommandManager.literal("clear")
                        .executes(FrostedCommands::clearPositions)
                )
                .then(CommandManager.literal("debug")
                        .executes(FrostedCommands::debugData)
                )
        );
    }

    private static int savePosition(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        String id = StringArgumentType.getString(context, "id");

        // FIXED: Use ModComponents.PLAYER_DATA.get() instead of PlayerDataComponent.get()
        PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);

        PositionData pos = PositionData.fromPlayer(player);
        data.savePosition(id, pos);

        source.sendFeedback(() ->
                        Text.literal("Saved position '" + id + "' at " +
                                String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ()) +
                                " in " + player.getWorld().getRegistryKey().getValue()),
                true
        );

        return 1;
    }

    private static int returnToPosition(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        String id = StringArgumentType.getString(context, "id");

        // FIXED: Use ModComponents.PLAYER_DATA.get()
        PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);

        Optional<PositionData> optionalPos = data.getPosition(id);

        if (optionalPos.isPresent()) {
            PositionData pos = optionalPos.get();

            ServerWorld targetWorld = player.getServer().getWorld(pos.dimension());
            if (targetWorld == null) {
                source.sendError(Text.literal("Target dimension '" + pos.dimension().getValue() + "' not found or not loaded"));
                return 0;
            }

            boolean success = TeleportHelper.teleportPlayer(
                    player,
                    targetWorld,
                    pos.toVec3d(),
                    pos.yaw(),
                    pos.pitch()
            );

            if (success) {
                source.sendFeedback(() ->
                                Text.literal("Teleported to position '" + id + "' at " +
                                        String.format("%.1f, %.1f, %.1f", pos.x(), pos.y(), pos.z()) +
                                        " in " + pos.dimension().getValue()),
                        true
                );
                return 1;
            } else {
                source.sendError(Text.literal("Failed to teleport to position '" + id + "'"));
                return 0;
            }
        } else {
            // Failsafe: teleport to world spawn
            ServerWorld overworld = player.getServer().getOverworld();
            Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

            boolean success = TeleportHelper.teleportPlayer(
                    player,
                    overworld,
                    spawnPos,
                    player.getYaw(),
                    player.getPitch()
            );

            if (success) {
                source.sendFeedback(() ->
                                Text.literal("Position '" + id + "' not found. Teleported to world spawn at " +
                                        String.format("%.1f, %.1f, %.1f", spawnPos.x, spawnPos.y, spawnPos.z)),
                        true
                );
                return 1;
            } else {
                source.sendError(Text.literal("Position '" + id + "' not found AND failed to teleport to spawn"));
                return 0;
            }
        }
    }

    private static int listPositions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        // FIXED: Use ModComponents.PLAYER_DATA.get()
        PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
        Map<String, PositionData> positions = data.getAllPositions();

        if (positions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No positions saved"), false);
        } else {
            source.sendFeedback(() -> Text.literal("=== Saved Positions (" + positions.size() + ") ==="), false);
            int index = 1;
            for (Map.Entry<String, PositionData> entry : positions.entrySet()) {
                PositionData pos = entry.getValue();
                int finalIndex = index;
                source.sendFeedback(() ->
                                Text.literal(finalIndex + ". '" + entry.getKey() + "'" +
                                        "\n   Position: " + String.format("%.1f, %.1f, %.1f", pos.x(), pos.y(), pos.z()) +
                                        "\n   Dimension: " + pos.dimension().getValue() +
                                        "\n   Rotation: " + String.format("%.1f° yaw, %.1f° pitch", pos.yaw(), pos.pitch())),
                        false
                );
                index++;
            }
        }

        return positions.size();
    }

    private static int clearPositions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        // FIXED: Use ModComponents.PLAYER_DATA.get()
        PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
        int count = data.getAllPositions().size();
        data.clearAllPositions();

        source.sendFeedback(() ->
                        Text.literal("Cleared " + count + " saved position" + (count == 1 ? "" : "s")),
                true
        );

        return count;
    }

    private static int debugData(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        // FIXED: Use ModComponents.PLAYER_DATA.get()
        PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);

        // Check if the data is actually our implementation
        if (data instanceof PlayerDataComponentImpl implData) {
            // Create a new NBT compound and write data to it
            NbtCompound nbt = new NbtCompound();
            implData.writeToNbt(nbt);

            // Get the saved positions list from NBT
            String rawNbt = nbt.toString();

            // Count how many positions are saved
            int positionCount = implData.getAllPositions().size();

            // Send debug information
            source.sendFeedback(() ->
                            Text.literal("=== FrostedLib Debug Information ==="),
                    false
            );

            source.sendFeedback(() ->
                            Text.literal("Player: " + player.getName().getString() +
                                    " | UUID: " + player.getUuidAsString()),
                    false
            );

            source.sendFeedback(() ->
                            Text.literal("Saved Positions: " + positionCount),
                    false
            );

            source.sendFeedback(() ->
                            Text.literal("Raw NBT Data (for debugging):"),
                    false
            );

            // Split long NBT string for readability
            if (rawNbt.length() > 100) {
                source.sendFeedback(() ->
                                Text.literal("  " + rawNbt.substring(0, 100) + "..."),
                        false
                );
                source.sendFeedback(() ->
                                Text.literal("  ..." + rawNbt.substring(rawNbt.length() - 50)),
                        false
                );
            } else {
                source.sendFeedback(() ->
                                Text.literal("  " + rawNbt),
                        false
                );
            }

            // List each position with its raw NBT
            Map<String, PositionData> positions = implData.getAllPositions();
            if (!positions.isEmpty()) {
                source.sendFeedback(() ->
                                Text.literal("Individual Position NBT:"),
                        false
                );

                int index = 1;
                for (Map.Entry<String, PositionData> entry : positions.entrySet()) {
                    NbtCompound posNbt = entry.getValue().toNbt();
                    int finalIndex = index;
                    source.sendFeedback(() ->
                                    Text.literal(finalIndex + ". '" + entry.getKey() + "': " + posNbt.toString()),
                            false
                    );
                    index++;
                }
            }

            // Check player's NBT directly
            NbtCompound playerNbt = new NbtCompound();
            player.writeNbt(playerNbt);
            boolean hasFrostedLibData = playerNbt.contains("FrostedLibData");

            source.sendFeedback(() ->
                            Text.literal("Has FrostedLibData in player NBT: " + hasFrostedLibData),
                    false
            );

            if (hasFrostedLibData) {
                NbtCompound frostedData = playerNbt.getCompound("FrostedLibData");
                source.sendFeedback(() ->
                                Text.literal("Player NBT FrostedLibData: " + frostedData.toString()),
                        false
                );
            }

            return 1;
        } else {
            source.sendError(Text.literal("Error: PlayerDataComponent is not the expected implementation"));
            return 0;
        }
    }
}