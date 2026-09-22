package de.chunkloader.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class NativeImageColorUtil {

    private NativeImageColorUtil() {
    }

    /** Converts ARGB (0xAARRGGBB) to NativeImage ABGR / little-endian RGBA. */
    public static int argbToNative(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
