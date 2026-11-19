package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerRemoveMixin {

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$blockUnexpectedFakePlayerRemove(ServerPlayerEntity player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer && !SyntheticPlayerContext.isDespawning()) {
            ci.cancel();
        }
    }
}
