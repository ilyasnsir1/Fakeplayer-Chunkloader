package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererNameMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void chunkloader$skipHiddenNameTag(AbstractClientPlayer player, PlayerRenderState state,
            float tickDelta, CallbackInfo ci) {
        String name = FakePlayerNameCache.getPlainName(player);
        if (Boolean.FALSE.equals(FakePlayerVisibilityCache.getVisibility(name))) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
