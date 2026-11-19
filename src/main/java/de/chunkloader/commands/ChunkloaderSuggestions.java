package de.chunkloader.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.function.Predicate;

final class ChunkloaderSuggestions {
    static final SuggestionProvider<CommandSourceStack> OPERABLE_LOADERS = loaders(false, entry -> true);
    static final SuggestionProvider<CommandSourceStack> OPERABLE_CHUNKPLAYERS = loaders(false, entry -> !entry.allowMobSpawning());
    static final SuggestionProvider<CommandSourceStack> OWNED_LOADERS = loaders(true, entry -> true);
    static final SuggestionProvider<CommandSourceStack> DISABLED_LOADERS = loaders(false, entry -> !entry.enabled());
    static final SuggestionProvider<CommandSourceStack> ACTIVE_LOADERS = loaders(false, ChunkloaderTarget::enabled);
    static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) -> {
        suggestOnlinePlayers(context.getSource(), builder, player -> true);
        return builder.buildFuture();
    };

    private ChunkloaderSuggestions() {
    }

    private static SuggestionProvider<CommandSourceStack> loaders(boolean ownerOnly, Predicate<ChunkloaderTarget> filter) {
        return (context, builder) -> {
            var config = ChunkloaderForgeMod.getConfig();
            if (config != null) {
                ServerPlayer caller = context.getSource().getPlayer();
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

    static void suggestOnlinePlayers(CommandSourceStack source, SuggestionsBuilder builder, Predicate<ServerPlayer> filter) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (!(player instanceof ChunkloaderFakePlayer) && filter.test(player)) {
                suggestName(builder, player.getGameProfile().getName());
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
