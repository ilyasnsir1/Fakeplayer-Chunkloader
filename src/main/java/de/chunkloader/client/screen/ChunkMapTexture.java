package de.chunkloader.client.screen;

import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkMapTile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public class ChunkMapTexture implements AutoCloseable {

    private final ChunkMapData data;
    @SuppressWarnings("unused")
    private final Level level;

    private DynamicTexture atlasTexture;
    private ResourceLocation atlasTextureId;
    private NativeImage atlasImage;

    public ChunkMapTexture(Level level, ChunkMapData data) {
        this.level = level;
        this.data = data;
        buildAtlasTexture();
    }

    private void buildAtlasTexture() {
        close();

        if (data.mapWidth() <= 0 || data.mapHeight() <= 0) {
            return;
        }

        int texW = data.mapWidth() * 16;
        int texH = data.mapHeight() * 16;

        atlasImage = new NativeImage(texW, texH, false);

        final Method setPixel;
        try {
            setPixel = resolveNativeImageSetter();
        } catch (Exception e) {
            atlasImage = null;
            return;
        }

        if (data.tiles() != null) for (ChunkMapTile tile : data.tiles()) {
            if (tile == null || tile.pixels() == null || tile.pixels().length != 16 * 16) {
                continue;
            }

            int col = tile.chunkX() - data.topLeftChunkX();
            int row = tile.chunkZ() - data.topLeftChunkZ();
            if (col < 0 || col >= data.mapWidth() || row < 0 || row >= data.mapHeight()) {
                continue;
            }

            int baseX = col * 16;
            int baseY = row * 16;
            int[] px = tile.pixels();

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int color = px[z * 16 + x];
                    try {
                        setPixel.invoke(atlasImage, baseX + x, baseY + z, color);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        atlasTexture = new DynamicTexture(() -> "chunkloader_map_atlas", atlasImage);
        trySetFilter(atlasTexture, false, false);
        atlasTextureId = registerDynamicTexture(Minecraft.getInstance().getTextureManager(), atlasTexture);
        tryUpload(atlasTexture);
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (atlasTextureId == null) return;
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        int texW = data.mapWidth() * 16;
        int texH = data.mapHeight() * 16;
        if (texW <= 0 || texH <= 0) {
            return;
        }

        int gpuW = texW;
        int gpuH = texH;
        try {
            var tm = mc.getTextureManager();
            var at = tm.getTexture(atlasTextureId);
            if (at != null && at.getTextureView() != null) {
                gpuW = at.getTextureView().getWidth(0);
                gpuH = at.getTextureView().getHeight(0);
            }
        } catch (Throwable ignored) {
        }

        blitNonAtlas(graphics, atlasTextureId, x, y, 0f, 0f, texW, texH, width, height, gpuW, gpuH);
    }

    @Override
    public void close() {
        Minecraft mc = Minecraft.getInstance();
        if (atlasTextureId != null && mc != null) {
            mc.getTextureManager().release(atlasTextureId);
            atlasTextureId = null;
        }
        if (atlasTexture != null) {
            atlasTexture.close();
            atlasTexture = null;
        }
        atlasImage = null;
    }

    private static Method resolveNativeImageSetter() {
        Method m = findMethod(NativeImage.class, "setPixelRGBA", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(NativeImage.class, "setPixelRGBAUnsafe", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(NativeImage.class, "setPixelABGR", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(NativeImage.class, "setPixel", int.class, int.class, int.class);
        if (m != null) return m;
        throw new IllegalStateException("No usable NativeImage pixel setter found");
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            try {
                Method m = clazz.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored2) {
                return null;
            }
        }
    }

    private static void tryUpload(DynamicTexture tex) {
        if (tex == null) return;
        try {
            Method upload = findMethod(DynamicTexture.class, "upload");
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "upload");
            }
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "uploadTexture");
            }
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "uploadIfDirty");
            }
            if (upload != null) {
                upload.invoke(tex);
            }
        } catch (Exception ignored) {
        }
    }

    private static Method findDeclaredNoArg(Class<?> clazz, String name) {
        try {
            Method m = clazz.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void trySetFilter(DynamicTexture tex, boolean blur, boolean mipmap) {
        if (tex == null) return;
        try {
            Method m = findMethod(tex.getClass(), "setFilter", boolean.class, boolean.class);
            if (m == null) {
                m = tex.getClass().getDeclaredMethod("setFilter", boolean.class, boolean.class);
                m.setAccessible(true);
            }
            m.invoke(tex, blur, mipmap);
        } catch (Exception ignored) {
        }
    }

    private static ResourceLocation registerDynamicTexture(TextureManager tm, DynamicTexture tex) {
        String key = "chunkloader_map_atlas/" + System.nanoTime();

        try {
            Method m = findMethod(tm.getClass(), "register", String.class, tex.getClass());
            if (m == null) {
                Class<?> abstractTexture = Class.forName("net.minecraft.client.renderer.texture.AbstractTexture");
                m = findMethod(tm.getClass(), "register", String.class, abstractTexture);
            }
            if (m == null) {
                m = tm.getClass().getDeclaredMethod("register", String.class, Object.class);
                m.setAccessible(true);
            }
            if (m != null && ResourceLocation.class.isAssignableFrom(m.getReturnType())) {
                Object id = m.invoke(tm, key, tex);
                if (id instanceof ResourceLocation rl) {
                    return rl;
                }
            }
        } catch (Throwable ignored) {
        }

        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("chunkloader", "chunkmap/atlas_" + System.nanoTime());
        try {
            tm.register(rl, tex);
        } catch (Throwable ignored) {
        }
        return rl;
    }

    private static void blitNonAtlas(
        GuiGraphics g,
        ResourceLocation texture,
        int x,
        int y,
        float u,
        float v,
        int uWidth,
        int vHeight,
        int width,
        int height,
        int textureWidth,
        int textureHeight
    ) {
        try {
            try {
                var tm = Minecraft.getInstance().getTextureManager();
                var at = tm.getTexture(texture);
                if (at != null && at.getTextureView() != null) {
                    RenderSystem.setShaderTexture(0, at.getTextureView());
                }
            } catch (Throwable ignored) {}
            
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, 0xFFFFFFFF);
        } catch (Throwable t) {
            g.blit(texture, x, y, (int) u, (int) v, width, height, textureWidth, textureHeight);
        }
    }
}
