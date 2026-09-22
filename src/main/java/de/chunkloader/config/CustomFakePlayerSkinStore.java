package de.chunkloader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CustomFakePlayerSkinStore {
    public static final int MAX_PNG_BYTES = 128 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String META_FILE = "skins.json";
    private static final String SKINS_SUBFOLDER = "skins";
    private static final int EDITABLE_LAYER_MASK = 0x7E;

    private final Path rootDir;
    private final Path skinsDir;
    private final Path metaPath;
    private final Map<String, StoredSkin> skinsByName = new LinkedHashMap<>();

    public record StoredSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
    }

    public CustomFakePlayerSkinStore(MinecraftServer server) {
        this.rootDir = ChunkloaderPaths.getChunkloaderDir(server);
        this.skinsDir = rootDir.resolve(SKINS_SUBFOLDER);
        this.metaPath = rootDir.resolve(META_FILE);
    }

    public synchronized void load() {
        skinsByName.clear();
        try {
            Files.createDirectories(skinsDir);
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.warn("Unable to create custom skin directory {}: {}", skinsDir, e.getMessage());
            return;
        }
        if (!Files.isRegularFile(metaPath)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(metaPath)).getAsJsonObject();
            JsonObject entries = root.has("skins") && root.get("skins").isJsonObject()
                ? root.getAsJsonObject("skins")
                : root;
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                String name = normalizeName(entry.getKey());
                if (name == null) {
                    continue;
                }
                JsonObject meta = entry.getValue().getAsJsonObject();
                int layerMask = sanitizeLayerMask(meta.has("layerMask") ? meta.get("layerMask").getAsInt() : EDITABLE_LAYER_MASK);
                String model = meta.has("model") ? meta.get("model").getAsString() : "";
                Path pngPath = skinsDir.resolve(safeFileName(name) + ".png");
                if (!Files.isRegularFile(pngPath)) {
                    continue;
                }
                byte[] pngBytes = Files.readAllBytes(pngPath);
                if (!isValidSkinPng(pngBytes)) {
                    ChunkloaderMod.LOGGER.warn("Skipping invalid stored skin PNG for '{}'", name);
                    continue;
                }
                skinsByName.put(name, new StoredSkin(name, layerMask, model != null ? model : "", pngBytes));
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn("Failed to load custom skins meta {}: {}", metaPath, e.getMessage());
        }
    }

    public synchronized Map<String, StoredSkin> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skinsByName));
    }

    public synchronized StoredSkin get(String playerName) {
        String name = normalizeName(playerName);
        if (name == null) {
            return null;
        }
        return skinsByName.get(name);
    }

    public synchronized StoredSkin put(String playerName, byte[] pngBytes, int layerMask, String model) throws IOException {
        String name = normalizeName(playerName);
        if (name == null) {
            throw new IOException("Invalid player name");
        }
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IOException("Empty skin data");
        }
        if (pngBytes.length > MAX_PNG_BYTES) {
            throw new IOException("Skin file too large (max " + MAX_PNG_BYTES + " bytes)");
        }
        if (!isValidSkinPng(pngBytes)) {
            throw new IOException("Skin must be a PNG of 64x64 or 64x32 pixels");
        }

        Files.createDirectories(skinsDir);
        Path pngPath = skinsDir.resolve(safeFileName(name) + ".png");
        try (OutputStream out = Files.newOutputStream(pngPath)) {
            out.write(pngBytes);
        }

        StoredSkin stored = new StoredSkin(name, sanitizeLayerMask(layerMask), model != null ? model : "", pngBytes);
        skinsByName.put(name, stored);
        saveMeta();
        return stored;
    }

    public synchronized boolean remove(String playerName) {
        String name = normalizeName(playerName);
        if (name == null) {
            return false;
        }
        StoredSkin removed = skinsByName.remove(name);
        if (removed == null) {
            return false;
        }
        try {
            Files.deleteIfExists(skinsDir.resolve(safeFileName(name) + ".png"));
            saveMeta();
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.warn("Failed to delete custom skin file for '{}': {}", name, e.getMessage());
        }
        return true;
    }

    public synchronized StoredSkin rename(String oldPlayerName, String newPlayerName) {
        String oldName = normalizeName(oldPlayerName);
        String newName = normalizeName(newPlayerName);
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return null;
        }
        StoredSkin existing = skinsByName.remove(oldName);
        if (existing == null) {
            return null;
        }
        Path oldPath = skinsDir.resolve(safeFileName(oldName) + ".png");
        Path newPath = skinsDir.resolve(safeFileName(newName) + ".png");
        try {
            Files.createDirectories(skinsDir);
            if (Files.isRegularFile(oldPath)) {
                Files.move(oldPath, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.write(newPath, existing.pngBytes());
            }
            Files.deleteIfExists(oldPath);
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.warn("Failed to rename custom skin '{}' -> '{}': {}", oldName, newName, e.getMessage());
            skinsByName.put(oldName, existing);
            return null;
        }
        StoredSkin renamed = new StoredSkin(newName, existing.layerMask(), existing.model(), existing.pngBytes());
        skinsByName.put(newName, renamed);
        try {
            saveMeta();
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.warn("Failed to save skins meta after rename: {}", e.getMessage());
        }
        return renamed;
    }

    private void saveMeta() throws IOException {
        Files.createDirectories(rootDir);
        JsonObject root = new JsonObject();
        JsonObject skins = new JsonObject();
        for (StoredSkin skin : skinsByName.values()) {
            JsonObject meta = new JsonObject();
            meta.addProperty("layerMask", skin.layerMask());
            meta.addProperty("model", skin.model() != null ? skin.model() : "");
            skins.add(skin.playerName(), meta);
        }
        root.add("skins", skins);
        Path tmp = metaPath.resolveSibling(META_FILE + ".tmp");
        Files.writeString(tmp, GSON.toJson(root));
        try {
            Files.move(tmp, metaPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, metaPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static int sanitizeLayerMask(int mask) {
        return mask & EDITABLE_LAYER_MASK;
    }

    public static String normalizeName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    public static String safeFileName(String normalizedName) {
        StringBuilder sb = new StringBuilder(normalizedName.length());
        for (int i = 0; i < normalizedName.length(); i++) {
            char c = normalizedName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.isEmpty()) {
            sb.append("skin");
        }
        return sb.toString();
    }

    public static boolean isValidSkinPng(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length < 24) {
            return false;
        }

        byte[] sig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < sig.length; i++) {
            if (pngBytes[i] != sig[i]) {
                return false;
            }
        }

        ByteBuffer buf = ByteBuffer.wrap(pngBytes, 8, 16).order(ByteOrder.BIG_ENDIAN);
        int length = buf.getInt();
        if (length < 13) {
            return false;
        }
        int type = buf.getInt();
        if (type != 0x49484452) {
            return false;
        }
        int width = buf.getInt();
        int height = buf.getInt();
        return width == 64 && (height == 64 || height == 32);
    }

    public static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        Objects.requireNonNull(in, "in");
        byte[] buffer = in.readNBytes(maxBytes + 1);
        if (buffer.length > maxBytes) {
            throw new IOException("Skin file too large (max " + maxBytes + " bytes)");
        }
        return buffer;
    }
}
