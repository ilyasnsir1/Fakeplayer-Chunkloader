package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerListedMixin {
    @Inject(method = "isListed", at = @At("HEAD"), cancellable = true, require = 0)
    private void chunkloader$hideFromTabList(CallbackInfoReturnable<Boolean> cir) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (manager.isTabListHidden(self)) {
            cir.setReturnValue(false);
        }
    }
}

