package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListPlaceNewPlayerMixin {

    @Inject(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At("HEAD")
    )
    private void chunkloader$enterSyntheticSpawn(
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie,
            CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            SyntheticPlayerContext.enterSpawn();
            SyntheticPlayerContext.mark(player);
        }
    }

    @Inject(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At("RETURN")
    )
    private void chunkloader$exitSyntheticSpawn(
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie,
            CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            SyntheticPlayerContext.exitSpawn();
        }
    }
}
