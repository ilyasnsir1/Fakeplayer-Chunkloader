package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityClientMixin {

    private String getFakePlayerPlainName(PlayerEntity player) {
        return FakePlayerNameCache.getPlainName(player);
    }

    @Inject(method = "getDisplayName()Lnet/minecraft/text/Text;", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        String plainName = getFakePlayerPlainName(self);
        if (plainName == null) {
            return;
        }

        Boolean cacheValue = FakePlayerVisibilityCache.getVisibility(plainName);
        if (cacheValue == null) {
            cacheValue = true;
        }

        if (!cacheValue) {
            cir.setReturnValue(Text.empty());
            return;
        }

        Text customName = self.getCustomName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }

    @Inject(method = "shouldRenderName()Z", at = @At("HEAD"), cancellable = true)
    private void onShouldRenderName(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        String plainName = getFakePlayerPlainName(self);
        if (plainName == null) {
            return;
        }

        Boolean cacheValue = FakePlayerVisibilityCache.getVisibility(plainName);
        if (cacheValue == null) {
            cacheValue = true;
        }

        cir.setReturnValue(cacheValue);
    }
}

