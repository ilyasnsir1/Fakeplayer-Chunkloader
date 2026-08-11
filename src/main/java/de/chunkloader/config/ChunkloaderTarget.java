package de.chunkloader.config;

import de.chunkloader.ChunkloaderConstants;

public record ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension, String ownerName, Integer easterEggSkinIndex, float spawnYaw) {
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension, String ownerName, Integer easterEggSkinIndex) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, ownerName, easterEggSkinIndex, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension, String ownerName) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, ownerName, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, null, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, "minecraft:overworld", null, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, true, "minecraft:overworld", null, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, ChunkloaderConstants.DEFAULT_RADIUS, true, "minecraft:overworld", null, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, true, true, 2, true, "minecraft:overworld", null, null, 0.0f);
    }

    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, null, true, true, 2, true, "minecraft:overworld", null, null, 0.0f);
    }
}
