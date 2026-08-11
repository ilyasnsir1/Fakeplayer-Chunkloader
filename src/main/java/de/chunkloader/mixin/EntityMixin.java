package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.util.EntitySyncUtil;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(
        method = "setCustomName(Lnet/minecraft/text/Text;)V",
        at = @At("TAIL")
    )
    private void onSetCustomName(net.minecraft.text.Text name, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (self instanceof ChunkloaderFakePlayer && self.getEntityWorld() instanceof ServerWorld serverWorld) {
            EntitySyncUtil.syncMetadataImmediately(serverWorld, self);
        }
    }

    @Inject(
        method = "setCustomNameVisible(Z)V",
        at = @At("TAIL")
    )
    private void onSetCustomNameVisible(boolean visible, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (self instanceof ChunkloaderFakePlayer && self.getEntityWorld() instanceof ServerWorld serverWorld) {
            EntitySyncUtil.syncMetadataImmediately(serverWorld, self);
        }
    }
}

