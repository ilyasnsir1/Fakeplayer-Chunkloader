package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerManagerMixin {

    @Inject(
        method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBroadcast(Component message, boolean overlay, CallbackInfo ci) {

        if (SyntheticPlayerContext.isSpawning()) {
            ci.cancel();
            return;
        }

        if (message == null) return;

        String messageString = message.getString();
        if (messageString == null) return;

        String lowerMessage = messageString.toLowerCase();
        if (!lowerMessage.contains("joined") && !lowerMessage.contains("left") &&
            !lowerMessage.contains("gejoint") && !lowerMessage.contains("verlassen")) {
            return;
        }

        if (messageString.contains("null") && (lowerMessage.contains("joined") || lowerMessage.contains("left"))) {
            PlayerList self = (PlayerList)(Object)this;
            for (ServerPlayer player : self.getPlayers()) {
                if (player instanceof ChunkloaderFakePlayer) {
                    String playerName = player.getName().getString();
                    if (playerName == null || playerName.isEmpty() || messageString.contains("null")) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        PlayerList self = (PlayerList)(Object)this;
        for (ServerPlayer player : self.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer) {
                String playerName = player.getName().getString();
                if (playerName != null && !playerName.isEmpty() && messageString.contains(playerName)) {
                    ci.cancel();
                    return;
                }
            }
        }

        if (ChunkloaderMod.getChunkloaderManager() != null) {
            var manager = ChunkloaderMod.getChunkloaderManager();
            for (var entry : manager.getActiveChunkloaderEntries()) {
                String prefix = entry.allowMobSpawning() ? "fakeplayer" : "chunkplayer";
                String fakePlayerName = entry.name() != null ? entry.name() :
                    (prefix + entry.chunkX() + "_" + entry.chunkZ());
                if (messageString.contains(fakePlayerName)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
