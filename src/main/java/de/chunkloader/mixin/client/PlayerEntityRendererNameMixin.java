package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererNameMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void chunkloader$skipHiddenNameTag(PlayerLikeEntity avatar, PlayerEntityRenderState state,
            float tickDelta, CallbackInfo ci) {
        if (!(avatar instanceof PlayerEntity player)) {
            return;
        }
        String name = FakePlayerNameCache.getPlainName(player);
        if (Boolean.FALSE.equals(FakePlayerVisibilityCache.getVisibility(name))) {
            state.displayName = null;
            state.playerName = null;
        }
    }
}
