package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkLoadingManager.class)
public class ServerChunkLoadingManagerMixin {

    @Inject(method = "doesNotGenerateChunks", at = @At("HEAD"), cancellable = true)
    private void chunkloader$fakePlayerDoesNotGenerateChunks(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ChunkloaderFakePlayer fakePlayer) {
            ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
            if (manager != null && manager.allowsMobSpawning(fakePlayer)) {
                cir.setReturnValue(false);
            } else {
            cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void chunkloader$forceFakePlayerViewDistance(ServerPlayerEntity player, CallbackInfoReturnable<Integer> cir) {
        if (player instanceof ChunkloaderFakePlayer fakePlayer) {
            ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
            if (manager != null && manager.allowsMobSpawning(fakePlayer)) {
                int spawnRadius = Integer.getInteger("chunkloader.fakeplayerMobSpawnChunkRadius", 8);
                cir.setReturnValue(Math.max(0, spawnRadius));
            } else {
            cir.setReturnValue(0);
            }
        }
    }
}

