package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
        method = "loadPlayerData(Lnet/minecraft/server/PlayerConfigEntry;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipFakePlayerData(
            PlayerConfigEntry entry,
            CallbackInfoReturnable<Optional<NbtCompound>> cir) {
        if (SyntheticPlayerContext.isSpawning()
                || (entry != null && SyntheticPlayerContext.isMarked(entry.id()))) {
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
            target = "Lnet/minecraft/server/world/ServerWorld;onPlayerConnected(Lnet/minecraft/server/network/ServerPlayerEntity;)V"
        )
    )
    private void chunkloader$connectFakePlayerToOwnWorld(ServerWorld world, ServerPlayerEntity player) {
        if (player instanceof ChunkloaderFakePlayer) {
            if (player.getEntityWorld() instanceof ServerWorld actual) {
                actual.onPlayerConnected(player);
                return;
            }
        }
        world.onPlayerConnected(player);
    }

    @Redirect(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;sendWorldInfo(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/world/ServerWorld;)V"
        )
    )
    private void chunkloader$sendFakePlayerWorldInfo(PlayerManager manager, ServerPlayerEntity player, ServerWorld world) {
        if (player instanceof ChunkloaderFakePlayer) {
            if (player.getEntityWorld() instanceof ServerWorld actual) {
                manager.sendWorldInfo(player, actual);
                return;
            }
        }
        manager.sendWorldInfo(player, world);
    }
}
