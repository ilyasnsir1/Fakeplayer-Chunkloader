package de.chunkloader.client;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;

public final class SkinOverrideHelper {

    private SkinOverrideHelper() {
    }

    public static PlayerSkin applyBodyOverride(PlayerSkin originalSkin, Identifier skinAssetId) {
        return applyBodyOverride(originalSkin, skinAssetId, null);
    }

    public static PlayerSkin applyBodyOverride(
        PlayerSkin originalSkin,
        Identifier skinAssetId,
        PlayerModelType model
    ) {
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
