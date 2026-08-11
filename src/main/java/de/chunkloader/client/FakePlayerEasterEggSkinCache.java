package de.chunkloader.client;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerEasterEggSkinCache {
    private static final Map<UUID, Integer> skinIndexByUuid = new ConcurrentHashMap<>();

    private static final Identifier SKIN_0 = Identifier.of(ChunkloaderMod.MOD_ID + ":skins/zedoy");
    private static final Identifier SKIN_1 = Identifier.of(
        ChunkloaderMod.MOD_ID
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

    public static Identifier getSkinTexture(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        Integer idx = skinIndexByUuid.get(playerUuid);
        if (idx == null) {
            return null;
        }
        Identifier chosen = (idx % 2) == 0 ? SKIN_0 : SKIN_1;
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
