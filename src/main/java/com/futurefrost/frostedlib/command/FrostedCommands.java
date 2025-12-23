package com.futurefrost.frostedlib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
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
import com.futurefrost.frostedlib.util.ComponentAccessor;

import java.util.Map;
import java.util.Optional;

public class FrostedCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("frostedlib")
                .requires(source -> source.hasPermissionLevel(2)) // Requires OP level 2
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
        PlayerDataComponent data = ComponentAccessor.getPlayerData(player);

        PositionData pos = PositionData.fromPlayer(player);
        data.savePosition(id, pos);

        source.sendFeedback(() ->
                        Text.literal("Saved position '" + id + "' at " +
                                String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ())),
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
        PlayerDataComponent data = ComponentAccessor.getPlayerData(player);

        Optional<PositionData> optionalPos = data.getPosition(id);

        if (optionalPos.isPresent()) {
            PositionData pos = optionalPos.get();

            ServerWorld targetWorld = player.getServer().getWorld(pos.dimension());
            if (targetWorld == null) {
                source.sendError(Text.literal("Target dimension not found"));
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
                                Text.literal("Teleported to position '" + id + "'"),
                        true
                );
                return 1;
            } else {
                source.sendError(Text.literal("Failed to teleport"));
                return 0;
            }
        } else {
            // Failsafe
            ServerWorld overworld = player.getServer().getOverworld();
            Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

            TeleportHelper.teleportPlayer(
                    player,
                    overworld,
                    spawnPos,
                    player.getYaw(),
                    player.getPitch()
            );

            source.sendFeedback(() ->
                            Text.literal("Position '" + id + "' not found. Teleported to spawn."),
                    true
            );
            return 1;
        }
    }

    private static int listPositions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player"));
            return 0;
        }

        PlayerDataComponent data = ComponentAccessor.getPlayerData(player);
        Map<String, PositionData> positions = data.getAllPositions();

        if (positions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No positions saved"), false);
        } else {
            source.sendFeedback(() -> Text.literal("Saved positions:"), false);
            for (Map.Entry<String, PositionData> entry : positions.entrySet()) {
                PositionData pos = entry.getValue();
                source.sendFeedback(() ->
                                Text.literal("  " + entry.getKey() + ": " +
                                        String.format("%.1f, %.1f, %.1f in %s",
                                                pos.x(), pos.y(), pos.z(),
                                                pos.dimension().getValue())),
                        false
                );
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

        PlayerDataComponent data = ComponentAccessor.getPlayerData(player);
        int count = data.getAllPositions().size();
        data.clearAllPositions();

        source.sendFeedback(() ->
                        Text.literal("Cleared " + count + " saved positions"),
                true
        );

        return count;
    }
}