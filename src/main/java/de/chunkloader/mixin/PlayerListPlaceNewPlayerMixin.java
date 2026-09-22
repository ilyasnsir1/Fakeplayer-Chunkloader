package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import java.util.Optional;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(
        method = "loadPlayerData(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/ErrorReporter;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipFakePlayerData(
            ServerPlayerEntity player,
            ErrorReporter errorReporter,
            CallbackInfoReturnable<Optional<ReadView>> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(
        method = "savePlayerData(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipFakePlayerSave(ServerPlayerEntity player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;setServerWorld(Lnet/minecraft/server/world/ServerWorld;)V"
        )
    )
    private void chunkloader$keepFakePlayerWorld(ServerPlayerEntity player, ServerWorld world) {
        if (player instanceof ChunkloaderFakePlayer) {
            return;
        }
        player.setServerWorld(world);
    }
}
