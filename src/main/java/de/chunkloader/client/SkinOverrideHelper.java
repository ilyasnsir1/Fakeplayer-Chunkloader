package de.chunkloader.client;

import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

public final class SkinOverrideHelper {

    private SkinOverrideHelper() {
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId) {
        return applyBodyOverride(skinTextures, skinAssetId, (SkinTextures.Model) null);
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId, SkinModelType model) {
        SkinTextures.Model playerModel = null;
        if (model == SkinModelType.SLIM) {
            playerModel = SkinTextures.Model.SLIM;
        } else if (model == SkinModelType.WIDE) {
            playerModel = SkinTextures.Model.WIDE;
        }
        return applyBodyOverride(skinTextures, skinAssetId, playerModel);
    }

    public static Object applyBodyOverride(Object skinTextures, Identifier skinAssetId, SkinTextures.Model model) {
        if (!(skinTextures instanceof SkinTextures originalSkin) || skinAssetId == null) {
            return null;
        }

        return new SkinTextures(
            toBoundTextureId(skinAssetId),
            originalSkin.textureUrl(),
            originalSkin.capeTexture(),
            originalSkin.elytraTexture(),
            model != null ? model : originalSkin.model(),
            originalSkin.secure()
        );
    }

    /**
     * 1.21.8 SkinTextures uses the raw atlas id. Dynamic skins are registered under
     * {@code textures/<path>.png}; 1.21.9+ ClientAsset added that prefix automatically.
     */
    private static Identifier toBoundTextureId(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("textures/")) {
            return id;
        }
        return Identifier.of(id.getNamespace(), "textures/" + path + ".png");
    }
}
