package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerList.class)
public class PlayerListBroadcastMixin {

    @Inject(
        method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void chunkloader$suppressFakeplayerJoinLeave(Component message, boolean overlay, CallbackInfo ci) {
        suppressIfFakeplayerJoinLeave(message, ci);
    }

    @Inject(
        method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;ZLjava/util/UUID;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void chunkloader$suppressFakeplayerJoinLeave(Component message, boolean overlay, UUID sender, CallbackInfo ci) {
        suppressIfFakeplayerJoinLeave(message, ci);
    }

    private void suppressIfFakeplayerJoinLeave(Component message, CallbackInfo ci) {
        if (SyntheticPlayerContext.isSpawning()) {
            ci.cancel();
            return;
        }

        if (message == null) {
            return;
        }

        String s = message.getString();
        if (s == null || s.isEmpty()) {
            return;
        }

        boolean isJoinLeave = isJoinLeaveMessage(message, s);
        if (!isJoinLeave) {
            return;
        }

        if (s.contains("null") && (s.toLowerCase().contains("joined") || s.toLowerCase().contains("left"))) {
            PlayerList self = (PlayerList) (Object) this;
            for (ServerPlayer player : self.getPlayers()) {
                if (player instanceof ChunkloaderFakePlayer) {
                    String playerName = player.getName().getString();
                    if (playerName == null || playerName.isEmpty() || s.contains("null")) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        PlayerList self = (PlayerList) (Object) this;
        for (ServerPlayer player : self.getPlayers()) {
            if (!(player instanceof ChunkloaderFakePlayer)) {
                continue;
            }

            String name = player.getName().getString();
            if (name != null && !name.isEmpty() && s.contains(name)) {
                ci.cancel();
                return;
            }
        }

        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager != null) {
            for (var entry : manager.getActiveChunkloaderEntries()) {
                String prefix = entry.allowMobSpawning() ? "fakeplayer" : "chunkplayer";
                String fakePlayerName = entry.name() != null ? entry.name() :
                    (prefix + entry.chunkX() + "_" + entry.chunkZ());
                if (s.contains(fakePlayerName)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    private static boolean isJoinLeaveMessage(Component message, String rendered) {
        String lower = rendered.toLowerCase();
        if (lower.contains("joined")
            || lower.contains("left")
            || lower.contains("gejoint")
            || lower.contains("verlassen")) {
            return true;
        }

        if (message.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            return "multiplayer.player.joined".equals(key) || "multiplayer.player.left".equals(key);
        }

        return false;
    }
}

