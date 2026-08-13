package de.chunkloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.List;

public class ChunkloaderCommand {

    private static final SuggestionProvider<CommandSourceStack> CHUNKLOADER_NAME_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining();
        if (input == null || input.isEmpty()) {
            return builder.buildFuture();
        }

        String inputLower = input.toLowerCase();
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            List<ChunkloaderTarget> entries = manager.getActiveChunkloaderEntries();
            for (ChunkloaderTarget entry : entries) {
                if (entry != null && entry.name() != null) {
                    String name = entry.name();
                    if (name.toLowerCase().startsWith(inputLower)) {
                        builder.suggest(name);
                    }
                }
            }
        }
        return builder.buildFuture();
    };

    private static String getEntityTypeName(boolean allowMobSpawning) {
        return "Player";
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))

            .then(Commands.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("disable")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobspawning")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(Commands.literal("setmobtarget")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(Commands.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("restore")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(Commands.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(Commands.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));

        var fpNode = Commands.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))

            .then(Commands.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("disable")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobspawning")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(Commands.literal("setmobtarget")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(Commands.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("restore")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(Commands.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(Commands.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions))));

        dispatcher.register(fpNode);
    }

    private static int addFakePlayer(CommandContext<CommandSourceStack> context) {
        try {
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            if (context.getSource().getPlayer() == null) {
                context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
                return 0;
            }

            var player = context.getSource().getPlayer();
            BlockPos playerPos = player.blockPosition();
            var world = (net.minecraft.server.level.ServerLevel) player.level();

            var config = ChunkloaderMod.getConfig();
            if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                context.getSource().sendFailure(Component.literal("Maximum player limit (" + config.getMaxChunkloaders() + ") reached!"));
                return 0;
            }

            if (world == null) {
                context.getSource().sendFailure(Component.literal("Level is not available"));
                return 0;
            }

            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            String dimension = world.dimension().identifier().toString();
            String overlappingName = ChunkloaderMod.getChunkloaderManager().getOverlappingChunkloaderName(
                chunkX, chunkZ, 0, dimension, null);

            if (overlappingName != null) {
                ChunkloaderTarget overlappingEntry = config != null ? config.getEntryByName(overlappingName) : null;
                String errorMsg;
                if (overlappingEntry != null && overlappingEntry.allowMobSpawning()) {
                    errorMsg = "Cannot add player: Position is already covered by a player '" + overlappingName + "'";
                } else {
                    errorMsg = "Cannot add player: Position is already covered by a player '" + overlappingName + "'";
                }
                context.getSource().sendFailure(Component.literal(errorMsg).withStyle(ChatFormatting.RED));
                return 0;
            }

            String playerName = player.getName().getString();
            float playerYaw = player.getYRot();
            boolean success = ChunkloaderMod.getChunkloaderManager().addChunkloader(
                chunkX, chunkZ, playerPos, null, world, playerName, playerYaw);

            if (success) {
                var entries = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();

                ChunkloaderTarget entry = entries.stream()
                    .filter(e -> e != null && e.blockX() == playerPos.getX() &&
                        e.blockY() == playerPos.getY() && e.blockZ() == playerPos.getZ())
                    .findFirst()
                    .orElse(null);

                if (entry != null && entry.easterEggSkinIndex() == null) {
                    String name = entry.name() != null ? entry.name() : "Unnamed";
                    context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' created at position (" +
                        playerPos.getX() + ", " + playerPos.getY() + ", " + playerPos.getZ() + ")").withStyle(ChatFormatting.GREEN), true);
                }
                return 1;
            } else {
                String errorMsg = "Failed to create player";
                if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                    errorMsg = "Maximum player limit (" + config.getMaxChunkloaders() + ") reached!";
                } else if (config != null && config.hasEntry(chunkX, chunkZ, dimension)) {
                    errorMsg = "A player already exists at this position!";
                }
                context.getSource().sendFailure(Component.literal(errorMsg).withStyle(ChatFormatting.RED));
                return 0;
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in addFakePlayer command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int removeFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().removeChunkloaderByName(name);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' removed").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
    }

    private static int listFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var fakePlayers = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();

        if (fakePlayers.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No active player"), false);
        } else {
            int count = fakePlayers.size();
            String noun = (count == 1 ? "player" : "players");
            context.getSource().sendSuccess(() -> Component.literal("Active " + noun + " (" + count + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendSuccess(() -> Component.literal("  - " + name + " @ Block (" +
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
            }
        }

        return fakePlayers.size();
    }

    private static int infoFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Player Info ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Name: " + entry.name()).withStyle(ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .withStyle(entry.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Player Mode (Mob Spawning)" : "Player Mode (Chunks Only)";
        ChatFormatting modeColor = entry.allowMobSpawning() ? ChatFormatting.GREEN : ChatFormatting.BLUE;
        context.getSource().sendSuccess(() -> Component.literal("Mode: " + mode).withStyle(modeColor), false);
        context.getSource().sendSuccess(() -> Component.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        int chunksLoaded = (entry.chunkRadius() * 2 + 1) * (entry.chunkRadius() * 2 + 1);
        context.getSource().sendSuccess(() -> Component.literal("Chunk radius: " + entry.chunkRadius() + " (loads " + chunksLoaded + " chunks)"), false);

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("Config reloaded").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int toggleFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget existingEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (existingEntry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean newEnabled = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);

        ChunkloaderTarget updatedEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(existingEntry);

        String entityType = getEntityTypeName(updatedEntry != null ? updatedEntry.allowMobSpawning() : true);

        if (newEnabled) {
            context.getSource().sendSuccess(() -> Component.literal(entityType + " '" + name + "' enabled").withStyle(ChatFormatting.GREEN), true);
        } else {
            String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
            context.getSource().sendSuccess(() -> Component.literal(entityType + " '" + name + "' disabled (Press " + keyName + " to open disabled list)").withStyle(ChatFormatting.RED), true);
        }

        return 1;
    }

    private static int statsFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderMod.getChunkloaderManager().getPerformanceStats();

        context.getSource().sendSuccess(() -> Component.literal("=== Player Statistics ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Total: " + stats.total()).withStyle(ChatFormatting.AQUA), false);
        context.getSource().sendSuccess(() -> Component.literal("Active: " + stats.enabled()).withStyle(ChatFormatting.GREEN), false);
        context.getSource().sendSuccess(() -> Component.literal("Inactive: " + stats.disabled()).withStyle(ChatFormatting.RED), false);
        context.getSource().sendSuccess(() -> Component.literal("Loaded chunks: " + stats.loadedChunks()), false);
        int activePlayers = stats.activeFakePlayers();
        String noun = (activePlayers == 1 ? "player" : "players");
        context.getSource().sendSuccess(() -> Component.literal("Active " + noun + ": " + activePlayers), false);

        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        ChatFormatting memoryColor = perfStats.memoryUsagePercent() > 80 ? ChatFormatting.RED : perfStats.memoryUsagePercent() > 60 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

        context.getSource().sendSuccess(() -> Component.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .withStyle(memoryColor), false);

        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Warning: High memory usage!").withStyle(ChatFormatting.RED), false);
        }

        if (stats.loadedChunks() > 1000) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Note: Many loaded chunks may impact performance").withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    private static int setNameVisible(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Name visibility for '" + name + "' set to " + (visible ? "visible" : "invisible"))
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting name visibility").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendFailure(Component.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderRadius(name, radius);

        if (success) {
            int chunksLoaded = (radius * 2 + 1) * (radius * 2 + 1);
            context.getSource().sendSuccess(() -> Component.literal("Chunk radius for '" + name + "' set to " + radius + " (loads " + chunksLoaded + " chunks)")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting chunk radius").withStyle(ChatFormatting.RED));
            return 0;
        }
    }


    private static int setMobTarget(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        if (!entry.allowMobSpawning()) {
            context.getSource().sendFailure(Component.literal("Mob target is only available for Fakeplayers").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderMobTarget(name, enabled);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Mob target for '" + name + "' set to " + (enabled ? "enabled" : "disabled"))
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting mob target").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    private static int setMobSpawning(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean allow = BoolArgumentType.getBool(context, "allow");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);

        if (success) {
            String mode = allow ? "Player Mode (Mob spawning enabled)" : "Player Mode (chunks only, no mob spawning)";
            context.getSource().sendSuccess(() -> Component.literal("Mob spawning for '" + name + "' set to " + allow + " (" + mode + ")")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Toggle failed: rename the player first to avoid a name conflict.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int toggleMobSpawning(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean newValue = !entry.allowMobSpawning();
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, newValue);

        if (success) {
            String mode = newValue ? "Fakeplayer (Mob spawning enabled)" : "Chunkplayer (chunks only, no mob spawning)";
            ChatFormatting color = newValue ? ChatFormatting.GREEN : ChatFormatting.BLUE;
            context.getSource().sendSuccess(() -> Component.literal("Toggled '" + name + "' to " + mode)
                .withStyle(color), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Toggle failed: rename the player first to avoid a name conflict.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setTabListVisibleAll(CommandContext<CommandSourceStack> context) {
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager().isTabListVisibleAll() == visible) {
            context.getSource().sendSuccess(
                () -> Component.literal("Tab list visibility is already set to " + visible).withStyle(ChatFormatting.YELLOW),
                false
            );
            return 1;
        }

        int changed = ChunkloaderMod.getChunkloaderManager().setTabListVisibleAll(visible);
        if (changed > 0) {
            context.getSource().sendSuccess(
                () -> Component.literal("Tab list visibility set to " + visible + " for " + changed + " players")
                    .withStyle(ChatFormatting.GREEN),
                true
            );
            return 1;
        }
        context.getSource().sendFailure(Component.literal("No players found").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int renameChunkloader(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        final String newName = StringArgumentType.getString(context, "newName");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        var server = context.getSource().getServer();
        boolean isRealPlayerName = false;
        for (var world : server.getAllLevels()) {
            for (var player : world.players()) {
                if (!(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) &&
                    newName.equalsIgnoreCase(player.getName().getString())) {
                    isRealPlayerName = true;
                    break;
                }
            }
            if (isRealPlayerName) break;
        }

        if (isRealPlayerName) {
            context.getSource().sendFailure(Component.literal("Cannot rename to '" + newName + "'. This name is already used by a real player.").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().renameChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Renamed '" + name + "' to '" + newName + "'")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to rename player. Name may already be in use or invalid.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int restoreFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        ChunkloaderTarget entry = config != null ? config.getEntryByName(name) : null;
        if (entry == null) {
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        if (entry.enabled()) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' is already enabled")
                .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        boolean restored = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);
        if (restored) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' restored")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("Failed to restore player '" + name + "'")
            .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int enableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        int count = ChunkloaderMod.getChunkloaderManager().enableAllChunkloaders();
        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.literal("No disabled player found").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        var entries = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();

        long fakeplayerCount = entries.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCount = entries.size() - fakeplayerCount;

        String message;
        String restoredLabel = (count == 1 ? " Player restored" : " Players restored");
        if (fakeplayerCount > 0 && chunkloaderCount > 0) {
            message = count + restoredLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkloaderCount + ")";
        } else {
            message = count + restoredLabel;
        }

        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int disableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var entriesBefore = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
        long fakeplayerCountBefore = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCountBefore = entriesBefore.size() - fakeplayerCountBefore;

        int count = ChunkloaderMod.getChunkloaderManager().disableAllChunkloaders();

        String message;
        String disabledLabel = (count == 1 ? " Player disabled" : " Players disabled");
        String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
        if (fakeplayerCountBefore > 0 && chunkloaderCountBefore > 0) {
            message = count + disabledLabel + " (mob spawning: " + fakeplayerCountBefore + ", chunks only: " + chunkloaderCountBefore + ") (Press " + keyName + " to open disabled list)";
        } else {
            message = count + disabledLabel + " (Press " + keyName + " to open disabled list)";
        }

        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), true);
        return count;
    }

    private static int removeAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().clearAllChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " Player removed" : " Players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllEnabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().removeAllEnabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " enabled player removed" : " enabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No enabled player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllDisabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> !e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().removeAllDisabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " disabled player removed" : " disabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No disabled player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int toggleVisualization(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equals(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualizationActive(key);

            context.getSource().sendSuccess(() -> Component.literal("Chunk border visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled"))
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int toggleVisualization3D(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key =
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);

            context.getSource().sendSuccess(() -> Component.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (full height: -64 to 320)")
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3D command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int toggleVisualization3DWithHeight(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            int minY = IntegerArgumentType.getInteger(context, "minY");
            int maxY = IntegerArgumentType.getInteger(context, "maxY");

            if (minY >= maxY) {
                context.getSource().sendFailure(Component.literal("minY must be less than maxY").withStyle(ChatFormatting.RED));
                return 0;
            }

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key =
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key, minY, maxY);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);

            context.getSource().sendSuccess(() -> Component.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (height: " + minY + " to " + maxY + ")")
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3DWithHeight command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int grantAllPermissions(CommandContext<CommandSourceStack> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendFailure(Component.literal("Permission system is not initialized").withStyle(ChatFormatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerList().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found").withStyle(ChatFormatting.RED));
                return 0;
            }

            permissionConfig.grantPermission(player.getUUID(), "chunkloader.*");
            server.getCommands().sendCommands(player);
            context.getSource().sendSuccess(() -> Component.literal("Granted all player permissions to " + playerName)
                .withStyle(ChatFormatting.GREEN), true);

            player.sendSystemMessage(Component.literal("You have been granted all player permissions!")
                .withStyle(ChatFormatting.GREEN));

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in grantAllPermissions command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int revokeAllPermissions(CommandContext<CommandSourceStack> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendFailure(Component.literal("Permission system is not initialized").withStyle(ChatFormatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerList().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found").withStyle(ChatFormatting.RED));
                return 0;
            }

            permissionConfig.clearPlayerPermissions(player.getUUID());
            server.getCommands().sendCommands(player);
            context.getSource().sendSuccess(() -> Component.literal("Revoked all player permissions from " + playerName)
                .withStyle(ChatFormatting.GREEN), true);

            player.sendSystemMessage(Component.literal("Your player permissions have been revoked.")
                .withStyle(ChatFormatting.RED));

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in revokeAllPermissions command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}
