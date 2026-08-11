package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true, require = 0)
    private void chunkloader$blockFakePlayerAdvancements(AdvancementHolder advancement, String criterionName,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(false);
        }
    }
}
