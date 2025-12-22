package de.chunkloader.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerNameCache {

    private static final Map<Player, String> plainNameCache = new ConcurrentHashMap<>();

    private FakePlayerNameCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String getPlainName(Player player) {
        if (player == null) {
            return null;
        }

        String cached = plainNameCache.get(player);
        if (cached != null) {
            return cached;
        }

        Component customName = player.getCustomName();
        if (customName == null) {
            return null;
        }

        String nameString = customName.getString();
        if (nameString == null || nameString.isEmpty()) {
            return null;
        }

        String plainName = removeFormattingCodes(nameString);

        if (!plainName.startsWith("Fakeplayer") && !plainName.startsWith("Chunkplayer") &&
            !plainName.startsWith("fakeplayer") && !plainName.startsWith("chunkplayer")) {
            return null;
        }

        plainNameCache.put(player, plainName);
        return plainName;
    }

    private static String removeFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int idx = text.indexOf('§');
        if (idx == -1) {
            return text.trim();
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if ((next >= '0' && next <= '9') ||
                    (next >= 'a' && next <= 'f') ||
                    (next >= 'k' && next <= 'o') ||
                    next == 'r') {
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    public static void remove(Player player) {
        if (player != null) {
            plainNameCache.remove(player);
        }
    }

    public static void clear() {
        plainNameCache.clear();
    }
}
