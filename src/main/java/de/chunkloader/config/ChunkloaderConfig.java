package de.chunkloader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import de.chunkloader.ChunkloaderConstants;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChunkloaderConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BACKUPS_SUBFOLDER = "backups";
    private static final String CONFIG_FILE = "chunkloader_config.json";
    private static final String BACKUP_PREFIX = "chunkloader_config_backup_";
    private static final int MAX_BACKUPS = 2;
    private static final String LATEST_BACKUP_NAME = BACKUP_PREFIX + "latest.json";
    private static final long BACKUP_COOLDOWN_MS = Long.getLong("chunkloader.backupCooldownMs", 5L * 60L * 1000L);
    private static final long SAVE_DEBOUNCE_MS = Long.getLong("chunkloader.saveDebounceMs", 3000L);
    private static final int CONFIG_VERSION = 2;

    private final List<ChunkloaderTarget> chunkEntries = new ArrayList<>();
    private volatile List<ChunkloaderTarget> cachedChunkEntries = null;
    private volatile long cacheVersion = 0;
    private volatile long cachedVersion = -1;

    private boolean tabListVisibleAll = true;
    private final Path configPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long lastBackupTimeMs = 0L;
    private final Object saveScheduleLock = new Object();
    private Timer saveTimer;
    private TimerTask pendingSaveTask;
    private volatile boolean savePending = false;

    public Path getConfigPath() {
        return configPath;
    }

    public ChunkloaderConfig(MinecraftServer server) {
        this.configPath = ChunkloaderPaths.getChunkloaderDir(server).resolve(CONFIG_FILE);
    }

    public static ChunkloaderConfig load(MinecraftServer server) {
        ChunkloaderConfig config = new ChunkloaderConfig(server);
        File configFile = config.configPath.toFile();
        migrateFromLegacyPathIfNeeded(config);

        if (configFile.exists()) {
            boolean needsSave = false;
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                int configVersion = getConfigVersion(json);
                if (configVersion != CONFIG_VERSION) {
                    migrateConfig(json, configVersion);
                    needsSave = true;
                }

                if (json.has("tabListVisibleAll")) {
                    config.tabListVisibleAll = json.get("tabListVisibleAll").getAsBoolean();
                }

                if (json.has("chunkloaders")) {
                    JsonArray chunkloaders = json.getAsJsonArray("chunkloaders");
                    for (JsonElement element : chunkloaders) {
                        try {
                            JsonObject chunkObj = element.getAsJsonObject();
                            int chunkX = chunkObj.get("x").getAsInt();
                            int chunkZ = chunkObj.get("z").getAsInt();
                            int blockX = chunkObj.has("blockX") ? chunkObj.get("blockX").getAsInt()
                                    : chunkX * ChunkloaderConstants.CHUNK_SIZE
                                            + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                            int blockY = chunkObj.has("blockY") ? chunkObj.get("blockY").getAsInt()
                                    : ChunkloaderConstants.DEFAULT_BLOCK_Y;
                            int blockZ = chunkObj.has("blockZ") ? chunkObj.get("blockZ").getAsInt()
                                    : chunkZ * ChunkloaderConstants.CHUNK_SIZE
                                            + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                            String name = chunkObj.has("name") ? chunkObj.get("name").getAsString() : null;
                            boolean enabled = chunkObj.has("enabled") ? chunkObj.get("enabled").getAsBoolean() : true;
                            boolean nameVisible = chunkObj.has("nameVisible")
                                    ? chunkObj.get("nameVisible").getAsBoolean()
                                    : true;
                            int chunkRadius = chunkObj.has("chunkRadius") ? chunkObj.get("chunkRadius").getAsInt()
                                    : ChunkloaderConstants.DEFAULT_RADIUS;
                            boolean allowMobSpawning = chunkObj.has("allowMobSpawning")
                                    ? chunkObj.get("allowMobSpawning").getAsBoolean()
                                    : true;
                            String dimension = chunkObj.has("dimension") ? chunkObj.get("dimension").getAsString()
                                    : "minecraft:overworld";
                            String ownerName = chunkObj.has("ownerName") ? chunkObj.get("ownerName").getAsString()
                                    : null;
                            Integer easterEggSkinIndex = EasterEggSkinGuard.readVerifiedIndex(
                                    chunkObj, dimension, chunkX, chunkZ);
                            float spawnYaw = chunkObj.has("spawnYaw") ? chunkObj.get("spawnYaw").getAsFloat() : 0.0f;

                            chunkRadius = Math.max(ChunkloaderConstants.MIN_RADIUS,
                                    Math.min(ChunkloaderConstants.MAX_RADIUS, chunkRadius));
                            blockY = Math.max(ChunkloaderConstants.MIN_BLOCK_Y,
                                    Math.min(ChunkloaderConstants.MAX_BLOCK_Y, blockY));

                            if (name != null && config.hasEntryByName(name)) {

                                continue;
                            }

                            config.chunkEntries.add(new ChunkloaderTarget(chunkX, chunkZ, blockX, blockY, blockZ, name,
                                    enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, ownerName,
                                    easterEggSkinIndex, spawnYaw));
                        } catch (Exception e) {

                        }
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Failed to load config file", e);
                boolean restored = config.restoreFromBackup();
                if (restored) {
                    try {
                        try (FileReader restoredReader = new FileReader(configFile)) {
                            JsonObject restoredJson = JsonParser.parseReader(restoredReader).getAsJsonObject();
                            config.chunkEntries.clear();
                            if (restoredJson.has("tabListVisibleAll")) {
                                config.tabListVisibleAll = restoredJson.get("tabListVisibleAll").getAsBoolean();
                            }
                            if (restoredJson.has("chunkloaders")) {
                                JsonArray chunkloaders = restoredJson.getAsJsonArray("chunkloaders");
                                for (JsonElement element : chunkloaders) {
                                    try {
                                        JsonObject chunkObj = element.getAsJsonObject();
                                        int chunkX = chunkObj.get("x").getAsInt();
                                        int chunkZ = chunkObj.get("z").getAsInt();
                                        int blockX = chunkObj.has("blockX") ? chunkObj.get("blockX").getAsInt()
                                                : chunkX * ChunkloaderConstants.CHUNK_SIZE
                                                        + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                                        int blockY = chunkObj.has("blockY") ? chunkObj.get("blockY").getAsInt()
                                                : ChunkloaderConstants.DEFAULT_BLOCK_Y;
                                        int blockZ = chunkObj.has("blockZ") ? chunkObj.get("blockZ").getAsInt()
                                                : chunkZ * ChunkloaderConstants.CHUNK_SIZE
                                                        + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                                        String name = chunkObj.has("name") ? chunkObj.get("name").getAsString() : null;
                                        boolean enabled = chunkObj.has("enabled") ? chunkObj.get("enabled").getAsBoolean() : true;
                                        boolean nameVisible = chunkObj.has("nameVisible")
                                                ? chunkObj.get("nameVisible").getAsBoolean()
                                                : true;
                                        int chunkRadius = chunkObj.has("chunkRadius") ? chunkObj.get("chunkRadius").getAsInt()
                                                : ChunkloaderConstants.DEFAULT_RADIUS;
                                        boolean allowMobSpawning = chunkObj.has("allowMobSpawning")
                                                ? chunkObj.get("allowMobSpawning").getAsBoolean()
                                                : true;
                                        String dimension = chunkObj.has("dimension") ? chunkObj.get("dimension").getAsString()
                                                : "minecraft:overworld";
                                        String ownerName = chunkObj.has("ownerName") ? chunkObj.get("ownerName").getAsString()
                                                : null;
                                        Integer easterEggSkinIndex = EasterEggSkinGuard.readVerifiedIndex(
                                    chunkObj, dimension, chunkX, chunkZ);
                            float spawnYaw = chunkObj.has("spawnYaw") ? chunkObj.get("spawnYaw").getAsFloat() : 0.0f;

                                        chunkRadius = Math.max(ChunkloaderConstants.MIN_RADIUS,
                                                Math.min(ChunkloaderConstants.MAX_RADIUS, chunkRadius));
                                        blockY = Math.max(ChunkloaderConstants.MIN_BLOCK_Y,
                                                Math.min(ChunkloaderConstants.MAX_BLOCK_Y, blockY));

                                        if (name != null && config.hasEntryByName(name)) {
                                            continue;
                                        }

                                        config.chunkEntries.add(new ChunkloaderTarget(chunkX, chunkZ, blockX, blockY, blockZ, name,
                                                enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, ownerName,
                                                easterEggSkinIndex, spawnYaw));
                                    } catch (Exception entryEx) {
                                        LOGGER.warn("Failed to load restored chunkloader entry, skipping", entryEx);
                                    }
                                }
                            }
                            LOGGER.warn("Successfully reloaded config from backup ({} entries)", config.chunkEntries.size());
                        }
                    } catch (Exception reloadEx) {
                        LOGGER.error("CRITICAL: Config backup restore succeeded but reload failed. Saving empty default config.", reloadEx);
                        config.chunkEntries.clear();
                        try {
                            config.save();
                        } catch (Exception saveEx) {
                            LOGGER.error("Failed to create default config", saveEx);
                        }
                    }
                } else {
                    LOGGER.error("CRITICAL: Config load failed and no valid backup could be restored. Saving empty default config.");
                    try {
                        config.save();
                    } catch (Exception saveEx) {
                        LOGGER.error("Failed to create default config", saveEx);
                    }
                }
            }
            if (needsSave) {
                try {
                    config.save();
                    LOGGER.info("Config migrated to version {}", CONFIG_VERSION);
                } catch (Exception e) {
                    LOGGER.warn("Failed to persist migrated config", e);
                }
            }
        } else {
            try {
                config.save();
            } catch (Exception e) {
                LOGGER.error("Failed to create default config file", e);
            }
        }

        return config;
    }

    public void save() {
        invalidateCache();
        if (SAVE_DEBOUNCE_MS <= 0L) {
            saveImmediate();
            return;
        }
        scheduleSave();
    }

    public void flushPendingSave() {
        if (savePending) {
            saveImmediate();
        }
    }

    public void saveImmediate() {
        synchronized (saveScheduleLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel();
                pendingSaveTask = null;
            }
            savePending = false;
        }
        lock.writeLock().lock();
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                }
            }
            createBackup();

            JsonObject json = new JsonObject();
            JsonArray chunkloaders = new JsonArray();
            json.addProperty("configVersion", CONFIG_VERSION);
            json.addProperty("tabListVisibleAll", tabListVisibleAll);

            for (ChunkloaderTarget entry : chunkEntries) {
                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("x", entry.chunkX());
                chunkObj.addProperty("z", entry.chunkZ());
                chunkObj.addProperty("blockX", entry.blockX());
                chunkObj.addProperty("blockY", entry.blockY());
                chunkObj.addProperty("blockZ", entry.blockZ());
                if (entry.name() != null) {
                    chunkObj.addProperty("name", entry.name());
                }
                chunkObj.addProperty("enabled", entry.enabled());
                chunkObj.addProperty("nameVisible", entry.nameVisible());
                chunkObj.addProperty("chunkRadius", entry.chunkRadius());
                chunkObj.addProperty("allowMobSpawning", entry.allowMobSpawning());
                chunkObj.addProperty("dimension", entry.dimension());
                if (entry.ownerName() != null) {
                    chunkObj.addProperty("ownerName", entry.ownerName());
                }
                                if (entry.easterEggSkinIndex() != null) {
                    EasterEggSkinGuard.writeSignedIndex(
                            chunkObj,
                            entry.dimension(),
                            entry.chunkX(),
                            entry.chunkZ(),
                            entry.easterEggSkinIndex());
                }
                if (entry.spawnYaw() != 0.0f) {
                    chunkObj.addProperty("spawnYaw", entry.spawnYaw());
                }
                chunkloaders.add(chunkObj);
            }

            json.add("chunkloaders", chunkloaders);

            Path tempPath = configPath.resolveSibling(CONFIG_FILE + ".tmp");
            try {
                try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                    GSON.toJson(json, writer);
                }
                try {
                    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to save config file", e);
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
                restoreFromBackup();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static int getConfigVersion(JsonObject json) {
        if (json.has("configVersion")) {
            try {
                return json.get("configVersion").getAsInt();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static void migrateConfig(JsonObject json, int version) {
        if (version < 1) {
        }
        if (version < 2) {

            EasterEggSkinGuard.migrateUnsignedProofs(json);
        }
        json.addProperty("configVersion", CONFIG_VERSION);
    }

    private void scheduleSave() {
        synchronized (saveScheduleLock) {
            savePending = true;
            if (saveTimer == null) {
                saveTimer = new Timer("chunkloader-config-save", true);
            }
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel();
            }
            pendingSaveTask = new TimerTask() {
                @Override
                public void run() {
                    saveImmediate();
                }
            };
            saveTimer.schedule(pendingSaveTask, SAVE_DEBOUNCE_MS);
        }
    }

    public boolean isTabListVisibleAll() {
        return tabListVisibleAll;
    }

    public void setTabListVisibleAll(boolean visible) {
        this.tabListVisibleAll = visible;
    }

    private static void migrateFromLegacyPathIfNeeded(ChunkloaderConfig config) {
        if (config.configPath.toFile().exists()) {
            return;
        }
        Path parent = config.configPath.getParent();
        if (parent == null)
            return;
        Path grandparent = parent.getParent();
        if (grandparent == null)
            return;
        Path legacyPath = grandparent.resolve(CONFIG_FILE);
        if (!Files.exists(legacyPath)) {
            return;
        }
        try {
            Files.createDirectories(parent);
            Files.copy(legacyPath, config.configPath, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(legacyPath);
        } catch (IOException ignored) {
        }
    }

    private void createBackup() {
        File configFile = configPath.toFile();
        if (!configFile.exists()) {
            return;
        }

        try {
            Path configDir = configPath.getParent();
            if (configDir == null) {
                return;
            }
            Path backupDir = configDir.resolve(BACKUPS_SUBFOLDER);
            Files.createDirectories(backupDir);
            cleanupOldBackups(backupDir);

            long now = System.currentTimeMillis();
            if (BACKUP_COOLDOWN_MS > 0 && (now - lastBackupTimeMs) < BACKUP_COOLDOWN_MS) {
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path backupPath = backupDir.resolve(BACKUP_PREFIX + timestamp + ".json");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            updateLatestBackup(backupDir, backupPath);
            lastBackupTimeMs = now;
        } catch (IOException ignored) {
        }
    }

    private void cleanupOldBackups(Path backupDir) {
        try {
            List<Path> backups = new ArrayList<>();
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX) &&
                                !path.getFileName().toString().equals(LATEST_BACKUP_NAME))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .forEach(backups::add);
            }

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                try {
                    Files.delete(backups.get(i));
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete old backup: {}", backups.get(i).getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to cleanup old backups", e);
        }
    }

    private void updateLatestBackup(Path backupDir, Path latest) {
        try {
            Path latestLink = backupDir.resolve(LATEST_BACKUP_NAME);
            Files.copy(latest, latestLink, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {

        }
    }

    private boolean restoreFromBackup() {
        try {
            Path configDir = configPath.getParent();
            if (configDir == null) {
                return false;
            }
            Path backupDir = configDir.resolve(BACKUPS_SUBFOLDER);
            if (!Files.exists(backupDir)) {
                return false;
            }
            List<Path> backups = new ArrayList<>();
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .forEach(backups::add);
            }

            for (Path backup : backups) {
                try {

                    try (FileReader reader = new FileReader(backup.toFile())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json == null) {
                            continue;
                        }
                    }
                    Files.copy(backup, configPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.warn("Restored config from backup: {}", backup.getFileName());
                    return true;
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid backup: {}", backup.getFileName(), e);
                }
            }
            return false;
        } catch (IOException e) {
            LOGGER.error("Failed to restore from backup", e);
            return false;
        }
    }

    public List<ChunkloaderTarget> getChunkEntries() {
        if (cachedVersion == cacheVersion && cachedChunkEntries != null) {
            return cachedChunkEntries;
        }

        lock.readLock().lock();
        try {
            if (cachedVersion == cacheVersion && cachedChunkEntries != null) {
                return cachedChunkEntries;
            }
            List<ChunkloaderTarget> snapshot = Collections.unmodifiableList(new ArrayList<>(chunkEntries));
            cachedChunkEntries = snapshot;
            cachedVersion = cacheVersion;
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void invalidateCache() {
        cacheVersion++;
    }

    public void replaceAllEntries(List<ChunkloaderTarget> newEntries) {
        lock.writeLock().lock();
        try {
            chunkEntries.clear();
            chunkEntries.addAll(newEntries);
            invalidateCache();
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasEntry(int chunkX, int chunkZ, String dimension) {
        lock.readLock().lock();
        try {
            return chunkEntries.stream().anyMatch(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasEntryByName(String name) {
        if (name == null)
            return false;
        lock.readLock().lock();
        try {
            return chunkEntries.stream().anyMatch(entry -> entry.name() != null && name.equalsIgnoreCase(entry.name()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public ChunkloaderTarget getEntry(int chunkX, int chunkZ, String dimension) {
        lock.readLock().lock();
        try {
            return chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getMaxChunkloaders() {
        return ChunkloaderConstants.MAX_CHUNKLOADERS;
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, "minecraft:overworld");
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name,
            String dimension) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, null);
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name,
            String dimension, ChunkloaderTarget excludeEntry) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, excludeEntry, null);
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name,
            String dimension, ChunkloaderTarget excludeEntry, Boolean forceEnabled) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, excludeEntry, forceEnabled,
                null);
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name,
            String dimension, ChunkloaderTarget excludeEntry, Boolean forceEnabled, String ownerName) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, excludeEntry, forceEnabled, ownerName, 0.0f);
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name,
            String dimension, ChunkloaderTarget excludeEntry, Boolean forceEnabled, String ownerName, float spawnYaw) {
        lock.writeLock().lock();
        try {
            boolean entryExists = chunkEntries.stream().anyMatch(entry -> entry.chunkX() == chunkX
                    && entry.chunkZ() == chunkZ && entry.dimension().equals(dimension));
            if (!entryExists && chunkEntries.size() >= ChunkloaderConstants.MAX_CHUNKLOADERS) {

                return false;
            }

            if (name != null && !entryExists) {
                boolean nameExists = chunkEntries.stream()
                        .anyMatch(entry -> entry.name() != null
                                && name.equalsIgnoreCase(entry.name())
                                && (excludeEntry == null || entry != excludeEntry));
                if (nameExists) {

                    return false;
                }
            }

            blockY = Math.max(ChunkloaderConstants.MIN_BLOCK_Y, Math.min(ChunkloaderConstants.MAX_BLOCK_Y, blockY));

            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ
                            && entry.dimension().equals(dimension))
                    .findFirst()
                    .orElse(null);

            boolean enabled;
            if (forceEnabled != null) {
                enabled = forceEnabled;
            } else {
                enabled = existing != null ? existing.enabled() : true;
            }

            boolean nameVisible = existing != null ? existing.nameVisible() : true;
            boolean allowMobSpawning = existing != null ? existing.allowMobSpawning() : true;
            int chunkRadius = existing != null ? existing.chunkRadius() : 0;
            String finalOwnerName = ownerName != null ? ownerName : (existing != null ? existing.ownerName() : null);
            Integer easterEggSkinIndex = existing != null ? existing.easterEggSkinIndex() : null;
            float finalSpawnYaw = existing != null ? existing.spawnYaw() : spawnYaw;
            if (existing != null) {
                chunkEntries.remove(existing);
            }
            chunkEntries.add(new ChunkloaderTarget(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible,
                    chunkRadius, allowMobSpawning, dimension, finalOwnerName, easterEggSkinIndex, finalSpawnYaw));
            save();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntryEnabled(int chunkX, int chunkZ, String dimension, boolean enabled) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                        existing.blockX(), existing.blockY(), existing.blockZ(),
                        existing.name(), enabled, existing.nameVisible(), existing.chunkRadius(),
                        existing.allowMobSpawning(), existing.dimension(), existing.ownerName(),
                        existing.easterEggSkinIndex(), existing.spawnYaw()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntryNameVisible(int chunkX, int chunkZ, String dimension, boolean nameVisible) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                        existing.blockX(), existing.blockY(), existing.blockZ(),
                        existing.name(), existing.enabled(), nameVisible, existing.chunkRadius(),
                        existing.allowMobSpawning(), existing.dimension(), existing.ownerName(),
                        existing.easterEggSkinIndex(), existing.spawnYaw()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntryChunkRadius(int chunkX, int chunkZ, String dimension, int chunkRadius) {
        lock.writeLock().lock();
        try {
            chunkRadius = Math.max(0, Math.min(3, chunkRadius));

            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                        existing.blockX(), existing.blockY(), existing.blockZ(),
                        existing.name(), existing.enabled(), existing.nameVisible(), chunkRadius,
                        existing.allowMobSpawning(), existing.dimension(), existing.ownerName(),
                        existing.easterEggSkinIndex(), existing.spawnYaw()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntryAllowMobSpawning(int chunkX, int chunkZ, String dimension, boolean allowMobSpawning) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                int newRadius = existing.chunkRadius();

                String newName = existing.name();
                if (newName != null) {
                    String oldPrefix = existing.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                    String newPrefix = allowMobSpawning ? "Fakeplayer" : "Chunkplayer";

                    if (newName.startsWith(oldPrefix)) {
                        String numStr = newName.substring(oldPrefix.length());
                        if (numStr.matches("^\\d+$")) {
                            newName = generateNextNameForPrefix(newPrefix, existing);
                        }
                    } else if (newName.startsWith("fakeplayer") || newName.startsWith("chunkplayer")) {
                        String oldPrefixLower = existing.allowMobSpawning() ? "fakeplayer" : "chunkplayer";
                        if (newName.startsWith(oldPrefixLower)) {
                            String numStr = newName.substring(oldPrefixLower.length());
                            if (numStr.matches("^\\d+$")) {
                                newName = generateNextNameForPrefix(newPrefix, existing);
                            }
                        }
                    }
                }

                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                        existing.blockX(), existing.blockY(), existing.blockZ(),
                        newName, existing.enabled(), existing.nameVisible(), newRadius, allowMobSpawning,
                        existing.dimension(), existing.ownerName(),
                        existing.easterEggSkinIndex(), existing.spawnYaw()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.length() <= 16 && name.matches("^[a-zA-Z0-9]+$");
    }

    public boolean updateEntryName(int chunkX, int chunkZ, String dimension, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        final String trimmedName = newName.trim();

        if (!isValidName(trimmedName)) {
            return false;
        }

        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                return false;
            }

            if (trimmedName.equals(existing.name())) {
                return false;
            }

            ChunkloaderTarget existingWithName = getEntryByName(trimmedName);
            if (existingWithName != null && existingWithName != existing) {
                return false;
            }

            chunkEntries.remove(existing);
            chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                    existing.blockX(), existing.blockY(), existing.blockZ(),
                    trimmedName, existing.enabled(), existing.nameVisible(), existing.chunkRadius(),
                    existing.allowMobSpawning(), existing.dimension(), existing.ownerName(),
                    existing.easterEggSkinIndex(), existing.spawnYaw()));
            save();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntryEasterEggSkinIndex(int chunkX, int chunkZ, String dimension, Integer easterEggSkinIndex) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                    .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && Objects.equals(entry.dimension(), dimension))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(),
                        existing.blockX(), existing.blockY(), existing.blockZ(),
                        existing.name(), existing.enabled(), existing.nameVisible(), existing.chunkRadius(),
                        existing.allowMobSpawning(), existing.dimension(), existing.ownerName(),
                        easterEggSkinIndex, existing.spawnYaw()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, null);
    }

    public ChunkloaderTarget getEntryByName(String name) {
        if (name == null)
            return null;
        lock.readLock().lock();
        try {
            return chunkEntries.stream()
                    .filter(entry -> entry.name() != null && name.equalsIgnoreCase(entry.name()))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean removeEntryByName(String name) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = getEntryByName(name);
            if (existing != null) {
                chunkEntries.remove(existing);
                save();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextName(boolean isFakePlayer) {
        String prefix = isFakePlayer ? "Fakeplayer" : "Chunkplayer";
        return generateNextNameForPrefix(prefix, null);
    }

    private String generateNextNameForPrefix(String prefix, ChunkloaderTarget excludeEntry) {
        Set<Integer> usedNumbers = new HashSet<>();
        for (ChunkloaderTarget entry : chunkEntries) {
            if (entry == excludeEntry) {
                continue;
            }
            String entryName = entry.name();
            if (entryName != null && entryName.length() >= prefix.length()
                    && entryName.regionMatches(true, 0, prefix, 0, prefix.length())) {
                try {
                    String numStr = entryName.substring(prefix.length());
                    if (numStr.matches("^\\d+$")) {
                        int num = Integer.parseInt(numStr);
                        usedNumbers.add(num);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }

        int nextNum = 1;
        while (usedNumbers.contains(nextNum)) {
            nextNum++;
        }
        return prefix + nextNum;
    }

    public String generateNextName() {
        return generateNextName(true);
    }

    public boolean removeEntry(int chunkX, int chunkZ, String dimension) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = getEntry(chunkX, chunkZ, dimension);
            if (existing != null) {
                chunkEntries.remove(existing);
                save();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> findSimilarNames(String name, int maxResults) {
        lock.readLock().lock();
        try {
            if (name == null || name.isEmpty()) {
                return List.of();
            }

            String lowerName = name.toLowerCase();
            List<String> similar = new ArrayList<>();

            for (ChunkloaderTarget entry : chunkEntries) {
                if (entry.name() != null) {
                    String entryName = entry.name().toLowerCase();
                    if (entryName.contains(lowerName) || lowerName.contains(entryName)) {
                        similar.add(entry.name());
                        if (similar.size() >= maxResults) {
                            break;
                        }
                    }
                }
            }

            return similar;
        } finally {
            lock.readLock().unlock();
        }
    }
}
