package de.chunkloader.util;

public class KeybindHelper {
    private static volatile String disabledChunkloadersKeyName = "F8";

    public static void setDisabledChunkloadersKeyName(String keyName) {
        if (keyName != null && !keyName.isEmpty()) {
            disabledChunkloadersKeyName = keyName;
        }
    }

    public static String getDisabledChunkloadersKeyName() {
        return disabledChunkloadersKeyName;
    }
}

