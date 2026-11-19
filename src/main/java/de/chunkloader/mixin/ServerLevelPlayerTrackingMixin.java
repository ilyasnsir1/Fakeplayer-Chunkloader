package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.world.ServerWorld$ServerEntityHandler")
public abstract class ServerLevelPlayerTrackingMixin {
    @Inject(method = {
            "startTracking(Lnet/minecraft/entity/Entity;)V",
            "stopTracking(Lnet/minecraft/entity/Entity;)V"
    }, at = @At("RETURN"))
    private void chunkloader$invalidateSpawningPlayers(Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayerEntity) {
            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager != null) manager.invalidateSpawnPlayerLists();
        }
    }
}
