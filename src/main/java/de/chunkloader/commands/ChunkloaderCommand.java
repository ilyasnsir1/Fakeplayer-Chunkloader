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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class ChunkloaderCommand {

    private static final SuggestionProvider<ServerCommandSource> CHUNKLOADER_NAME_SUGGESTIONS = (context, builder) -> {
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

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("disable")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobspawning")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(CommandManager.literal("setmobtarget")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(CommandManager.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(CommandManager.literal("rename")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(CommandManager.literal("restore")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(CommandManager.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(CommandManager.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));

        var fpNode = CommandManager.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("disable")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobspawning")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(CommandManager.literal("setmobtarget")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(CommandManager.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(CommandManager.literal("rename")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .then(CommandManager.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(CommandManager.literal("restore")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(CommandManager.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(CommandManager.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(CHUNKLOADER_NAME_SUGGESTIONS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions))));

        dispatcher.register(fpNode);
    }

    private static int addFakePlayer(CommandContext<ServerCommandSource> context) {
        try {
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            if (context.getSource().getPlayer() == null) {
                context.getSource().sendError(Text.literal("This command can only be executed by a player"));
                return 0;
            }

            var player = context.getSource().getPlayer();
            BlockPos playerPos = player.getBlockPos();
            var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();

            var config = ChunkloaderMod.getConfig();
            if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                context.getSource().sendError(Text.literal("Maximum player limit (" + config.getMaxChunkloaders() + ") reached!"));
                return 0;
            }

            if (world == null) {
                context.getSource().sendError(Text.literal("World is not available"));
                return 0;
            }

            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            String dimension = world.getRegistryKey().getValue().toString();
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
                context.getSource().sendError(Text.literal(errorMsg).formatted(Formatting.RED));
                return 0;
            }

            String playerName = player.getName().getString();
            float playerYaw = MathHelper.wrapDegrees(player.getYaw());
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
                    context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' created at position (" +
                        playerPos.getX() + ", " + playerPos.getY() + ", " + playerPos.getZ() + ")").formatted(Formatting.GREEN), true);
                }
                return 1;
            } else {
                String errorMsg = "Failed to create player";
                if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                    errorMsg = "Maximum player limit (" + config.getMaxChunkloaders() + ") reached!";
                } else if (config != null && config.hasEntry(chunkX, chunkZ, dimension)) {
                    errorMsg = "A player already exists at this position!";
                }
                context.getSource().sendError(Text.literal(errorMsg).formatted(Formatting.RED));
                return 0;
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in addFakePlayer command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int removeFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().removeChunkloaderByName(name);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' removed").formatted(Formatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
    }

    private static int listFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var fakePlayers = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();

        if (fakePlayers.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No active player"), false);
        } else {
            int count = fakePlayers.size();
            String noun = (count == 1 ? "player" : "players");
            context.getSource().sendFeedback(() -> Text.literal("Active " + noun + " (" + count + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendFeedback(() -> Text.literal("  - " + name + " @ Block (" +
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
            }
        }

        return fakePlayers.size();
    }

    private static int infoFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("=== Player Info ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Name: " + entry.name()).formatted(Formatting.YELLOW), false);
        context.getSource().sendFeedback(() -> Text.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .formatted(entry.enabled() ? Formatting.GREEN : Formatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Player Mode (Mob Spawning)" : "Player Mode (Chunks Only)";
        Formatting modeColor = entry.allowMobSpawning() ? Formatting.GREEN : Formatting.BLUE;
        context.getSource().sendFeedback(() -> Text.literal("Mode: " + mode).formatted(modeColor), false);
        context.getSource().sendFeedback(() -> Text.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        int chunksLoaded = (entry.chunkRadius() * 2 + 1) * (entry.chunkRadius() * 2 + 1);
        context.getSource().sendFeedback(() -> Text.literal("Chunk radius: " + entry.chunkRadius() + " (loads " + chunksLoaded + " chunks)"), false);

        return 1;
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendFeedback(() -> Text.literal("Config reloaded").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int toggleFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget existingEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (existingEntry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean newEnabled = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);

        ChunkloaderTarget updatedEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(existingEntry);

        String entityType = getEntityTypeName(updatedEntry != null ? updatedEntry.allowMobSpawning() : true);

        if (newEnabled) {
            context.getSource().sendFeedback(() -> Text.literal(entityType + " '" + name + "' enabled").formatted(Formatting.GREEN), true);
        } else {
            String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
            context.getSource().sendFeedback(() -> Text.literal(entityType + " '" + name + "' disabled (Press " + keyName + " to open disabled list)").formatted(Formatting.RED), true);
        }

        return 1;
    }

    private static int statsFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderMod.getChunkloaderManager().getPerformanceStats();

        context.getSource().sendFeedback(() -> Text.literal("=== Player Statistics ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Total: " + stats.total()).formatted(Formatting.AQUA), false);
        context.getSource().sendFeedback(() -> Text.literal("Active: " + stats.enabled()).formatted(Formatting.GREEN), false);
        context.getSource().sendFeedback(() -> Text.literal("Inactive: " + stats.disabled()).formatted(Formatting.RED), false);
        context.getSource().sendFeedback(() -> Text.literal("Loaded chunks: " + stats.loadedChunks()), false);
        int activePlayers = stats.activeFakePlayers();
        String noun = (activePlayers == 1 ? "player" : "players");
        context.getSource().sendFeedback(() -> Text.literal("Active " + noun + ": " + activePlayers), false);

        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        Formatting memoryColor = perfStats.memoryUsagePercent() > 80 ? Formatting.RED : perfStats.memoryUsagePercent() > 60 ? Formatting.YELLOW : Formatting.GREEN;

        context.getSource().sendFeedback(() -> Text.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .formatted(memoryColor), false);

        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Warning: High memory usage!").formatted(Formatting.RED), false);
        }

        if (stats.loadedChunks() > 1000) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Note: Many loaded chunks may impact performance").formatted(Formatting.YELLOW), false);
        }

        return 1;
    }

    private static int setNameVisible(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Name visibility for '" + name + "' set to " + (visible ? "visible" : "invisible"))
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting name visibility").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setRadius(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendError(Text.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").formatted(Formatting.RED));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderRadius(name, radius);

        if (success) {
            int chunksLoaded = (radius * 2 + 1) * (radius * 2 + 1);
            context.getSource().sendFeedback(() -> Text.literal("Chunk radius for '" + name + "' set to " + radius + " (loads " + chunksLoaded + " chunks)")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting chunk radius").formatted(Formatting.RED));
            return 0;
        }
    }


    private static int setMobTarget(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        if (!entry.allowMobSpawning()) {
            context.getSource().sendError(Text.literal("Mob target is only available for Fakeplayers").formatted(Formatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderMobTarget(name, enabled);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Mob target for '" + name + "' set to " + (enabled ? "enabled" : "disabled"))
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting mob target").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setMobSpawning(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean allow = BoolArgumentType.getBool(context, "allow");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);

        if (success) {
            String mode = allow ? "Player Mode (Mob spawning enabled)" : "Player Mode (chunks only, no mob spawning)";
            context.getSource().sendFeedback(() -> Text.literal("Mob spawning for '" + name + "' set to " + allow + " (" + mode + ")")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Toggle failed: rename the player first to avoid a name conflict.").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int toggleMobSpawning(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean newValue = !entry.allowMobSpawning();
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, newValue);

        if (success) {
            String mode = newValue ? "Fakeplayer (Mob spawning enabled)" : "Chunkplayer (chunks only, no mob spawning)";
            Formatting color = newValue ? Formatting.GREEN : Formatting.BLUE;
            context.getSource().sendFeedback(() -> Text.literal("Toggled '" + name + "' to " + mode)
                .formatted(color), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Toggle failed: rename the player first to avoid a name conflict.").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setTabListVisibleAll(CommandContext<ServerCommandSource> context) {
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager().isTabListVisibleAll() == visible) {
            context.getSource().sendFeedback(
                () -> Text.literal("Tab list visibility is already set to " + visible).formatted(Formatting.YELLOW),
                false
            );
            return 1;
        }

        int changed = ChunkloaderMod.getChunkloaderManager().setTabListVisibleAll(visible);
        if (changed > 0) {
            context.getSource().sendFeedback(
                () -> Text.literal("Tab list visibility set to " + visible + " for " + changed + " players")
                    .formatted(Formatting.GREEN),
                true
            );
            return 1;
        }
        context.getSource().sendError(Text.literal("No players found").formatted(Formatting.RED));
        return 0;
    }

    private static int renameChunkloader(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        final String newName = StringArgumentType.getString(context, "newName");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        var server = context.getSource().getServer();
        boolean isRealPlayerName = false;
        for (var world : server.getWorlds()) {
            for (var player : world.getPlayers()) {
                if (!(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) &&
                    newName.equalsIgnoreCase(player.getName().getString())) {
                    isRealPlayerName = true;
                    break;
                }
            }
            if (isRealPlayerName) break;
        }

        if (isRealPlayerName) {
            context.getSource().sendError(Text.literal("Cannot rename to '" + newName + "'. This name is already used by a real player.").formatted(Formatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().renameChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Renamed '" + name + "' to '" + newName + "'")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Failed to rename player. Name may already be in use or invalid.").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int restoreFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        ChunkloaderTarget entry = config != null ? config.getEntryByName(name) : null;
        if (entry == null) {
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        if (entry.enabled()) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' is already enabled")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }

        boolean restored = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);
        if (restored) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' restored")
                .formatted(Formatting.GREEN), true);
            return 1;
        }

        context.getSource().sendError(Text.literal("Failed to restore player '" + name + "'")
            .formatted(Formatting.RED));
        return 0;
    }

    private static int enableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        int count = ChunkloaderMod.getChunkloaderManager().enableAllChunkloaders();
        if (count == 0) {
            context.getSource().sendFeedback(() -> Text.literal("No disabled player found").formatted(Formatting.YELLOW), false);
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

        context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        return count;
    }

    private static int disableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
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

        context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.YELLOW), true);
        return count;
    }

    private static int removeAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
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
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllEnabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
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
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No enabled player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllDisabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
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
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No disabled player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int toggleVisualization(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equals(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualizationActive(key);

            context.getSource().sendFeedback(() -> Text.literal("Chunk border visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled"))
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int toggleVisualization3D(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key =
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);

            context.getSource().sendFeedback(() -> Text.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (full height: -64 to 320)")
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3D command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int toggleVisualization3DWithHeight(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            int minY = IntegerArgumentType.getInteger(context, "minY");
            int maxY = IntegerArgumentType.getInteger(context, "maxY");

            if (minY >= maxY) {
                context.getSource().sendError(Text.literal("minY must be less than maxY").formatted(Formatting.RED));
                return 0;
            }

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);

            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }

            de.chunkloader.manager.ChunkloaderManager.ChunkKey key =
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());

            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }

            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key, minY, maxY);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);

            context.getSource().sendFeedback(() -> Text.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (height: " + minY + " to " + maxY + ")")
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3DWithHeight command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int grantAllPermissions(CommandContext<ServerCommandSource> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendError(Text.literal("Permission system is not initialized").formatted(Formatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerManager().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendError(Text.literal("Player '" + playerName + "' not found").formatted(Formatting.RED));
                return 0;
            }

            permissionConfig.grantPermission(player.getUuid(), "chunkloader.*");
            server.getCommandManager().sendCommandTree(player);
            context.getSource().sendFeedback(() -> Text.literal("Granted all player permissions to " + playerName)
                .formatted(Formatting.GREEN), true);

            player.sendMessage(Text.literal("You have been granted all player permissions!")
                .formatted(Formatting.GREEN), false);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in grantAllPermissions command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int revokeAllPermissions(CommandContext<ServerCommandSource> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendError(Text.literal("Permission system is not initialized").formatted(Formatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerManager().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendError(Text.literal("Player '" + playerName + "' not found").formatted(Formatting.RED));
                return 0;
            }

            permissionConfig.clearPlayerPermissions(player.getUuid());
            server.getCommandManager().sendCommandTree(player);
            context.getSource().sendFeedback(() -> Text.literal("Revoked all player permissions from " + playerName)
                .formatted(Formatting.GREEN), true);

            player.sendMessage(Text.literal("Your player permissions have been revoked.")
                .formatted(Formatting.RED), false);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in revokeAllPermissions command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
}

