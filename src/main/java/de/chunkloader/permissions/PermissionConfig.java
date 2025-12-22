package de.chunkloader.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "chunkloader_permissions.json";
    
    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Path configPath;
    
    public PermissionConfig(MinecraftServer server) {
        Path path;
        try {
            if (server != null) {
                var overworld = server.getOverworld();
                if (overworld != null) {
                    Path serverPath = server.getRunDirectory();
                    if (serverPath != null) {
                        var savesDir = serverPath.resolve("saves");
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
                                    ChunkloaderMod.LOGGER.warn("Error searching for world directory: {}", e.getMessage());
                                }
                                
                                if (mostRecentWorldDir != null) {
                                    path = mostRecentWorldDir.resolve(CONFIG_FILE);
                                } else {
                                    String levelName = server.getSaveProperties().getLevelName();
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
                    Path serverPath = server.getRunDirectory();
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
            ChunkloaderMod.LOGGER.error("Error determining permission config path", e);
            path = new File(".").toPath().resolve(CONFIG_FILE);
        }
        this.configPath = path;
        ChunkloaderMod.LOGGER.info("Permission config path: {}", path);
    }
    
    public static PermissionConfig load(MinecraftServer server) {
        PermissionConfig config = new PermissionConfig(server);
        File configFile = config.configPath.toFile();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                
                if (json.has("permissions")) {
                    JsonObject permissionsObj = json.getAsJsonObject("permissions");
                    for (Map.Entry<String, JsonElement> entry : permissionsObj.entrySet()) {
                        try {
                            UUID playerUuid = UUID.fromString(entry.getKey());
                            JsonArray permissionsArray = entry.getValue().getAsJsonArray();
                            Set<String> permissions = new HashSet<>();
                            for (JsonElement perm : permissionsArray) {
                                permissions.add(perm.getAsString());
                            }
                            config.playerPermissions.put(playerUuid, permissions);
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.warn("Failed to load permissions for player {}: {}", entry.getKey(), e.getMessage());
                        }
                    }
                }
                
                ChunkloaderMod.LOGGER.info("Loaded permissions for {} players", config.playerPermissions.size());
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Failed to load permission config", e);
            }
        } else {
            try {
                config.save();
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Failed to create default permission config", e);
            }
        }
        
        return config;
    }
    
    public void save() throws IOException {
        File configFile = configPath.toFile();
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        if (parentDir == null) {
            ChunkloaderMod.LOGGER.warn("Permission config path has no parent directory, using current directory");
            configFile = new File(CONFIG_FILE);
        }
        
        JsonObject json = new JsonObject();
        JsonObject permissionsObj = new JsonObject();
        
        for (Map.Entry<UUID, Set<String>> entry : playerPermissions.entrySet()) {
            JsonArray permissionsArray = new JsonArray();
            for (String permission : entry.getValue()) {
                permissionsArray.add(permission);
            }
            permissionsObj.add(entry.getKey().toString(), permissionsArray);
        }
        
        json.add("permissions", permissionsObj);
        
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(json, writer);
        }
    }
    
    public void grantPermission(UUID playerUuid, String permission) {
        playerPermissions.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(permission);
        try {
            save();
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.error("Failed to save permission config", e);
        }
    }
    
    public void revokePermission(UUID playerUuid, String permission) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        if (permissions != null) {
            permissions.remove(permission);
            if (permissions.isEmpty()) {
                playerPermissions.remove(playerUuid);
            }
            try {
                save();
            } catch (IOException e) {
                ChunkloaderMod.LOGGER.error("Failed to save permission config", e);
            }
        }
    }
    
    public boolean hasPermission(UUID playerUuid, String permission) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        if (permissions == null) {
            return false;
        }
        
        if (permissions.contains(permission)) {
            return true;
        }
        
        if (permissions.contains("chunkloader.*")) {
            return permission.startsWith("chunkloader.");
        }
        
        if (permissions.contains(PermissionManager.PERMISSION_ADMIN)) {
            return true;
        }
        
        return false;
    }
    
    public Set<String> getPlayerPermissions(UUID playerUuid) {
        return new HashSet<>(playerPermissions.getOrDefault(playerUuid, Collections.emptySet()));
    }
    
    public Map<UUID, Set<String>> getAllPermissions() {
        Map<UUID, Set<String>> result = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : playerPermissions.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
    }
    
    public void clearPlayerPermissions(UUID playerUuid) {
        playerPermissions.remove(playerUuid);
        try {
            save();
        } catch (IOException e) {
            ChunkloaderMod.LOGGER.error("Failed to save permission config", e);
        }
    }
}

