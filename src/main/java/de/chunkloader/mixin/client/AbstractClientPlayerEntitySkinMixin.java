package de.chunkloader.mixin.client;

import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.client.FakePlayerEasterEggSkinCache;
import de.chunkloader.client.SkinOverrideHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntitySkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void chunkloader$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getGameProfile().id();
        if (uuid == null) {
            return;
        }

        PlayerSkin original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        CustomFakePlayerSkinCache.CustomSkin customSkin = CustomFakePlayerSkinCache.getSkin(uuid);
        if (customSkin != null) {
            PlayerSkin overridden = SkinOverrideHelper.applyBodyOverride(
                original,
                customSkin.assetId(),
                customSkin.model()
            );
            if (overridden != null) {
                cir.setReturnValue(overridden);
            }
            return;
        }

        ResourceLocation easterEggSkin = FakePlayerEasterEggSkinCache.getSkinTexture(uuid);
        if (easterEggSkin != null) {
            PlayerSkin overridden = SkinOverrideHelper.applyBodyOverride(original, easterEggSkin);
            if (overridden != null) {
                cir.setReturnValue(overridden);
            }
        }
    }
}
