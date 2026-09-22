package de.chunkloader.util;

public final class KeybindHelper {
    private static volatile String disabledChunkloadersKeyName = "F8";

    private KeybindHelper() {
    }

    public static void setDisabledChunkloadersKeyName(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return;
        }
        disabledChunkloadersKeyName = keyName;
    }

    public static String getDisabledChunkloadersKeyName() {
        String name = disabledChunkloadersKeyName;
        if (name == null || name.isBlank()) {
            return "F8";
        }
        return name;
    }
}

