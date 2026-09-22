package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class MobTargetFakePlayerMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void chunkloader$blockNonTargetFakeplayer(LivingEntity target, CallbackInfo ci) {
        if (target instanceof ChunkloaderFakePlayer fakePlayer && !fakePlayer.isMobTarget()) {
            ci.cancel();
        }
    }
}
