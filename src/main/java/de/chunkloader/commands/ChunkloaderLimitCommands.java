package de.chunkloader.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.CompletableFuture;

final class ChunkloaderLimitCommands {
    private ChunkloaderLimitCommands() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> createNode() {
        return CommandManager.literal("limits")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderLimitCommands::showLimits)
                .then(CommandManager.literal("server")
                        .then(CommandManager.argument("limit", IntegerArgumentType.integer(0))
                                .executes(ChunkloaderLimitCommands::setServerLimit)))
                .then(CommandManager.literal("default")
                        .then(CommandManager.argument("limit", IntegerArgumentType.integer(0))
                                .suggests(ChunkloaderLimitCommands::suggestDefaultPlayerLimit)
                                .executes(ChunkloaderLimitCommands::setDefaultPlayerLimit)))
                .then(CommandManager.literal("player")
                        .then(CommandManager.argument("limitPlayer", StringArgumentType.word())
                                .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                                .then(CommandManager.literal("status")
                                        .executes(ChunkloaderLimitCommands::showPlayerLimit))
                                .then(CommandManager.argument("limit", IntegerArgumentType.integer(0))
                                        .suggests(ChunkloaderLimitCommands::suggestPlayerLimit)
                                        .executes(ChunkloaderLimitCommands::setPlayerLimit))
                                .then(CommandManager.literal("unlimited")
                                        .executes(context -> setPlayerLimit(context, ChunkloaderConfig.UNLIMITED_PLAYER_LIMIT)))
                                .then(CommandManager.literal("reset")
                                        .executes(ChunkloaderLimitCommands::clearPlayerLimit))));
    }

    private static CompletableFuture<Suggestions> suggestDefaultPlayerLimit(
            CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var config = ChunkloaderMod.getConfig();
        if (config != null) {
            suggestLimit(builder, config.getDefaultPlayerLoaderLimit(), "Default per player (0 = unlimited)");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerLimit(
            CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var config = ChunkloaderMod.getConfig();
        String name = StringArgumentType.getString(context, "limitPlayer");
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(name);
        if (config != null && player != null && !(player instanceof ChunkloaderFakePlayer)) {
            int limit = Math.max(0, config.getPlayerLoaderLimit(player.getUuid()));
            String tooltip = limit == 0 ? "Block new and reactivated players" : "Set a custom limit for this player";
            suggestLimit(builder, limit, tooltip);
        }
        return builder.buildFuture();
    }

    private static void suggestLimit(SuggestionsBuilder builder, int limit, String tooltip) {
        if (Integer.toString(limit).startsWith(builder.getRemaining())) {
            builder.suggest(limit, Text.literal(tooltip));
        }
    }

    private static ChunkloaderConfig getConfig(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Player settings unavailable."));
        }
        return config;
    }

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "limitPlayer");
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(name);
        if (player == null) {
            context.getSource().sendError(Text.literal("Player not online: " + name));
            return null;
        }
        if (player instanceof ChunkloaderFakePlayer) {
            context.getSource().sendError(Text.literal("Choose a real player."));
            return null;
        }
        return player;
    }

    private static String formatLimit(int limit) {
        return limit == 0 ? "Unlimited" : Integer.toString(limit);
    }

    private static String formatPlayerLimit(int limit) {
        return limit == ChunkloaderConfig.UNLIMITED_PLAYER_LIMIT ? "Unlimited" : Integer.toString(limit);
    }

    private static int showLimits(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("=== Player limits ===")
                .formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Server: " + config.getEnabledChunkloaderCount()
                + " / " + formatLimit(config.getServerLoaderLimit()) + " active"), false);
        context.getSource().sendFeedback(() -> Text.literal("Default per player: "
                + formatLimit(config.getDefaultPlayerLoaderLimit())), false);
        return 1;
    }

    private static int showPlayerLimit(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayerEntity player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        Integer override = config.getPlayerLoaderLimitOverride(player.getUuid());
        String name = player.getGameProfile().name();
        String source = override == null ? "default" : "custom";
        String message = name + ": " + config.getEnabledChunkloaderCount(player.getUuid(), name)
                + " / " + formatPlayerLimit(config.getPlayerLoaderLimit(player.getUuid())) + " active (" + source + ")";
        context.getSource().sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private static int setServerLimit(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        int limit = IntegerArgumentType.getInteger(context, "limit");
        config.setServerLoaderLimit(limit);
        context.getSource().sendFeedback(() -> Text.literal("Server limit: " + formatLimit(limit))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int setDefaultPlayerLimit(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        int limit = IntegerArgumentType.getInteger(context, "limit");
        config.setDefaultPlayerLoaderLimit(limit);
        context.getSource().sendFeedback(() -> Text.literal("Default per player: " + formatLimit(limit))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int setPlayerLimit(CommandContext<ServerCommandSource> context) {
        return setPlayerLimit(context, IntegerArgumentType.getInteger(context, "limit"));
    }

    private static int setPlayerLimit(CommandContext<ServerCommandSource> context, int limit) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayerEntity player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        String name = player.getGameProfile().name();
        config.setPlayerLoaderLimit(player.getUuid(), limit);
        context.getSource().sendFeedback(() -> Text.literal(name + "'s limit: " + formatPlayerLimit(limit))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int clearPlayerLimit(CommandContext<ServerCommandSource> context) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayerEntity player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        String name = player.getGameProfile().name();
        config.clearPlayerLoaderLimit(player.getUuid());
        context.getSource().sendFeedback(() -> Text.literal(name + "'s limit: "
                + formatLimit(config.getDefaultPlayerLoaderLimit()) + " (default)")
                .formatted(Formatting.GREEN), true);
        return 1;
    }
}
