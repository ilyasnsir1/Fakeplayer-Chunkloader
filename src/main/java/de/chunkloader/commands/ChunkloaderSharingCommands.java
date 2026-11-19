package de.chunkloader.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;

final class ChunkloaderSharingCommands {
    private ChunkloaderSharingCommands() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> share() {
        return CommandManager.literal("share")
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ChunkloaderTarget loader = getSuggestedLoader(context);
                                    if (loader != null) {
                                        ChunkloaderSuggestions.suggestOnlinePlayers(context.getSource(), builder,
                                                recipient -> PermissionManager.canUse(recipient)
                                                        && !PermissionManager.canModifyEntry(recipient, loader)
                                                        && !loader.sharedWith().containsKey(recipient.getUuid()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ChunkloaderSharingCommands::grantShare)));
    }

    static LiteralArgumentBuilder<ServerCommandSource> unshare() {
        return CommandManager.literal("unshare")
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ChunkloaderTarget loader = getSuggestedLoader(context);
                                    if (loader != null) {
                                        loader.sharedWith().forEach((id, name) ->
                                                ChunkloaderSuggestions.suggestName(builder, currentName(context, id, name)));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ChunkloaderSharingCommands::revokeShare)));
    }

    static LiteralArgumentBuilder<ServerCommandSource> shares() {
        return CommandManager.literal("shares")
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .executes(ChunkloaderSharingCommands::listShares));
    }

    private static ChunkloaderTarget getSuggestedLoader(CommandContext<ServerCommandSource> context) {
        var config = ChunkloaderMod.getConfig();
        var loader = config == null ? null : config.getEntryByName(StringArgumentType.getString(context, "name"));
        ServerPlayerEntity caller = context.getSource().getPlayer();
        return caller == null || PermissionManager.canModifyEntry(caller, loader) ? loader : null;
    }

    private static String currentName(CommandContext<ServerCommandSource> context, UUID id, String savedName) {
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(id);
        return player != null ? player.getGameProfile().getName() : savedName;
    }

    private static ChunkloaderTarget getManagedLoader(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        var config = ChunkloaderMod.getConfig();
        ChunkloaderTarget loader = config == null ? null : config.getEntryByName(name);
        if (loader == null) {
            context.getSource().sendError(Text.literal("Player '" + name + "' not found."));
            return null;
        }
        ServerPlayerEntity caller = context.getSource().getPlayer();
        if (caller != null && !PermissionManager.canModifyEntry(caller, loader)) {
            context.getSource().sendError(Text.literal("Only the owner or an admin can manage access to this player."));
            return null;
        }
        return loader;
    }

    private static int grantShare(CommandContext<ServerCommandSource> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "player");
        ServerPlayerEntity recipient = context.getSource().getServer().getPlayerManager().getPlayer(name);
        if (recipient == null || recipient instanceof ChunkloaderFakePlayer) {
            context.getSource().sendError(Text.literal("Choose a player who is online."));
            return 0;
        }
        if (!PermissionManager.canUse(recipient)) {
            context.getSource().sendError(Text.literal("This player needs permission to use the mod first."));
            return 0;
        }
        if (PermissionManager.canModifyEntry(recipient, loader)) {
            context.getSource().sendError(Text.literal("This player can already manage " + loader.name() + "."));
            return 0;
        }
        String playerName = recipient.getGameProfile().getName();
        if (!ChunkloaderMod.getConfig().setEntrySharedPlayer(loader.name(), recipient.getUuid(), playerName)) {
            context.getSource().sendError(Text.literal("This player already has access to " + loader.name() + "."));
            return 0;
        }
        refreshMaps(context);
        context.getSource().sendFeedback(() -> Text.literal(loader.name() + " shared with " + playerName + "."), false);
        recipient.sendMessage(Text.literal("You now have access to " + loader.name() + "."), false);
        return 1;
    }

    private static int revokeShare(CommandContext<ServerCommandSource> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "player");
        ServerPlayerEntity online = context.getSource().getServer().getPlayerManager().getPlayer(name);
        UUID playerId = online != null && loader.sharedWith().containsKey(online.getUuid()) ? online.getUuid()
                : loader.sharedWith().entrySet().stream()
                        .filter(share -> share.getValue().equalsIgnoreCase(name))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);
        if (playerId == null || !ChunkloaderMod.getConfig().setEntrySharedPlayer(loader.name(), playerId, null)) {
            context.getSource().sendError(Text.literal("This player has no share for " + loader.name() + "."));
            return 0;
        }
        refreshMaps(context);
        context.getSource().sendFeedback(() -> Text.literal("Access to " + loader.name() + " revoked for " + name + "."), false);
        return 1;
    }

    private static int listShares(CommandContext<ServerCommandSource> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        var names = loader.sharedWith().entrySet().stream()
                .map(share -> currentName(context, share.getKey(), share.getValue()))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        String message = names.isEmpty() ? loader.name() + " is not shared."
                : loader.name() + " is shared with " + String.join(", ", names) + ".";
        context.getSource().sendFeedback(() -> Text.literal(message), false);
        return names.size();
    }

    private static void refreshMaps(CommandContext<ServerCommandSource> context) {
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                ChunkloaderMod.getChunkloaderManager());
    }
}
