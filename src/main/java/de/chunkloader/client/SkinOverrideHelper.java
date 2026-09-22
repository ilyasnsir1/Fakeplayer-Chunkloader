package de.chunkloader.client;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class SkinOverrideHelper {

    private SkinOverrideHelper() {
    }

    public static PlayerSkin applyBodyOverride(PlayerSkin originalSkin, ResourceLocation skinAssetId) {
        return applyBodyOverride(originalSkin, skinAssetId, (PlayerSkin.Model) null);
    }

    public static PlayerSkin applyBodyOverride(
        PlayerSkin originalSkin,
        ResourceLocation skinAssetId,
        SkinModelType model
    ) {
        PlayerSkin.Model playerModel = null;
        if (model == SkinModelType.SLIM) {
            playerModel = PlayerSkin.Model.SLIM;
        } else if (model == SkinModelType.WIDE) {
            playerModel = PlayerSkin.Model.WIDE;
        }
        return applyBodyOverride(originalSkin, skinAssetId, playerModel);
    }

    public static PlayerSkin applyBodyOverride(
        PlayerSkin originalSkin,
        ResourceLocation skinAssetId,
        PlayerSkin.Model model
    ) {
        if (originalSkin == null || skinAssetId == null) {
            return null;
        }

        return new PlayerSkin(
            toBoundTextureId(skinAssetId),
            originalSkin.textureUrl(),
            originalSkin.capeTexture(),
            originalSkin.elytraTexture(),
            model != null ? model : originalSkin.model(),
            originalSkin.secure()
        );
    }

    /**
     * 1.21.8 PlayerSkin uses the raw atlas id. Dynamic skins are registered under
     * {@code textures/<path>.png}; 1.21.9+ ClientAsset added that prefix automatically.
     */
    private static ResourceLocation toBoundTextureId(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("textures/")) {
            return id;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + path + ".png");
    }
}
