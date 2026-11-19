package de.chunkloader.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.function.Predicate;

final class ChunkloaderSuggestions {
    static final SuggestionProvider<ServerCommandSource> OPERABLE_LOADERS = loaders(false, entry -> true);
    static final SuggestionProvider<ServerCommandSource> OPERABLE_CHUNKPLAYERS = loaders(false, entry -> !entry.allowMobSpawning());
    static final SuggestionProvider<ServerCommandSource> OWNED_LOADERS = loaders(true, entry -> true);
    static final SuggestionProvider<ServerCommandSource> DISABLED_LOADERS = loaders(false, entry -> !entry.enabled());
    static final SuggestionProvider<ServerCommandSource> ACTIVE_LOADERS = loaders(false, ChunkloaderTarget::enabled);
    static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYERS = (context, builder) -> {
        suggestOnlinePlayers(context.getSource(), builder, player -> true);
        return builder.buildFuture();
    };

    private ChunkloaderSuggestions() {
    }

    private static SuggestionProvider<ServerCommandSource> loaders(boolean ownerOnly, Predicate<ChunkloaderTarget> filter) {
        return (context, builder) -> {
            var config = ChunkloaderMod.getConfig();
            if (config != null) {
                ServerPlayerEntity caller = context.getSource().getPlayer();
                for (ChunkloaderTarget entry : config.getChunkEntries()) {
                    boolean allowed = caller == null || (ownerOnly
                            ? PermissionManager.canModifyEntry(caller, entry)
                            : PermissionManager.canOperateEntry(caller, entry));
                    if (allowed && entry.name() != null && filter.test(entry)) {
                        suggestName(builder, entry.name());
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    static void suggestOnlinePlayers(ServerCommandSource source, SuggestionsBuilder builder, Predicate<ServerPlayerEntity> filter) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (!(player instanceof ChunkloaderFakePlayer) && filter.test(player)) {
                suggestName(builder, player.getGameProfile().name());
            }
        }
    }

    static void suggestName(SuggestionsBuilder builder, String name) {
        String escaped = StringArgumentType.escapeIfRequired(name);
        String prefix = builder.getRemainingLowerCase();
        if (name.toLowerCase(Locale.ROOT).startsWith(prefix) || escaped.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            builder.suggest(escaped);
        }
    }
}
