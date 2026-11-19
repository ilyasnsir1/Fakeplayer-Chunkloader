package de.chunkloader.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

final class ChunkloaderSharingCommands {
    private ChunkloaderSharingCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> share() {
        return Commands.literal("share")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ChunkloaderTarget loader = getSuggestedLoader(context);
                                    if (loader != null) {
                                        ChunkloaderSuggestions.suggestOnlinePlayers(context.getSource(), builder,
                                                recipient -> PermissionManager.canUse(recipient)
                                                        && !PermissionManager.canModifyEntry(recipient, loader)
                                                        && !loader.sharedWith().containsKey(recipient.getUUID()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ChunkloaderSharingCommands::grantShare)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> unshare() {
        return Commands.literal("unshare")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .then(Commands.argument("player", StringArgumentType.word())
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

    static LiteralArgumentBuilder<CommandSourceStack> shares() {
        return Commands.literal("shares")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                        .executes(ChunkloaderSharingCommands::listShares));
    }

    private static ChunkloaderTarget getSuggestedLoader(CommandContext<CommandSourceStack> context) {
        var config = ChunkloaderMod.getConfig();
        var loader = config == null ? null : config.getEntryByName(StringArgumentType.getString(context, "name"));
        ServerPlayer caller = context.getSource().getPlayer();
        return caller == null || PermissionManager.canModifyEntry(caller, loader) ? loader : null;
    }

    private static String currentName(CommandContext<CommandSourceStack> context, UUID id, String savedName) {
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().name() : savedName;
    }

    private static ChunkloaderTarget getManagedLoader(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        var config = ChunkloaderMod.getConfig();
        ChunkloaderTarget loader = config == null ? null : config.getEntryByName(name);
        if (loader == null) {
            context.getSource().sendFailure(Component.literal("Player '" + name + "' not found."));
            return null;
        }
        ServerPlayer caller = context.getSource().getPlayer();
        if (caller != null && !PermissionManager.canModifyEntry(caller, loader)) {
            context.getSource().sendFailure(Component.literal("Only the owner or an admin can manage access to this player."));
            return null;
        }
        return loader;
    }

    private static int grantShare(CommandContext<CommandSourceStack> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer recipient = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (recipient == null || recipient instanceof ChunkloaderFakePlayer) {
            context.getSource().sendFailure(Component.literal("Choose a player who is online."));
            return 0;
        }
        if (!PermissionManager.canUse(recipient)) {
            context.getSource().sendFailure(Component.literal("This player needs permission to use the mod first."));
            return 0;
        }
        if (PermissionManager.canModifyEntry(recipient, loader)) {
            context.getSource().sendFailure(Component.literal("This player can already manage " + loader.name() + "."));
            return 0;
        }
        String playerName = recipient.getGameProfile().name();
        if (!ChunkloaderMod.getConfig().setEntrySharedPlayer(loader.name(), recipient.getUUID(), playerName)) {
            context.getSource().sendFailure(Component.literal("This player already has access to " + loader.name() + "."));
            return 0;
        }
        refreshMaps(context);
        context.getSource().sendSuccess(() -> Component.literal(loader.name() + " shared with " + playerName + "."), false);
        recipient.sendSystemMessage(Component.literal("You now have access to " + loader.name() + "."));
        return 1;
    }

    private static int revokeShare(CommandContext<CommandSourceStack> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        UUID playerId = online != null && loader.sharedWith().containsKey(online.getUUID()) ? online.getUUID()
                : loader.sharedWith().entrySet().stream()
                        .filter(share -> share.getValue().equalsIgnoreCase(name))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);
        if (playerId == null || !ChunkloaderMod.getConfig().setEntrySharedPlayer(loader.name(), playerId, null)) {
            context.getSource().sendFailure(Component.literal("This player has no share for " + loader.name() + "."));
            return 0;
        }
        refreshMaps(context);
        context.getSource().sendSuccess(() -> Component.literal("Access to " + loader.name() + " revoked for " + name + "."), false);
        return 1;
    }

    private static int listShares(CommandContext<CommandSourceStack> context) {
        ChunkloaderTarget loader = getManagedLoader(context);
        if (loader == null) {
            return 0;
        }
        var names = loader.sharedWith().entrySet().stream()
                .map(share -> currentName(context, share.getKey(), share.getValue()))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        String message = names.isEmpty() ? loader.name() + " is not shared."
                : loader.name() + " is shared with " + String.join(", ", names) + ".";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return names.size();
    }

    private static void refreshMaps(CommandContext<CommandSourceStack> context) {
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                ChunkloaderMod.getChunkloaderManager());
    }
}
