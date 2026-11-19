package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererNameMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void chunkloader$skipHiddenNameTag(AbstractClientPlayerEntity player, PlayerEntityRenderState state,
            float tickDelta, CallbackInfo ci) {
        String name = FakePlayerNameCache.getPlainName(player);
        if (Boolean.FALSE.equals(FakePlayerVisibilityCache.getVisibility(name))) {
            state.displayName = null;
            state.playerName = null;
        }
    }
}
