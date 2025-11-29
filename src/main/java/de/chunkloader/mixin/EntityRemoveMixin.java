package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityRemoveMixin {
    
    @Inject(method = "remove(Lnet/minecraft/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            return;
        }
        
        if (reason != Entity.RemovalReason.KILLED) {
            return;
        }
        
        try {
            if (self instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
                ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(self.getUuid());
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error handling marker destruction", e);
        }
    }
}

