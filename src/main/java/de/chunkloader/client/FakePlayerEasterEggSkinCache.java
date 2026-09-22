package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerEasterEggSkinCache {
    private static final Map<UUID, Integer> skinIndexByUuid = new ConcurrentHashMap<>();

    private static final ResourceLocation SKIN_0 = ResourceLocation.tryParse(ChunkloaderForgeMod.MODID + ":skins/zedoy");
    private static final ResourceLocation SKIN_1 = ResourceLocation.tryParse(
        ChunkloaderForgeMod.MODID
            + ":skins/denizking"
    );

    private FakePlayerEasterEggSkinCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void setSkinIndex(UUID playerUuid, int skinIndex) {
        if (playerUuid == null) {
            return;
        }
        if (skinIndex < 0) {
            skinIndexByUuid.remove(playerUuid);
            return;
        }
        skinIndexByUuid.put(playerUuid, skinIndex);
    }

    public static ResourceLocation getSkinTexture(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        Integer idx = skinIndexByUuid.get(playerUuid);
        if (idx == null) {
            return null;
        }
        ResourceLocation chosen = (idx % 2) == 0 ? SKIN_0 : SKIN_1;
        return chosen;
    }

    public static boolean hasSkin(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        Integer idx = skinIndexByUuid.get(playerUuid);
        return idx != null && idx >= 0;
    }

    public static void clear() {
        skinIndexByUuid.clear();
    }
}
