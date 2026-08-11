package de.chunkloader.client;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;

public final class SkinOverrideHelper {

    private SkinOverrideHelper() {
    }

    public static PlayerSkin applyBodyOverride(PlayerSkin originalSkin, ResourceLocation skinAssetId) {
        return applyBodyOverride(originalSkin, skinAssetId, (PlayerModelType) null);
    }

    public static PlayerSkin applyBodyOverride(
        PlayerSkin originalSkin,
        ResourceLocation skinAssetId,
        SkinModelType model
    ) {
        PlayerModelType playerModel = null;
        if (model == SkinModelType.SLIM) {
            playerModel = PlayerModelType.SLIM;
        } else if (model == SkinModelType.WIDE) {
            playerModel = PlayerModelType.WIDE;
        }
        return applyBodyOverride(originalSkin, skinAssetId, playerModel);
    }

    public static PlayerSkin applyBodyOverride(
        PlayerSkin originalSkin,
        ResourceLocation skinAssetId,
        PlayerModelType model
    ) {
        if (originalSkin == null || skinAssetId == null) {
            return null;
        }

        ClientAsset.ResourceTexture bodyTexture = new ClientAsset.ResourceTexture(skinAssetId);
        PlayerSkin.Patch patch = PlayerSkin.Patch.create(
            Optional.of(bodyTexture),
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(model)
        );
        return originalSkin.with(patch);
    }
}
