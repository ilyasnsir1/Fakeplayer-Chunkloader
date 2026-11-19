package de.chunkloader.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

final class ChunkloaderLimitCommands {
    private ChunkloaderLimitCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> createNode() {
        return Commands.literal("limits")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderLimitCommands::showLimits)
                .then(Commands.literal("server")
                        .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                .executes(ChunkloaderLimitCommands::setServerLimit)))
                .then(Commands.literal("default")
                        .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                .suggests(ChunkloaderLimitCommands::suggestDefaultPlayerLimit)
                                .executes(ChunkloaderLimitCommands::setDefaultPlayerLimit)))
                .then(Commands.literal("player")
                        .then(Commands.argument("limitPlayer", StringArgumentType.word())
                                .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                                .then(Commands.literal("status")
                                        .executes(ChunkloaderLimitCommands::showPlayerLimit))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                        .suggests(ChunkloaderLimitCommands::suggestPlayerLimit)
                                        .executes(ChunkloaderLimitCommands::setPlayerLimit))
                                .then(Commands.literal("unlimited")
                                        .executes(context -> setPlayerLimit(context, ChunkloaderConfig.UNLIMITED_PLAYER_LIMIT)))
                                .then(Commands.literal("reset")
                                        .executes(ChunkloaderLimitCommands::clearPlayerLimit))));
    }

    private static CompletableFuture<Suggestions> suggestDefaultPlayerLimit(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var config = ChunkloaderForgeMod.getConfig();
        if (config != null) {
            suggestLimit(builder, config.getDefaultPlayerLoaderLimit(), "Default per player (0 = unlimited)");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerLimit(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var config = ChunkloaderForgeMod.getConfig();
        String name = StringArgumentType.getString(context, "limitPlayer");
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (config != null && player != null && !(player instanceof ChunkloaderFakePlayer)) {
            int limit = Math.max(0, config.getPlayerLoaderLimit(player.getUUID()));
            String tooltip = limit == 0 ? "Block new and reactivated players" : "Set a custom limit for this player";
            suggestLimit(builder, limit, tooltip);
        }
        return builder.buildFuture();
    }

    private static void suggestLimit(SuggestionsBuilder builder, int limit, String tooltip) {
        if (Integer.toString(limit).startsWith(builder.getRemaining())) {
            builder.suggest(limit, Component.literal(tooltip));
        }
    }

    private static ChunkloaderConfig getConfig(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Player settings unavailable."));
        }
        return config;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "limitPlayer");
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Player not online: " + name));
            return null;
        }
        if (player instanceof ChunkloaderFakePlayer) {
            context.getSource().sendFailure(Component.literal("Choose a real player."));
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

    private static int showLimits(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("=== Player limits ===")
                .withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Server: " + config.getEnabledChunkloaderCount()
                + " / " + formatLimit(config.getServerLoaderLimit()) + " active"), false);
        context.getSource().sendSuccess(() -> Component.literal("Default per player: "
                + formatLimit(config.getDefaultPlayerLoaderLimit())), false);
        return 1;
    }

    private static int showPlayerLimit(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayer player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        Integer override = config.getPlayerLoaderLimitOverride(player.getUUID());
        String name = player.getGameProfile().name();
        String source = override == null ? "default" : "custom";
        String message = name + ": " + config.getEnabledChunkloaderCount(player.getUUID(), name)
                + " / " + formatPlayerLimit(config.getPlayerLoaderLimit(player.getUUID())) + " active (" + source + ")";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setServerLimit(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        int limit = IntegerArgumentType.getInteger(context, "limit");
        config.setServerLoaderLimit(limit);
        context.getSource().sendSuccess(() -> Component.literal("Server limit: " + formatLimit(limit))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setDefaultPlayerLimit(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = getConfig(context);
        if (config == null) {
            return 0;
        }
        int limit = IntegerArgumentType.getInteger(context, "limit");
        config.setDefaultPlayerLoaderLimit(limit);
        context.getSource().sendSuccess(() -> Component.literal("Default per player: " + formatLimit(limit))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setPlayerLimit(CommandContext<CommandSourceStack> context) {
        return setPlayerLimit(context, IntegerArgumentType.getInteger(context, "limit"));
    }

    private static int setPlayerLimit(CommandContext<CommandSourceStack> context, int limit) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayer player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        String name = player.getGameProfile().name();
        config.setPlayerLoaderLimit(player.getUUID(), limit);
        context.getSource().sendSuccess(() -> Component.literal(name + "'s limit: " + formatPlayerLimit(limit))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearPlayerLimit(CommandContext<CommandSourceStack> context) {
        ChunkloaderConfig config = getConfig(context);
        ServerPlayer player = getPlayer(context);
        if (config == null || player == null) {
            return 0;
        }
        String name = player.getGameProfile().name();
        config.clearPlayerLoaderLimit(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(name + "'s limit: "
                + formatLimit(config.getDefaultPlayerLoaderLimit()) + " (default)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
