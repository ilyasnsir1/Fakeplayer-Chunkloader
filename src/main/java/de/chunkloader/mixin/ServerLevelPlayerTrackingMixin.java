package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks", remap = false)
public abstract class ServerLevelPlayerTrackingMixin {
    @Inject(method = {
            "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V",
            "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V"
    }, at = @At("RETURN"), remap = false)
    private void chunkloader$invalidateSpawningPlayers(Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer) {
            var manager = ChunkloaderForgeMod.getChunkloaderManager();
            if (manager != null) manager.invalidateSpawnPlayerLists();
        }
    }
}
