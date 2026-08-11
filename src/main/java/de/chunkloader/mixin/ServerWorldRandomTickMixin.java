package de.chunkloader.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerWorldRandomTickMixin {

    @Inject(
        method = "tick(Ljava/util/function/BooleanSupplier;)V",
        at = @At("TAIL")
    )
    private void performRandomTicksForChunkplayers(CallbackInfo ci) {
    }
}
