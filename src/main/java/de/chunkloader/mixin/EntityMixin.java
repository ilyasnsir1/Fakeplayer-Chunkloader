package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import de.chunkloader.util.EntitySyncUtil;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(
        method = "refreshPositionAndAngles(Lnet/minecraft/util/math/Vec3d;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipLoginWorldSpawnSnap(Vec3d pos, float yaw, float pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ChunkloaderFakePlayer && SyntheticPlayerContext.isSpawning()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "setCustomName(Lnet/minecraft/text/Text;)V",
        at = @At("TAIL")
    )
    private void onSetCustomName(net.minecraft.text.Text name, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (self instanceof ChunkloaderFakePlayer && self.getWorld() instanceof ServerWorld serverWorld) {
            EntitySyncUtil.syncMetadataImmediately(serverWorld, self);
        }
    }

    @Inject(
        method = "setCustomNameVisible(Z)V",
        at = @At("TAIL")
    )
    private void onSetCustomNameVisible(boolean visible, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (self instanceof ChunkloaderFakePlayer && self.getWorld() instanceof ServerWorld serverWorld) {
            EntitySyncUtil.syncMetadataImmediately(serverWorld, self);
        }
    }
}

