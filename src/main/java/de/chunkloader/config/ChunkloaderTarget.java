package de.chunkloader.config;

import de.chunkloader.ChunkloaderConstants;

public record ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension, String ownerName) {
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning, String dimension) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, dimension, null);
    }
    
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius, boolean allowMobSpawning) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, allowMobSpawning, "minecraft:overworld", null);
    }
    
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible, int chunkRadius) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, chunkRadius, true, "minecraft:overworld", null);
    }
    
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name, boolean enabled, boolean nameVisible) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, enabled, nameVisible, ChunkloaderConstants.DEFAULT_RADIUS, true, "minecraft:overworld", null);
    }
    
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ, String name) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, name, true, true, 2, true, "minecraft:overworld", null);
    }
    
    public ChunkloaderTarget(int chunkX, int chunkZ, int blockX, int blockY, int blockZ) {
        this(chunkX, chunkZ, blockX, blockY, blockZ, null, true, true, 2, true, "minecraft:overworld", null);
    }
}

