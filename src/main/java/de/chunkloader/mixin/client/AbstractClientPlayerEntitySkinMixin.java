package de.chunkloader.mixin.client;

import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.client.FakePlayerEasterEggSkinCache;
import de.chunkloader.client.SkinOverrideHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
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
        UUID uuid = ((AbstractClientPlayer) (Object) this).getGameProfile().id();
        if (uuid == null) {
            return;
        }

        PlayerSkin originalSkin = cir.getReturnValue();
        if (originalSkin == null) {
            return;
        }
        CustomFakePlayerSkinCache.CustomSkin customSkin = CustomFakePlayerSkinCache.getSkin(uuid);
        if (customSkin != null) {
            cir.setReturnValue(
                SkinOverrideHelper.applyBodyOverride(originalSkin, customSkin.assetId(), customSkin.model())
            );
            return;
        }

        Identifier easterEggSkin = FakePlayerEasterEggSkinCache.getSkinTexture(uuid);
        if (easterEggSkin != null) {
            cir.setReturnValue(SkinOverrideHelper.applyBodyOverride(originalSkin, easterEggSkin));
        }
    }
}
