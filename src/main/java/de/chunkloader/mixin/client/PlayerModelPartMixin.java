package de.chunkloader.mixin.client;

import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.client.SkinLayerMask;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerModelPartMixin {

    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    private void chunkloader$applyCustomSkinLayers(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        if (part == null || part == PlayerModelPart.CAPE) {
            return;
        }

        Integer mask = CustomFakePlayerSkinCache.getLayerMask(((Player) (Object) this).getUUID());
        if (mask == null) {
            return;
        }
        cir.setReturnValue(SkinLayerMask.isShown(mask, part));
    }
}
