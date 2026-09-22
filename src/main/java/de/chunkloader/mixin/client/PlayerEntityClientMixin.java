package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityClientMixin {

    private String getFakePlayerPlainName(Player player) {
        return FakePlayerNameCache.getPlainName(player);
    }

    @Inject(method = "getDisplayName()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player)(Object)this;

        String plainName = getFakePlayerPlainName(self);
        if (plainName == null) {
            return;
        }

        Boolean cacheValue = FakePlayerVisibilityCache.getVisibility(plainName);
        if (cacheValue == null) {
            cacheValue = true;
        }

        if (!cacheValue) {
            cir.setReturnValue(Component.empty());
            return;
        }

        Component customName = self.getCustomName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }

    @Inject(method = "shouldShowName()Z", at = @At("HEAD"), cancellable = true)
    private void onShouldRenderName(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;

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

