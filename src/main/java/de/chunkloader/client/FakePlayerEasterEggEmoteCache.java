package de.chunkloader.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerEasterEggEmoteCache {
    private static final Map<UUID, EmoteStart> startByUuid = new ConcurrentHashMap<>();

    private FakePlayerEasterEggEmoteCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void startEmote(UUID playerUuid, long startGameTime) {
        if (playerUuid == null) {
            return;
        }
        startByUuid.put(playerUuid, new EmoteStart(startGameTime, null));
    }

    public static void startEmoteNow(UUID playerUuid, long currentWorldTime) {
        if (playerUuid == null) {
            return;
        }
        startByUuid.putIfAbsent(playerUuid, new EmoteStart(currentWorldTime, 0.0f));
    }

    public static Float getOrInitStartAge(UUID playerUuid, float currentAge, long currentWorldTime) {
        if (playerUuid == null) {
            return null;
        }
        EmoteStart start = startByUuid.get(playerUuid);
        if (start == null) {
            return null;
        }
        if (start.startAge != null) {
            return start.startAge;
        }
        float computed = currentAge - (float) (currentWorldTime - start.startWorldTime);
        start.startAge = computed;
        return computed;
    }

    public static void stopEmote(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        startByUuid.remove(playerUuid);
    }

    public static void clear() {
        startByUuid.clear();
    }

    private static final class EmoteStart {
        private final long startWorldTime;
        private volatile Float startAge;

        private EmoteStart(long startWorldTime, Float startAge) {
            this.startWorldTime = startWorldTime;
            this.startAge = startAge;
        }
    }
}
