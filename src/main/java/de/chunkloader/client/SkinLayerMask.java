package de.chunkloader.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.player.PlayerModelPart;

@Environment(EnvType.CLIENT)
public final class SkinLayerMask {
    public static final PlayerModelPart[] EDITABLE_PARTS = {
        PlayerModelPart.HAT,
        PlayerModelPart.JACKET,
        PlayerModelPart.LEFT_SLEEVE,
        PlayerModelPart.RIGHT_SLEEVE,
        PlayerModelPart.LEFT_PANTS_LEG,
        PlayerModelPart.RIGHT_PANTS_LEG
    };

    public static final int DEFAULT_MASK = maskOf(EDITABLE_PARTS);

    private SkinLayerMask() {
    }

    public static int maskOf(PlayerModelPart... parts) {
        int mask = 0;
        for (PlayerModelPart part : parts) {
            if (part != null && part != PlayerModelPart.CAPE) {
                mask |= part.getMask();
            }
        }
        return mask;
    }

    public static int sanitize(int mask) {
        return mask & DEFAULT_MASK;
    }

    public static boolean isShown(int mask, PlayerModelPart part) {
        if (part == null || part == PlayerModelPart.CAPE) {
            return false;
        }
        int bit = part.getMask();
        return (sanitize(mask) & bit) == bit;
    }

    public static int toggle(int mask, PlayerModelPart part) {
        if (part == null || part == PlayerModelPart.CAPE) {
            return sanitize(mask);
        }
        return sanitize(mask ^ part.getMask());
    }

    public static String label(PlayerModelPart part) {
        if (part == null) {
            return "";
        }
        return switch (part) {
            case HAT -> "Hat";
            case JACKET -> "Jacket";
            case LEFT_SLEEVE -> "Left Sleeve";
            case RIGHT_SLEEVE -> "Right Sleeve";
            case LEFT_PANTS_LEG -> "Left Pant Leg";
            case RIGHT_PANTS_LEG -> "Right Pant Leg";
            default -> part.getSerializedName();
        };
    }

    public static void applyToModel(PlayerModel model, int mask) {
        if (model == null) {
            return;
        }
        int sanitized = sanitize(mask);
        model.hat.visible = isShown(sanitized, PlayerModelPart.HAT);
        model.jacket.visible = isShown(sanitized, PlayerModelPart.JACKET);
        model.leftSleeve.visible = isShown(sanitized, PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = isShown(sanitized, PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = isShown(sanitized, PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = isShown(sanitized, PlayerModelPart.RIGHT_PANTS_LEG);
    }

    public static void applyToSimpleModel(Model.Simple model, int mask) {
        if (model == null) {
            return;
        }
        int sanitized = sanitize(mask);
        ModelPart root = model.root();
        setChildVisible(root, "hat", isShown(sanitized, PlayerModelPart.HAT));
        setChildVisible(root, "jacket", isShown(sanitized, PlayerModelPart.JACKET));
        setChildVisible(root, "left_sleeve", isShown(sanitized, PlayerModelPart.LEFT_SLEEVE));
        setChildVisible(root, "right_sleeve", isShown(sanitized, PlayerModelPart.RIGHT_SLEEVE));
        setChildVisible(root, "left_pants", isShown(sanitized, PlayerModelPart.LEFT_PANTS_LEG));
        setChildVisible(root, "right_pants", isShown(sanitized, PlayerModelPart.RIGHT_PANTS_LEG));
    }

    private static void setChildVisible(ModelPart root, String childName, boolean visible) {
        if (root == null || childName == null) {
            return;
        }
        try {
            root.getChild(childName).visible = visible;
        } catch (RuntimeException ignored) {
        }
    }
}
