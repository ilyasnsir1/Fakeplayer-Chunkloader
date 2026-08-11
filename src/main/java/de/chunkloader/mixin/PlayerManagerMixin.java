package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(
        method = "broadcast(Lnet/minecraft/text/Text;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBroadcast(Text message, boolean overlay, CallbackInfo ci) {
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
            PlayerManager self = (PlayerManager)(Object)this;
            for (ServerPlayerEntity player : self.getPlayerList()) {
                if (player instanceof ChunkloaderFakePlayer) {
                    String playerName = player.getName().getString();
                    if (playerName == null || playerName.isEmpty() || messageString.contains("null")) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        PlayerManager self = (PlayerManager)(Object)this;
        for (ServerPlayerEntity player : self.getPlayerList()) {
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
