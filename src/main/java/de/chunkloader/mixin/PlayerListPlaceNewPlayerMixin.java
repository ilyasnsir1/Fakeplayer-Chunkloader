package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerListPlaceNewPlayerMixin {

    @Inject(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
        at = @At("HEAD")
    )
    private void chunkloader$enterSyntheticSpawn(
            ClientConnection connection,
            ServerPlayerEntity player,
            ConnectedClientData clientData,
            CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            SyntheticPlayerContext.enterSpawn();
            SyntheticPlayerContext.mark(player);
        }
    }

    @Inject(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
        at = @At("RETURN")
    )
    private void chunkloader$exitSyntheticSpawn(
            ClientConnection connection,
            ServerPlayerEntity player,
            ConnectedClientData clientData,
            CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            SyntheticPlayerContext.exitSpawn();
        }
    }
}
