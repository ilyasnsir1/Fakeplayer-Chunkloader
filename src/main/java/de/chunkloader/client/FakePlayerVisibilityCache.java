package de.chunkloader.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerVisibilityCache {
    private static final Map<String, Boolean> visibilityCache = new ConcurrentHashMap<>();
    
    private FakePlayerVisibilityCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    public static void setVisibility(String playerName, boolean visible) {
        if (playerName != null && !playerName.isEmpty()) {
            visibilityCache.put(playerName, visible);
        }
    }
    
    public static Boolean getVisibility(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return null;
        }
        return visibilityCache.get(playerName);
    }
}

