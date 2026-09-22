package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityLoginPoseMixin {

    @Inject(
        method = "snapTo(Lnet/minecraft/world/phys/Vec3;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$skipLoginWorldSpawnSnap(Vec3 pos, float yRot, float xRot, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ChunkloaderFakePlayer && SyntheticPlayerContext.isSpawning()) {
            ci.cancel();
        }
    }
}
