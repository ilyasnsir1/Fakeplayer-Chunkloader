package de.chunkloader.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "chunkloader_permissions.json";

    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Path configPath;

    public PermissionConfig(MinecraftServer server) {
        this.configPath = de.chunkloader.config.ChunkloaderPaths.getChunkloaderDir(server).resolve(CONFIG_FILE);
    }

    public static PermissionConfig load(MinecraftServer server) {
        PermissionConfig config = new PermissionConfig(server);
        migrateFromLegacyPathIfNeeded(config);
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
                        }
                    }
                }

            } catch (Exception e) {
            }
        } else {
            try {
                config.save();
            } catch (Exception e) {
            }
        }

        return config;
    }

    private static void migrateFromLegacyPathIfNeeded(PermissionConfig config) {
        if (config.configPath.toFile().exists()) {
            return;
        }
        Path parent = config.configPath.getParent();
        if (parent == null) return;
        Path grandparent = parent.getParent();
        if (grandparent == null) return;
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

    public void save() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        File configFile = configPath.toFile();
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (parentDir == null) {
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
        }
    }
}

