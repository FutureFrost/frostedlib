package com.futurefrost.frostedlib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import com.futurefrost.frostedlib.util.TeleportHelper;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class FrostedCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("frostedlib")
                .requires(source -> source.hasPermissionLevel(2))

                // Save command: /frostedlib save <target> <id>
                .then(CommandManager.literal("save")
                        .then(CommandManager.argument("target", EntityArgumentType.entities())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(FrostedCommands::savePosition)
                                )
                        )
                )

                // Return command: /frostedlib return <target> <id>
                .then(CommandManager.literal("return")
                        .then(CommandManager.argument("target", EntityArgumentType.entities())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(FrostedCommands::returnToPosition)
                                )
                        )
                )

                // List command: /frostedlib list <target>
                .then(CommandManager.literal("list")
                        .then(CommandManager.argument("target", EntityArgumentType.entity())
                                .executes(FrostedCommands::listPositions)
                        )
                        .executes(context -> listPositionsForExecutor(context))
                )

                // Clear command: /frostedlib clear <target>
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("target", EntityArgumentType.entity())
                                .executes(FrostedCommands::clearPositions)
                        )
                        .executes(context -> clearPositionsForExecutor(context))
                )

                // Debug command: /frostedlib debug <target>
                .then(CommandManager.literal("debug")
                        .then(CommandManager.argument("target", EntityArgumentType.entity())
                                .executes(FrostedCommands::debugData)
                        )
                        .executes(context -> debugDataForExecutor(context))
                )
        );
    }

    private static int savePosition(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Collection<? extends Entity> targets;
        try {
            targets = EntityArgumentType.getEntities(context, "target");
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
        String id = StringArgumentType.getString(context, "id");

        int successCount = 0;

        for (Entity entity : targets) {
            PositionData pos = PositionData.fromEntity(entity);

            // Use appropriate component based on entity type
            if (entity instanceof ServerPlayerEntity player) {
                PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
                data.savePosition(id, pos);
                successCount++;
            } else {
                EntityDataComponent data = ModComponents.ENTITY_DATA.get(entity);
                data.savePosition(id, pos);
                successCount++;
            }
        }

        if (successCount > 0) {
            int finalSuccessCount = successCount;
            int finalSuccessCount1 = successCount;
            source.sendFeedback(() ->
                            Text.literal("Saved position '" + id + "' for " + finalSuccessCount + " entity" + (finalSuccessCount1 == 1 ? "" : "s")),
                    true
            );
            return successCount;
        } else {
            source.sendError(Text.literal("Failed to save position for any entities"));
            return 0;
        }
    }

    private static int returnToPosition(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "target");
        String id = StringArgumentType.getString(context, "id");

        int successCount = 0;
        int failCount = 0;

        for (Entity entity : targets) {
            boolean success = false;

            // Try to get saved position
            Optional<PositionData> optionalPos = Optional.empty();

            if (entity instanceof ServerPlayerEntity player) {
                PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
                optionalPos = data.getPosition(id);
            } else {
                EntityDataComponent data = ModComponents.ENTITY_DATA.get(entity);
                optionalPos = data.getPosition(id);
            }

            if (optionalPos.isPresent()) {
                // Teleport to saved position
                PositionData pos = optionalPos.get();
                MinecraftServer server = entity.getServer();

                if (server != null) {
                    ServerWorld targetWorld = server.getWorld(pos.dimension());
                    if (targetWorld != null) {
                        if (entity instanceof ServerPlayerEntity player) {
                            success = TeleportHelper.teleportPlayer(
                                    player,
                                    targetWorld,
                                    pos.toVec3d(),
                                    pos.yaw(),
                                    pos.pitch()
                            );
                        } else {
                            // Teleport non-player entity
                            entity.teleport(
                                    targetWorld,
                                    pos.x(),
                                    pos.y(),
                                    pos.z(),
                                    java.util.Set.of(),
                                    pos.yaw(),
                                    pos.pitch()
                            );
                            success = true;
                        }
                    }
                }
            } else {
                // For players only: execute failsafe (bed/respawn anchor logic)
                if (entity instanceof ServerPlayerEntity player) {
                    success = teleportToSpawnFailsafe(player);
                } else {
                    // For non-players: teleport to world spawn
                    success = teleportToWorldSpawn(entity);
                }
            }

            if (success) {
                successCount++;
            } else {
                failCount++;
            }
        }

        if (successCount > 0) {
            int finalSuccessCount = successCount;
            int finalSuccessCount1 = successCount;
            int finalFailCount = failCount;
            source.sendFeedback(() ->
                            Text.literal("Teleported " + finalSuccessCount + " entity" + (finalSuccessCount1 == 1 ? "" : "s") +
                                    " to position '" + id + "'" + (finalFailCount > 0 ? " (" + finalFailCount + " failed)" : "")),
                    true
            );
            return successCount;
        } else {
            source.sendError(Text.literal("Failed to teleport any entities"));
            return 0;
        }
    }

    private static boolean teleportToSpawnFailsafe(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        // Your existing failsafe logic here (bed/respawn anchor check)
        RegistryKey<World> spawnDimension = player.getSpawnPointDimension();
        BlockPos spawnBlockPos = player.getSpawnPointPosition();

        boolean hasValidSpawnPoint = false;
        ServerWorld targetWorld = null;
        Vec3d spawnLocation = null;

        if (spawnBlockPos != null && spawnDimension != null) {
            targetWorld = server.getWorld(spawnDimension);

            if (targetWorld != null) {
                if (spawnDimension == World.OVERWORLD) {
                    BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                    boolean isBed = blockState.getBlock() instanceof BedBlock;
                    boolean isWorldSpawn = spawnBlockPos.equals(targetWorld.getSpawnPos());
                    hasValidSpawnPoint = isBed && !isWorldSpawn;
                } else if (spawnDimension == World.NETHER) {
                    BlockState blockState = targetWorld.getBlockState(spawnBlockPos);
                    boolean isRespawnAnchor = blockState.getBlock() instanceof RespawnAnchorBlock;
                    boolean isCharged = isRespawnAnchor && blockState.get(RespawnAnchorBlock.CHARGES) > 0;
                    hasValidSpawnPoint = isRespawnAnchor && isCharged;
                } else {
                    hasValidSpawnPoint = true;
                }
            }
        }

        if (!hasValidSpawnPoint || targetWorld == null) {
            targetWorld = server.getOverworld();
            spawnBlockPos = targetWorld.getSpawnPos();
        }

        if (hasValidSpawnPoint && spawnBlockPos != null) {
            Optional<Vec3d> safeSpawn = PlayerEntity.findRespawnPosition(
                    targetWorld,
                    spawnBlockPos,
                    player.getSpawnAngle(),
                    false,
                    true
            );

            if (safeSpawn.isPresent()) {
                spawnLocation = safeSpawn.get();
            } else {
                targetWorld = server.getOverworld();
                spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
            }
        } else {
            spawnLocation = Vec3d.ofBottomCenter(targetWorld.getSpawnPos());
        }

        return TeleportHelper.teleportPlayer(
                player,
                targetWorld,
                spawnLocation,
                player.getSpawnAngle(),
                0.0f
        );
    }

    private static boolean teleportToWorldSpawn(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) return false;

        ServerWorld overworld = server.getOverworld();
        Vec3d spawnPos = Vec3d.ofBottomCenter(overworld.getSpawnPos());

        entity.teleport(
                overworld,
                spawnPos.x,
                spawnPos.y,
                spawnPos.z,
                java.util.Set.of(),
                entity.getYaw(),
                entity.getPitch()
        );
        return true;
    }

    private static int listPositions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Entity target = EntityArgumentType.getEntity(context, "target");

        Map<String, PositionData> positions;
        String entityName = target.getName().getString();

        if (target instanceof ServerPlayerEntity player) {
            PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
            positions = data.getAllPositions();
        } else {
            EntityDataComponent data = ModComponents.ENTITY_DATA.get(target);
            positions = data.getAllPositions();
        }

        if (positions.isEmpty()) {
            source.sendFeedback(() ->
                            Text.literal(entityName + " has no saved positions"),
                    false
            );
        } else {
            source.sendFeedback(() ->
                            Text.literal("=== " + entityName + "'s Saved Positions (" + positions.size() + ") ==="),
                    false
            );
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

    private static int listPositionsForExecutor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (source.getEntity() == null) {
            source.sendError(Text.literal("You must specify a target or be an entity"));
            return 0;
        }
        return listPositions(context);
    }

    private static int clearPositions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Entity target = EntityArgumentType.getEntity(context, "target");

        int count;
        String entityName = target.getName().getString();

        if (target instanceof ServerPlayerEntity player) {
            PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
            count = data.getAllPositions().size();
            data.clearAllPositions();
        } else {
            EntityDataComponent data = ModComponents.ENTITY_DATA.get(target);
            count = data.getAllPositions().size();
            data.clearAllPositions();
        }

        source.sendFeedback(() ->
                        Text.literal("Cleared " + count + " saved position" + (count == 1 ? "" : "s") +
                                " from " + entityName),
                true
        );

        return count;
    }

    private static int clearPositionsForExecutor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (source.getEntity() == null) {
            source.sendError(Text.literal("You must specify a target or be an entity"));
            return 0;
        }
        return clearPositions(context);
    }

    private static int debugData(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Entity target = EntityArgumentType.getEntity(context, "target");

        // Simplified debug for now
        source.sendFeedback(() ->
                        Text.literal("Debug info for " + target.getName().getString() +
                                " (UUID: " + target.getUuidAsString() + ")"),
                false
        );

        return 1;
    }

    private static int debugDataForExecutor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (source.getEntity() == null) {
            source.sendError(Text.literal("You must specify a target or be an entity"));
            return 0;
        }
        return debugData(context);
    }
}