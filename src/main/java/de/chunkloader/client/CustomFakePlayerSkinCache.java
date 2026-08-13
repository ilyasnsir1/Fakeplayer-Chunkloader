package de.chunkloader.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import de.chunkloader.client.SkinModelType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CustomFakePlayerSkinCache {
    private static final Map<String, String> skinPathByPlayerName = new HashMap<>();
    private static final Map<String, Integer> layerMaskByPlayerName = new HashMap<>();
    private static final Map<UUID, CustomSkin> skinByPlayerUuid = new HashMap<>();
    private static final Map<UUID, String> previewPathByPlayerUuid = new HashMap<>();
    private static final Map<UUID, String> playerNameByUuid = new HashMap<>();
    private static final Map<String, CustomSkin> skinByPath = new HashMap<>();
    private static final Map<String, DynamicTexture> dynamicTextureByPath = new HashMap<>();
    private static final Set<String> failedPaths = new HashSet<>();

    private CustomFakePlayerSkinCache() {
    }

    public record CustomSkin(ResourceLocation assetId, ResourceLocation textureId, SkinModelType model) {
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
        invalidateCachedSkin(normalizedPath);
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
        Path dir = syncedSkinsDirectory();
        if (dir == null) {
            throw new IOException("Client unavailable");
        }
        Files.createDirectories(dir);
        Path file = dir.resolve(safeSyncedFileName(normalizedName) + ".png");
        String normalizedPath = normalizeSkinPath(file);
        boolean samePixels = Files.isRegularFile(file)
            && skinByPath.containsKey(normalizedPath)
            && java.util.Arrays.equals(Files.readAllBytes(file), pngBytes);
        Files.write(file, pngBytes);
        if (samePixels) {
            skinPathByPlayerName.put(normalizedName, normalizedPath);
            layerMaskByPlayerName.put(normalizedName, SkinLayerMask.sanitize(layerMask));
            failedPaths.remove(normalizedPath);
            refreshBindings();
            return;
        }
        setSkin(normalizedName, file, layerMask);
    }

    private static Path syncedSkinsDirectory() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameDirectory == null) {
            return null;
        }
        return client.gameDirectory.toPath().resolve("chunkloader").resolve("synced_skins");
    }

    private static void deleteSyncedSkinFile(String normalizedName) {
        Path dir = syncedSkinsDirectory();
        if (dir == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve(safeSyncedFileName(normalizedName) + ".png"));
        } catch (IOException ignored) {
        }
    }

    private static void clearSyncedSkinsDirectory() {
        Path dir = syncedSkinsDirectory();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
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
        String normalizedName = normalizePlayerName(playerName);
        removeSkin(normalizedName);
        deleteSyncedSkinFile(normalizedName);
    }

    public static void clearAllSkins() {
        for (String path : new HashSet<>(skinByPath.keySet())) {
            releaseIfUnused(path);
        }
        skinPathByPlayerName.clear();
        layerMaskByPlayerName.clear();
        previewPathByPlayerUuid.clear();
        clearRuntimeBindings();
        failedPaths.clear();
        clearSyncedSkinsDirectory();
    }

    public static boolean hasSkin(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return skinPathByPlayerName.containsKey(playerName.trim().toLowerCase(Locale.ROOT));
    }

    public static String getSkinPath(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        return skinPathByPlayerName.get(playerName.trim().toLowerCase(Locale.ROOT));
    }

    public static int getLayerMaskForPlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return SkinLayerMask.DEFAULT_MASK;
        }
        Integer mask = layerMaskByPlayerName.get(playerName.trim().toLowerCase(Locale.ROOT));
        return mask == null ? SkinLayerMask.DEFAULT_MASK : SkinLayerMask.sanitize(mask);
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

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        if (client.level != null) {
            for (var player : client.level.players()) {
                var profile = player.getGameProfile();
                if (profile != null) {
                    String plainName = FakePlayerNameCache.getPlainName(player);
                    bindPlayer(profile.id(), plainName != null ? plainName : profile.name());
                }
            }
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return;
        }

        for (var playerInfo : connection.getOnlinePlayers()) {
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
        ResourceLocation assetId = ResourceLocation.fromNamespaceAndPath(
            ChunkloaderForgeMod.MODID,
            "custom_skin/" + Integer.toUnsignedString(normalizedPath.hashCode(), 36)
        );
        ResourceLocation textureId = assetId.withPath(path -> "textures/" + path + ".png");
        DynamicTexture texture = new DynamicTexture(() -> "chunkloader_custom_skin", skinImage);
        try {
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        } catch (RuntimeException e) {
            texture.close();
            throw e;
        }
        CustomSkin skin = new CustomSkin(assetId, textureId, model);
        skinByPath.put(normalizedPath, skin);
        dynamicTextureByPath.put(normalizedPath, texture);
        return skin;
    }

    
    public static boolean isSyncedSkinPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path syncedDir = syncedSkinsDirectory();
        if (syncedDir == null) {
            return false;
        }
        try {
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            return candidate.startsWith(syncedDir.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    
    private static volatile String cachedUserPicturesDirectory;

    public static String getUserPicturesDirectory() {
        String cached = cachedUserPicturesDirectory;
        if (cached != null) {
            return cached;
        }
        String resolved = resolveUserPicturesDirectory();
        if (resolved != null) {
            cachedUserPicturesDirectory = resolved;
        }
        return resolved;
    }

    private static String resolveUserPicturesDirectory() {
        
        String[] candidates = {
            System.getenv("USERPROFILE") != null ? System.getenv("USERPROFILE") + java.io.File.separator + "Pictures" : null,
            System.getProperty("user.home") != null ? System.getProperty("user.home") + java.io.File.separator + "Pictures" : null,
            System.getProperty("user.home") != null ? System.getProperty("user.home") + java.io.File.separator + "Bilder" : null
        };
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                Path path = Path.of(candidate).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    return path.toString();
                }
            } catch (Exception ignored) {
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            String fromShell = queryWindowsShellMyPictures();
            if (fromShell != null) {
                return fromShell;
            }
        }
        if (System.getProperty("user.home") != null) {
            return System.getProperty("user.home");
        }
        return System.getProperty("user.dir", ".");
    }

    private static String queryWindowsShellMyPictures() {
        ProcessBuilder builder = new ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders",
            "/v", "My Pictures"
        );
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
            )) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || output == null || output.isBlank()) {
                return null;
            }
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                int idx = trimmed.toUpperCase(java.util.Locale.ROOT).indexOf("REG_SZ");
                if (idx < 0) {
                    continue;
                }
                String value = trimmed.substring(idx + "REG_SZ".length()).trim();
                if (value.isEmpty()) {
                    continue;
                }
                Path path = Path.of(value).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    return path.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void invalidateCachedSkin(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return;
        }
        CustomSkin skin = skinByPath.remove(normalizedPath);
        DynamicTexture texture = dynamicTextureByPath.remove(normalizedPath);
        if (skin != null) {
            skinByPlayerUuid.entrySet().removeIf(entry -> entry.getValue() == skin);
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getTextureManager().release(skin.textureId());
            }
        } else if (texture != null) {
            texture.close();
        }
        failedPaths.remove(normalizedPath);
    }

    private static void releaseIfUnused(String normalizedPath) {
        if (skinPathByPlayerName.containsValue(normalizedPath) || previewPathByPlayerUuid.containsValue(normalizedPath)) {
            return;
        }

        CustomSkin skin = skinByPath.remove(normalizedPath);
        DynamicTexture texture = dynamicTextureByPath.remove(normalizedPath);
        Minecraft client = Minecraft.getInstance();
        if (skin != null && client != null) {
            client.getTextureManager().release(skin.textureId());
        } else if (texture != null) {
            texture.close();
        }
    }

    private static SkinModelType detectModel(NativeImage skinImage) {
        if (skinImage.getHeight() == 32) {
            return SkinModelType.WIDE;
        }
        return ARGB.alpha(skinImage.getPixel(54, 20)) == 0 ? SkinModelType.SLIM : SkinModelType.WIDE;
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
                if (ARGB.alpha(skinImage.getPixel(x, y)) < 128) {
                    return;
                }
            }
        }

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                skinImage.setPixel(x, y, skinImage.getPixel(x, y) & 0x00FFFFFF);
            }
        }
    }

    private static void setNoAlpha(NativeImage skinImage, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                skinImage.setPixel(x, y, ARGB.opaque(skinImage.getPixel(x, y)));
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
