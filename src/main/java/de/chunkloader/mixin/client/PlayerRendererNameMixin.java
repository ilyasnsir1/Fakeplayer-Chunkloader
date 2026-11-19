package de.chunkloader.mixin.client;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererNameMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void chunkloader$skipHiddenNameTag(Avatar avatar, AvatarRenderState state,
            float tickDelta, CallbackInfo ci) {
        if (!(avatar instanceof Player player)) {
            return;
        }
        String name = FakePlayerNameCache.getPlainName(player);
        if (Boolean.FALSE.equals(FakePlayerVisibilityCache.getVisibility(name))) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
