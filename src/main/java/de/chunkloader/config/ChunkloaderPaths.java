package de.chunkloader.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;

public final class ChunkloaderPaths {
    public static final String CONFIG_SUBFOLDER = "chunkloader";

    private ChunkloaderPaths() {}

    public static Path getChunkloaderDir(MinecraftServer server) {
        Path path;
        try {
            if (server != null) {
                Path worldRoot = server.getWorldPath(LevelResource.ROOT);
                if (worldRoot != null) {
                    return worldRoot.resolve(CONFIG_SUBFOLDER);
                }
                Path serverPath = server.getServerDirectory();
                if (serverPath == null) {
                    serverPath = new File(".").toPath();
                }
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    Path savesDir = serverPath.resolve("saves");
                    if (java.nio.file.Files.exists(savesDir)) {
                        String levelName = server.getWorldData().getLevelName();
                        if (levelName != null && !levelName.isEmpty()) {
                            path = savesDir.resolve(levelName).resolve(CONFIG_SUBFOLDER);
                        } else {
                            path = serverPath.resolve("world").resolve(CONFIG_SUBFOLDER);
                        }
                    } else {
                        path = serverPath.resolve("world").resolve(CONFIG_SUBFOLDER);
                    }
                } else {
                    path = serverPath.resolve("world").resolve(CONFIG_SUBFOLDER);
                }
            } else {
                path = new File(".").toPath().resolve("world").resolve(CONFIG_SUBFOLDER);
            }
        } catch (Exception e) {
            path = new File(".").toPath().resolve("world").resolve(CONFIG_SUBFOLDER);
        }
        return path;
    }
}
