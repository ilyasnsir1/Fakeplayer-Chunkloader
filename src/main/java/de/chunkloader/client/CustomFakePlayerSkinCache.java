package de.chunkloader.client;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.client.config.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class CustomFakePlayerSkinCache {
    private static final Map<String, String> skinPathByPlayerName = new HashMap<>();
    private static final Map<String, Integer> layerMaskByPlayerName = new HashMap<>();
    private static final Map<UUID, CustomSkin> skinByPlayerUuid = new HashMap<>();
    private static final Map<UUID, String> previewPathByPlayerUuid = new HashMap<>();
    private static final Map<UUID, String> playerNameByUuid = new HashMap<>();
    private static final Map<String, CustomSkin> skinByPath = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> dynamicTextureByPath = new HashMap<>();
    private static final Set<String> failedPaths = new HashSet<>();

    private CustomFakePlayerSkinCache() {
    }

    public record CustomSkin(Identifier assetId, Identifier textureId, SkinModelType model) {
    }

    public static void loadConfiguredSkins(ClientConfig config) {
        skinPathByPlayerName.clear();
        skinPathByPlayerName.putAll(config.getCustomSkinPathsByPlayerName());
        layerMaskByPlayerName.clear();
        layerMaskByPlayerName.putAll(config.getCustomSkinLayersByPlayerName());
        clearRuntimeBindings();
        refreshBindings();
    }

    public static CustomSkin setPreviewSkin(UUID previewPlayerUuid, Path skinPath) throws IOException {
        if (previewPlayerUuid == null) {
            throw new IllegalArgumentException("Preview player UUID must not be null");
        }

        String normalizedPath = normalizeSkinPath(skinPath);
        CustomSkin skin = loadSkin(normalizedPath);
        String previousPath = previewPathByPlayerUuid.put(previewPlayerUuid, normalizedPath);
        failedPaths.remove(normalizedPath);

        if (previousPath != null && !previousPath.equals(normalizedPath)) {
            releaseIfUnused(previousPath);
        }
        return skin;
    }

    public static void clearPreviewSkin(UUID previewPlayerUuid) {
        if (previewPlayerUuid == null) {
            return;
        }

        String removedPath = previewPathByPlayerUuid.remove(previewPlayerUuid);
        if (removedPath != null) {
            releaseIfUnused(removedPath);
        }
    }

    public static void setSkin(String playerName, Path skinPath) throws IOException {
        setSkin(playerName, skinPath, SkinLayerMask.DEFAULT_MASK);
    }

    public static void setSkin(String playerName, Path skinPath, int layerMask) throws IOException {
        String normalizedName = normalizePlayerName(playerName);
        String normalizedPath = normalizeSkinPath(skinPath);
        loadSkin(normalizedPath);
        skinPathByPlayerName.put(normalizedName, normalizedPath);
        layerMaskByPlayerName.put(normalizedName, SkinLayerMask.sanitize(layerMask));
        failedPaths.remove(normalizedPath);
        refreshBindings();
    }

    public static void applySyncedSkin(String playerName, byte[] pngBytes, int layerMask) throws IOException {
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IOException("Empty skin data");
        }
        String normalizedName = normalizePlayerName(playerName);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            throw new IOException("Client unavailable");
        }
        Path dir = client.runDirectory.toPath().resolve("chunkloader").resolve("synced_skins");
        Files.createDirectories(dir);
        Path file = dir.resolve(safeSyncedFileName(normalizedName) + ".png");
        Files.write(file, pngBytes);

        setSkin(normalizedName, file, layerMask);
        ClientConfig.load().setCustomSkinPath(normalizedName, file.toString());
        ClientConfig.load().setCustomSkinLayers(normalizedName, layerMask);
    }

    private static String safeSyncedFileName(String normalizedName) {
        StringBuilder sb = new StringBuilder(normalizedName.length());
        for (int i = 0; i < normalizedName.length(); i++) {
            char c = normalizedName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "skin" : sb.toString();
    }

    public static void setLayerMask(String playerName, int layerMask) {
        String normalizedName = normalizePlayerName(playerName);
        layerMaskByPlayerName.put(normalizedName, SkinLayerMask.sanitize(layerMask));
    }

    public static void removeSkin(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        String normalizedName = playerName.trim().toLowerCase(Locale.ROOT);
        String removedPath = skinPathByPlayerName.remove(normalizedName);
        layerMaskByPlayerName.remove(normalizedName);

        skinByPlayerUuid.entrySet().removeIf(entry -> {
            String assignedName = playerNameByUuid.get(entry.getKey());
            return normalizedName.equals(assignedName);
        });
        playerNameByUuid.entrySet().removeIf(entry -> normalizedName.equals(entry.getValue()));
        if (removedPath != null && !skinPathByPlayerName.containsValue(removedPath)) {
            releaseIfUnused(removedPath);
        }
    }

    public static void clearPersistedSkin(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        removeSkin(playerName);

        ClientConfig.load().setCustomSkinPath(playerName, null);
    }

    public static CustomSkin getSkin(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return skinByPlayerUuid.get(playerUuid);
    }

    public static Integer getLayerMask(UUID playerUuid) {
        if (playerUuid == null || !skinByPlayerUuid.containsKey(playerUuid)) {
            return null;
        }
        String playerName = playerNameByUuid.get(playerUuid);
        if (playerName == null) {
            return SkinLayerMask.DEFAULT_MASK;
        }
        Integer mask = layerMaskByPlayerName.get(playerName);
        return mask == null ? SkinLayerMask.DEFAULT_MASK : SkinLayerMask.sanitize(mask);
    }

    public static void refreshBindings() {

        skinByPlayerUuid.entrySet().removeIf(entry -> {
            String assignedName = playerNameByUuid.get(entry.getKey());
            return assignedName == null || !skinPathByPlayerName.containsKey(assignedName);
        });
        playerNameByUuid.entrySet().removeIf(entry -> !skinPathByPlayerName.containsKey(entry.getValue()));

        if (skinPathByPlayerName.isEmpty()) {
            clearRuntimeBindings();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        if (client.world != null) {
            for (var player : client.world.getPlayers()) {
                var profile = player.getGameProfile();
                if (profile != null) {
                    bindPlayer(profile.id(), profile.name());
                }
            }
        }

        ClientPlayNetworkHandler connection = client.getNetworkHandler();
        if (connection == null) {
            return;
        }

        for (var playerInfo : connection.getPlayerList()) {
            var profile = playerInfo.getProfile();
            if (profile != null) {
                bindPlayer(profile.id(), profile.name());
            }
        }
    }

    private static void bindPlayer(UUID playerUuid, String playerName) {
        if (playerUuid == null || playerName == null || playerName.isBlank()) {
            return;
        }

        String normalizedName = playerName.trim().toLowerCase(Locale.ROOT);
        String path = skinPathByPlayerName.get(normalizedName);
        if (path == null) {
            skinByPlayerUuid.remove(playerUuid);
            playerNameByUuid.remove(playerUuid);
            return;
        }

        try {
            skinByPlayerUuid.put(playerUuid, loadSkin(path));
            playerNameByUuid.put(playerUuid, normalizedName);
        } catch (IOException e) {
            skinByPlayerUuid.remove(playerUuid);
            playerNameByUuid.remove(playerUuid);
            if (failedPaths.add(path)) {
                org.slf4j.LoggerFactory.getLogger("chunkloader").warn("Unable to load custom skin '{}': {}", path, e.getMessage());
            }
        }
    }

    public static void clearRuntimeBindings() {
        skinByPlayerUuid.clear();
        playerNameByUuid.clear();
    }

    private static CustomSkin loadSkin(String normalizedPath) throws IOException {
        CustomSkin cachedSkin = skinByPath.get(normalizedPath);
        if (cachedSkin != null) {
            return cachedSkin;
        }

        Path skinPath = Path.of(normalizedPath);
        if (!Files.isRegularFile(skinPath)) {
            throw new IOException("File does not exist");
        }

        NativeImage skinImage;
        try (InputStream input = Files.newInputStream(skinPath)) {
            skinImage = NativeImage.read(input);
        }

        if (skinImage.getWidth() != 64 || (skinImage.getHeight() != 64 && skinImage.getHeight() != 32)) {
            skinImage.close();
            throw new IOException("Skin must be 64x64 or 64x32 pixels");
        }

        SkinModelType model = detectModel(skinImage);
        skinImage = normalizeSkinImage(skinImage);
        Identifier assetId = Identifier.of(
            ChunkloaderMod.MOD_ID,
            "custom_skin/" + Integer.toUnsignedString(normalizedPath.hashCode(), 36)
        );
        Identifier textureId = assetId.withPath(path -> "textures/" + path + ".png");
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "chunkloader_custom_skin", skinImage);
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
        } catch (RuntimeException e) {
            texture.close();
            throw e;
        }
        CustomSkin skin = new CustomSkin(assetId, textureId, model);
        skinByPath.put(normalizedPath, skin);
        dynamicTextureByPath.put(normalizedPath, texture);
        return skin;
    }

    private static void releaseIfUnused(String normalizedPath) {
        if (skinPathByPlayerName.containsValue(normalizedPath) || previewPathByPlayerUuid.containsValue(normalizedPath)) {
            return;
        }

        CustomSkin skin = skinByPath.remove(normalizedPath);
        NativeImageBackedTexture texture = dynamicTextureByPath.remove(normalizedPath);
        MinecraftClient client = MinecraftClient.getInstance();
        if (skin != null && client != null) {
            client.getTextureManager().destroyTexture(skin.textureId());
        } else if (texture != null) {
            texture.close();
        }
    }

    private static SkinModelType detectModel(NativeImage skinImage) {
        if (skinImage.getHeight() == 32) {
            return SkinModelType.WIDE;
        }
        return skinImage.getOpacity(54, 20) == 0 ? SkinModelType.SLIM : SkinModelType.WIDE;
    }

    private static NativeImage normalizeSkinImage(NativeImage skinImage) {
        boolean legacySkin = skinImage.getHeight() == 32;
        if (legacySkin) {
            NativeImage convertedSkin = new NativeImage(64, 64, true);
            convertedSkin.copyFrom(skinImage);
            skinImage.close();
            skinImage = convertedSkin;
            skinImage.fillRect(0, 32, 64, 32, 0);
            skinImage.copyRect(4, 16, 16, 32, 4, 4, true, false);
            skinImage.copyRect(8, 16, 16, 32, 4, 4, true, false);
            skinImage.copyRect(0, 20, 24, 32, 4, 12, true, false);
            skinImage.copyRect(4, 20, 16, 32, 4, 12, true, false);
            skinImage.copyRect(8, 20, 8, 32, 4, 12, true, false);
            skinImage.copyRect(12, 20, 16, 32, 4, 12, true, false);
            skinImage.copyRect(44, 16, -8, 32, 4, 4, true, false);
            skinImage.copyRect(48, 16, -8, 32, 4, 4, true, false);
            skinImage.copyRect(40, 20, 0, 32, 4, 12, true, false);
            skinImage.copyRect(44, 20, -8, 32, 4, 12, true, false);
            skinImage.copyRect(48, 20, -16, 32, 4, 12, true, false);
            skinImage.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        setNoAlpha(skinImage, 0, 0, 32, 16);
        if (legacySkin) {
            applyLegacyTransparencyFix(skinImage, 32, 0, 64, 32);
        }
        setNoAlpha(skinImage, 0, 16, 64, 32);
        setNoAlpha(skinImage, 16, 48, 48, 64);
        return skinImage;
    }

    private static void applyLegacyTransparencyFix(NativeImage skinImage, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if (skinImage.getOpacity(x, y) < 128) {
                    return;
                }
            }
        }

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int color = skinImage.getColorArgb(x, y);
                skinImage.setColorArgb(x, y, color & 0x00FFFFFF);
            }
        }
    }

    private static void setNoAlpha(NativeImage skinImage, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int color = skinImage.getColorArgb(x, y);
                skinImage.setColorArgb(x, y, 0xFF000000 | (color & 0x00FFFFFF));
            }
        }
    }

    private static String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSkinPath(Path skinPath) {
        if (skinPath == null) {
            throw new IllegalArgumentException("Skin path must not be null");
        }
        return skinPath.toAbsolutePath().normalize().toString();
    }
}
