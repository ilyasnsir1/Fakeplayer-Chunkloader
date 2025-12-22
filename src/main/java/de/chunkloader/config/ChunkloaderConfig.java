package de.chunkloader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chunkloader.ChunkloaderConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChunkloaderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "chunkloader_config.json";
    private static final String BACKUP_PREFIX = "chunkloader_config_backup_";
    private static final int MAX_BACKUPS = 5;
private static final String LATEST_BACKUP_NAME = BACKUP_PREFIX + "latest.json";
    
    private final List<ChunkloaderTarget> chunkEntries = new ArrayList<>();
    private final Path configPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public Path getConfigPath() {
        return configPath;
    }
    
    public ChunkloaderConfig(MinecraftServer server) {
        Path path;
        try {
            if (server != null) {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    Path serverPath = server.getServerDirectory();
                    if (serverPath != null) {
                        Path savesDir = serverPath.resolve("saves");
                        if (java.nio.file.Files.exists(savesDir)) {
                            try {
                                java.nio.file.Path mostRecentWorldDir = null;
                                long mostRecentTime = 0;
                                try {
                                    java.nio.file.DirectoryStream.Filter<java.nio.file.Path> filter = entry -> {
                                        return java.nio.file.Files.isDirectory(entry) && 
                                               java.nio.file.Files.exists(entry.resolve("level.dat"));
                                    };
                                    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = 
                                         java.nio.file.Files.newDirectoryStream(savesDir, filter)) {
                                        for (java.nio.file.Path worldDir : stream) {
                                            try {
                                                Path levelDat = worldDir.resolve("level.dat");
                                                if (java.nio.file.Files.exists(levelDat)) {
                                                    long levelDatTime = java.nio.file.Files.getLastModifiedTime(levelDat).toMillis();
                                                    if (levelDatTime > mostRecentTime) {
                                                        mostRecentTime = levelDatTime;
                                                        mostRecentWorldDir = worldDir;
                                                    }
                                                }
                                            } catch (Exception e) {
                                            }
                                        }
                                    }
                                } catch (Exception e) {

                                }
                                
                                if (mostRecentWorldDir != null) {
                                    path = mostRecentWorldDir.resolve(CONFIG_FILE);

                                } else {
                                    String levelName = server.getWorldData().getLevelName();
                                    if (levelName != null && !levelName.isEmpty()) {
                                        path = savesDir.resolve(levelName).resolve(CONFIG_FILE);

                                    } else {
                                        path = serverPath.resolve(CONFIG_FILE);
                                    }
                                }
                            } catch (Exception e) {
                                path = serverPath.resolve(CONFIG_FILE);
                            }
                        } else {
                            path = serverPath.resolve("world").resolve(CONFIG_FILE);
                        }
                    } else {
                        path = new File(".").toPath().resolve(CONFIG_FILE);
                    }
                } else {
                    Path serverPath = server.getServerDirectory();
                    if (serverPath != null) {
                        path = serverPath.resolve(CONFIG_FILE);
                    } else {
                        path = new File(".").toPath().resolve(CONFIG_FILE);
                    }
                }
            } else {
                path = new File(".").toPath().resolve(CONFIG_FILE);
            }
        } catch (Exception e) {

            path = new File(".").toPath().resolve(CONFIG_FILE);
        }
        this.configPath = path;

    }
    
    public static ChunkloaderConfig load(MinecraftServer server) {
        ChunkloaderConfig config = new ChunkloaderConfig(server);
        File configFile = config.configPath.toFile();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                
                if (json.has("chunkloaders")) {
                    JsonArray chunkloaders = json.getAsJsonArray("chunkloaders");
                    for (JsonElement element : chunkloaders) {
                        try {
                            JsonObject chunkObj = element.getAsJsonObject();
                            int chunkX = chunkObj.get("x").getAsInt();
                            int chunkZ = chunkObj.get("z").getAsInt();
                            int blockX = chunkObj.has("blockX") ? chunkObj.get("blockX").getAsInt() : chunkX * ChunkloaderConstants.CHUNK_SIZE + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                            int blockY = chunkObj.has("blockY") ? chunkObj.get("blockY").getAsInt() : ChunkloaderConstants.DEFAULT_BLOCK_Y;
                            int blockZ = chunkObj.has("blockZ") ? chunkObj.get("blockZ").getAsInt() : chunkZ * ChunkloaderConstants.CHUNK_SIZE + ChunkloaderConstants.CHUNK_CENTER_OFFSET;
                            String name = chunkObj.has("name") ? chunkObj.get("name").getAsString() : null;
                            boolean enabled = chunkObj.has("enabled") ? chunkObj.get("enabled").getAsBoolean() : true;
                            boolean nameVisible = chunkObj.has("nameVisible") ? chunkObj.get("nameVisible").getAsBoolean() : true;
                            int chunkRadius = chunkObj.has("chunkRadius") ? chunkObj.get("chunkRadius").getAsInt() : ChunkloaderConstants.DEFAULT_RADIUS;
                            boolean allowMobSpawning = chunkObj.has("allowMobSpawning") ? chunkObj.get("allowMobSpawning").getAsBoolean() : true;
                            String dimension = chunkObj.has("dimension") ? chunkObj.get("dimension").getAsString() : "minecraft:overworld";
                            String ownerName = chunkObj.has("ownerName") ? chunkObj.get("ownerName").getAsString() : null;
                            boolean hideOtherDots = chunkObj.has("hideOtherDots") ? chunkObj.get("hideOtherDots").getAsBoolean() : false;
                            
                            chunkRadius = Math.max(ChunkloaderConstants.MIN_RADIUS, Math.min(ChunkloaderConstants.MAX_RADIUS, chunkRadius));
                            blockY = Math.max(ChunkloaderConstants.MIN_BLOCK_Y, Math.min(ChunkloaderConstants.MAX_BLOCK_Y, blockY));
                            
                            if (name != null && config.hasEntryByName(name)) {

                                continue;
                            }
                            
                            config.chunkEntries.add(new ChunkloaderTarget(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, ownerName, hideOtherDots));
                        } catch (Exception e) {

                        }
                    }
                }

            } catch (Exception e) {

                try {
                    config.save();
                } catch (Exception saveEx) {

                }
            }
        } else {
            try {
                config.save();
            } catch (Exception e) {

            }
        }
        
        return config;
    }
    
    public void save() {
        lock.writeLock().lock();
        try {
            createBackup();
            
            JsonObject json = new JsonObject();
            JsonArray chunkloaders = new JsonArray();
            
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
                chunkObj.addProperty("hideOtherDots", entry.hideOtherDots());
                chunkloaders.add(chunkObj);
            }
            
            json.add("chunkloaders", chunkloaders);
            
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(json, writer);

            } catch (IOException e) {

                restoreFromBackup();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void createBackup() {
        File configFile = configPath.toFile();
        if (!configFile.exists()) {
            return;
        }
        
        try {
            Path backupDir = configPath.getParent();
            if (backupDir == null) {
                return;
            }
            
            cleanupOldBackups(backupDir);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path backupPath = backupDir.resolve(BACKUP_PREFIX + timestamp + ".json");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            updateLatestBackup(backupDir, backupPath);

        } catch (IOException e) {

        }
    }
    
    private void cleanupOldBackups(Path backupDir) {
        try {
            List<Path> backups = new ArrayList<>();
            Files.list(backupDir)
                .filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX) &&
                    !path.getFileName().toString().equals(LATEST_BACKUP_NAME))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .forEach(backups::add);
            
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                try {
                    Files.delete(backups.get(i));

                } catch (IOException e) {

                }
            }
        } catch (IOException e) {

        }
    }
    
    private void updateLatestBackup(Path backupDir, Path latest) {
        try {
            Path latestLink = backupDir.resolve(LATEST_BACKUP_NAME);
            Files.copy(latest, latestLink, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {

        }
    }
    
    private void restoreFromBackup() {
        try {
            Path backupDir = configPath.getParent();
            if (backupDir == null) {
                return;
            }
            
            Path latestBackup = Files.list(backupDir)
                .filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
            
            if (latestBackup != null) {
                Files.copy(latestBackup, configPath, StandardCopyOption.REPLACE_EXISTING);

            }
        } catch (IOException e) {

        }
    }
    
    public List<ChunkloaderTarget> getChunkEntries() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(chunkEntries));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void replaceAllEntries(List<ChunkloaderTarget> newEntries) {
        lock.writeLock().lock();
        try {
            chunkEntries.clear();
            chunkEntries.addAll(newEntries);
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public boolean hasEntry(int chunkX, int chunkZ) {
        lock.readLock().lock();
        try {
            return chunkEntries.stream().anyMatch(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean hasEntryByName(String name) {
        if (name == null) return false;
        lock.readLock().lock();
        try {
            return chunkEntries.stream().anyMatch(entry -> entry.name() != null && name.equalsIgnoreCase(entry.name()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public ChunkloaderTarget getEntry(int chunkX, int chunkZ) {
        lock.readLock().lock();
        try {
            return chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
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
    
    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, String dimension) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, null);
    }
    
    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, String dimension, ChunkloaderTarget excludeEntry) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, excludeEntry, null);
    }
    
    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, String dimension, ChunkloaderTarget excludeEntry, Boolean forceEnabled) {
        return addOrUpdateEntry(chunkX, chunkZ, blockX, blockY, blockZ, name, dimension, excludeEntry, forceEnabled, null);
    }
    
    public boolean addOrUpdateEntry(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, String dimension, ChunkloaderTarget excludeEntry, Boolean forceEnabled, String ownerName) {
        lock.writeLock().lock();
        try {
            boolean entryExists = chunkEntries.stream().anyMatch(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && entry.dimension().equals(dimension));
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
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ && entry.dimension().equals(dimension))
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
            boolean hideOtherDots = existing != null ? existing.hideOtherDots() : false;
            String finalOwnerName = ownerName != null ? ownerName : (existing != null ? existing.ownerName() : null);
            if (existing != null) {
                chunkEntries.remove(existing);
            }
            chunkEntries.add(new ChunkloaderTarget(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, finalOwnerName, hideOtherDots));
            save();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void updateEntryEnabled(int chunkX, int chunkZ, boolean enabled) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
                .findFirst()
                .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                    existing.blockX(), existing.blockY(), existing.blockZ(), 
                    existing.name(), enabled, existing.nameVisible(), existing.chunkRadius(), existing.allowMobSpawning(), existing.dimension(), existing.ownerName(), existing.hideOtherDots()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void updateEntryNameVisible(int chunkX, int chunkZ, boolean nameVisible) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
                .findFirst()
                .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                    existing.blockX(), existing.blockY(), existing.blockZ(), 
                    existing.name(), existing.enabled(), nameVisible, existing.chunkRadius(), existing.allowMobSpawning(), existing.dimension(), existing.ownerName(), existing.hideOtherDots()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void updateEntryChunkRadius(int chunkX, int chunkZ, int chunkRadius) {
        lock.writeLock().lock();
        try {
            chunkRadius = Math.max(0, Math.min(3, chunkRadius));
            
            ChunkloaderTarget existing = chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
                .findFirst()
                .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                    existing.blockX(), existing.blockY(), existing.blockZ(), 
                    existing.name(), existing.enabled(), existing.nameVisible(), chunkRadius, existing.allowMobSpawning(), existing.dimension(), existing.ownerName(), existing.hideOtherDots()));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void updateEntryAllowMobSpawning(int chunkX, int chunkZ, boolean allowMobSpawning) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
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
                            String candidateName = newPrefix + numStr;
                            boolean nameExists = chunkEntries.stream()
                                .anyMatch(entry -> entry != existing && entry.name() != null && candidateName.equalsIgnoreCase(entry.name()));
                            if (nameExists) {
                                newName = generateNextNameForPrefix(newPrefix, existing);
                            } else {
                                String lowestAvailableName = generateNextNameForPrefix(newPrefix, existing);
                                try {
                                    int currentNum = Integer.parseInt(numStr);
                                    String lowestNumStr = lowestAvailableName.substring(newPrefix.length());
                                    if (lowestNumStr.matches("^\\d+$")) {
                                        int lowestNum = Integer.parseInt(lowestNumStr);
                                        if (lowestNum < currentNum) {
                                            newName = lowestAvailableName;
                                        } else {
                                            newName = candidateName;
                                        }
                                    } else {
                                        newName = candidateName;
                                    }
                                } catch (NumberFormatException e) {
                                    newName = candidateName;
                                }
                            }
                        }
                    }
                    else if (newName.startsWith("fakeplayer") || newName.startsWith("chunkplayer")) {
                        String oldPrefixLower = existing.allowMobSpawning() ? "fakeplayer" : "chunkplayer";
                        if (newName.startsWith(oldPrefixLower)) {
                            String numStr = newName.substring(oldPrefixLower.length());
                            if (numStr.matches("^\\d+$")) {
                                String candidateName = newPrefix + numStr;
                                boolean nameExists = chunkEntries.stream()
                                    .anyMatch(entry -> entry != existing && entry.name() != null && candidateName.equalsIgnoreCase(entry.name()));
                                if (nameExists) {
                                    newName = generateNextNameForPrefix(newPrefix, existing);
                                } else {
                                    String lowestAvailableName = generateNextNameForPrefix(newPrefix, existing);
                                    try {
                                        int currentNum = Integer.parseInt(numStr);
                                        String lowestNumStr = lowestAvailableName.substring(newPrefix.length());
                                        if (lowestNumStr.matches("^\\d+$")) {
                                            int lowestNum = Integer.parseInt(lowestNumStr);
                                            if (lowestNum < currentNum) {
                                                newName = lowestAvailableName;
                                            } else {
                                                newName = candidateName;
                                            }
                                        } else {
                                            newName = candidateName;
                                        }
                                    } catch (NumberFormatException e) {
                                        newName = candidateName;
                                    }
                                }
                            }
                        }
                    }
                }
                
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                    existing.blockX(), existing.blockY(), existing.blockZ(), 
                    newName, existing.enabled(), existing.nameVisible(), newRadius, allowMobSpawning, existing.dimension(), existing.ownerName(), existing.hideOtherDots()));
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
        return name.matches("^[a-zA-Z0-9]+$");
    }
    
    public boolean updateEntryName(int chunkX, int chunkZ, String newName) {
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
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
                .findFirst()
                .orElse(null);
            if (existing == null) {
                return false;
            }
            
            if (trimmedName.equals(existing.name())) {
                return false;
            }

            boolean nameTaken = chunkEntries.stream()
                .anyMatch(entry -> entry != existing
                    && entry.name() != null
                    && trimmedName.equalsIgnoreCase(entry.name()));
            if (nameTaken) {
                return false;
            }
            
            chunkEntries.remove(existing);
            chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                existing.blockX(), existing.blockY(), existing.blockZ(), 
                trimmedName, existing.enabled(), existing.nameVisible(), existing.chunkRadius(), existing.allowMobSpawning(), existing.dimension(), existing.ownerName(), existing.hideOtherDots()));
            save();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void updateEntryHideOtherDots(int chunkX, int chunkZ, boolean hideOtherDots) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = chunkEntries.stream()
                .filter(entry -> entry.chunkX() == chunkX && entry.chunkZ() == chunkZ)
                .findFirst()
                .orElse(null);
            if (existing != null) {
                chunkEntries.remove(existing);
                chunkEntries.add(new ChunkloaderTarget(existing.chunkX(), existing.chunkZ(), 
                    existing.blockX(), existing.blockY(), existing.blockZ(), 
                    existing.name(), existing.enabled(), existing.nameVisible(), existing.chunkRadius(), existing.allowMobSpawning(), existing.dimension(), existing.ownerName(), hideOtherDots));
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
        if (name == null) return null;
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
            if (entryName != null && entryName.length() >= prefix.length() && entryName.regionMatches(true, 0, prefix, 0, prefix.length())) {
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
    
    public boolean removeEntry(int chunkX, int chunkZ) {
        lock.writeLock().lock();
        try {
            ChunkloaderTarget existing = getEntry(chunkX, chunkZ);
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

