package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import java.util.Optional;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(
        method = "load(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/util/ProblemReporter;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipFakePlayerData(
            ServerPlayer player,
            ProblemReporter reporter,
            CallbackInfoReturnable<Optional<ValueInput>> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(
        method = "save(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipFakePlayerSave(ServerPlayer player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setServerLevel(Lnet/minecraft/server/level/ServerLevel;)V"
        )
    )
    private void chunkloader$keepFakePlayerWorld(ServerPlayer player, ServerLevel level) {
        if (player instanceof ChunkloaderFakePlayer) {
            return;
        }
        player.setServerLevel(level);
    }
}
