package de.chunkloader.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererNameMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void chunkloader$skipHiddenNameTag(AbstractClientPlayer player, Component text, PoseStack poses,
            MultiBufferSource buffers, int light, float tickDelta, CallbackInfo ci) {
        String name = FakePlayerNameCache.getPlainName(player);
        if (Boolean.FALSE.equals(FakePlayerVisibilityCache.getVisibility(name))) {
            ci.cancel();
        }
    }
}
