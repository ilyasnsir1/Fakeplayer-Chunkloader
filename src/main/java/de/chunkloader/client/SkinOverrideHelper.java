package de.chunkloader.client;

import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.Optional;

public final class SkinOverrideHelper {

    private SkinOverrideHelper() {
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId) {
        return applyBodyOverride(skinTextures, skinAssetId, (PlayerSkinType) null);
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId, SkinModelType model) {
        PlayerSkinType playerModel = null;
        if (model == SkinModelType.SLIM) {
            playerModel = PlayerSkinType.SLIM;
        } else if (model == SkinModelType.WIDE) {
            playerModel = PlayerSkinType.WIDE;
        }
        return applyBodyOverride(skinTextures, skinAssetId, playerModel);
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId, PlayerSkinType model) {
        if (!(skinTextures instanceof SkinTextures originalSkin) || skinAssetId == null) {
            return null;
        }

        AssetInfo.TextureAssetInfo bodyTexture = new AssetInfo.TextureAssetInfo(skinAssetId);
        SkinTextures.SkinOverride override = SkinTextures.SkinOverride.create(
            Optional.of(bodyTexture),
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(model)
        );
        return originalSkin.withOverride(override);
    }
}
