package de.chunkloader.mixin.client;

import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.client.FakePlayerEasterEggSkinCache;
import de.chunkloader.client.SkinOverrideHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntrySkinMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true, require = 0)
    private void chunkloader$overrideSkinTextures(CallbackInfoReturnable<Object> cir) {
        UUID uuid = ((PlayerListEntry) (Object) this).getProfile().id();
        if (uuid == null) {
            return;
        }

        Object original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        CustomFakePlayerSkinCache.CustomSkin customSkin = CustomFakePlayerSkinCache.getSkin(uuid);
        if (customSkin != null) {
            Object overridden = SkinOverrideHelper.applyBodyOverride(original, customSkin.assetId(), customSkin.model());
            if (overridden != null) {
                cir.setReturnValue(overridden);
            }
            return;
        }

        Identifier easterEggSkin = FakePlayerEasterEggSkinCache.getSkinTexture(uuid);
        if (easterEggSkin != null) {
            Object overridden = SkinOverrideHelper.applyBodyOverride(original, easterEggSkin);
            if (overridden != null) {
                cir.setReturnValue(overridden);
            }
        }
    }
}
