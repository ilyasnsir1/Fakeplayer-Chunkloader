package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SculkShriekerBlockEntity.class)
public class SculkShriekerBlockEntityMixin {
    @Inject(method = "tryShriek", at = @At("HEAD"), cancellable = true)
    private void chunkloader$ignoreLoader(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            ci.cancel();
        }
    }
}
