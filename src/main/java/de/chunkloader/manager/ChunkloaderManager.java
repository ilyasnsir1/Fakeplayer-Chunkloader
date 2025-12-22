package de.chunkloader.manager;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkMapTile;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.util.EntitySyncUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChunkloaderManager {
    private final MinecraftServer server;
    private ChunkloaderConfig config;
    private final Map<ChunkKey, ChunkloaderTarget> activeTargets = new ConcurrentHashMap<>();
    private final Map<ChunkKey, ChunkloaderFakePlayer> activeFakePlayers = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, UUID> markerEntities = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ChunkKey> markerToChunkKey = new ConcurrentHashMap<>();
    private final Set<ChunkKey> visualizationActive = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<ChunkKey, Visualization3DConfig> visualization3DActive = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, PendingChunkloaderState> pendingChunkloaderActivations = new ConcurrentHashMap<>();
    private final Set<UUID> syncingFakePlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastToggleTime = new ConcurrentHashMap<>();
    private static final long TOGGLE_COOLDOWN_MS = 200;
    private static final int PENDING_ACTIVATION_INITIAL_DELAY_TICKS = 0;
    private static final int PENDING_ACTIVATION_RETRY_TICKS = 20;
    private int tickCounter = 0;
    private String storedWorldName = null;

    private static TicketType getChunkTicketType(boolean simulate) {
        return simulate ? TicketType.PLAYER_SIMULATION : TicketType.PLAYER_LOADING;
    }
    private static final int DEFAULT_TILE_COLOR_ABGR = argbToAbgr(0xFF555555);
    private static final int ERROR_TILE_COLOR_ABGR = argbToAbgr(0xFFFF00FF);
    private static final int AIR_TILE_COLOR_ABGR = argbToAbgr(0xFF000000);
    
    final ConcurrentMap<ServerLevel, String> dimensionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerLevel> dimensionToWorldCache = new ConcurrentHashMap<>();
    
    public record Visualization3DConfig(int minY, int maxY) {
        public Visualization3DConfig() {
            this(ChunkloaderConstants.MIN_BLOCK_Y, ChunkloaderConstants.MAX_BLOCK_Y);
        }
    }

    private static class PendingChunkloaderState {
        private final ChunkloaderTarget entry;
        private int ticksUntilNextAttempt;

        PendingChunkloaderState(ChunkloaderTarget entry, int initialDelay) {
            this.entry = entry;
            this.ticksUntilNextAttempt = initialDelay;
        }

        public ChunkloaderTarget entry() {
            return entry;
        }

        public int ticksUntilNextAttempt() {
            return ticksUntilNextAttempt;
        }

        public void setTicksUntilNextAttempt(int ticks) {
            this.ticksUntilNextAttempt = ticks;
        }
    }
    
    public ChunkloaderManager(MinecraftServer server, ChunkloaderConfig config) {
        this.server = server;
        this.config = config;
    }

    private void scheduleChunkloaderInitialization(ChunkloaderTarget entry, int delayTicks) {
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        pendingChunkloaderActivations.put(key, new PendingChunkloaderState(entry, delayTicks));
    }

    private void cancelPendingChunkloader(ChunkKey key) {
        pendingChunkloaderActivations.remove(key);
    }
    
    private ServerLevel getWorldByDimension(String dimension) {
        ServerLevel cached = dimensionToWorldCache.get(dimension);
        if (cached != null) {
            return cached;
        }
        
        try {
            for (ServerLevel world : server.getAllLevels()) {
                String worldDimension = getDimensionFromWorld(world);
                if (worldDimension.equals(dimension)) {
                    dimensionToWorldCache.put(dimension, world);
                    return world;
                }
            }
        } catch (Exception e) {

        }
        return server.overworld();
    }
    
    private String getDimensionFromWorld(ServerLevel world) {
        if (world == null) {
            return "unknown";
        }
        return dimensionCache.computeIfAbsent(world, w -> w.dimension().location().toString());
    }
    
    public static String getDimensionString(ServerLevel world) {
        if (world == null) {
            return "unknown";
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager != null) {
            return manager.dimensionCache.computeIfAbsent(world, w -> w.dimension().location().toString());
        }
        return world.dimension().location().toString();
    }
    
    private Path determineConfigPath(MinecraftServer server) {
        try {
            if (server != null) {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    Path serverPath = server.getServerDirectory();
                    if (serverPath != null) {
                        Path savesDir = serverPath.resolve("saves");
                        if (java.nio.file.Files.exists(savesDir)) {
                            try {
                                String currentLevelName = null;
                                try {
                                    if (server.getWorldData() != null) {
                                        currentLevelName = server.getWorldData().getLevelName();
                                    }
                                } catch (Exception e) {
                                }
                                
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

                                    return mostRecentWorldDir.resolve("chunkloader_config.json");
                                }
                                
                                if (currentLevelName != null && !currentLevelName.isEmpty()) {

                                    return savesDir.resolve(currentLevelName).resolve("chunkloader_config.json");
                                }
                            } catch (Exception e) {

                            }
                        } else {
                            return serverPath.resolve("world").resolve("chunkloader_config.json");
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
        return null;
    }
    
    private String getStoredWorldName() {
        return storedWorldName;
    }
    
    private void storeWorldName(String worldName) {
        this.storedWorldName = worldName;
    }
    
    private void spawnParticles(ServerLevel world, BlockPos pos, boolean enabled, boolean allowMobSpawning) {
        if (world == null) return;
        
        if (enabled) {
            if (allowMobSpawning) {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.random.nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0.02);
                }
            } else {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.random.nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    world.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0.02);
                }
            }
        } else {
            for (int i = 0; i < 10; i++) {
                double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                double y = pos.getY() + world.random.nextDouble() * 2;
                double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                world.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
    }
    
    private void playSound(ServerLevel world, BlockPos pos, boolean enabled) {
        if (world == null) return;
        
        if (enabled) {
            world.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.5f, 1.2f);
        } else {
            world.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5f, 0.8f);
        }
    }
    
    public void toggleVisualization(ChunkKey key) {
        boolean wasActive = visualizationActive.contains(key);
        if (wasActive) {
            visualizationActive.remove(key);
        } else {
            visualizationActive.add(key);
        }

        if (!wasActive && visualizationActive.contains(key)) {
            ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
            if (fakePlayer != null && fakePlayer.isAlive()) {
                fakePlayer.setVisibleAsMarker(true);
                forceEntitySync(fakePlayer);

            }
        }
    }
    
    public boolean isVisualizationActive(ChunkKey key) {
        return visualizationActive.contains(key);
    }
    
    public void toggleVisualization3D(ChunkKey key) {
        toggleVisualization3D(key, -64, 320);
    }
    
    public void toggleVisualization3D(ChunkKey key, int minY, int maxY) {
        boolean wasActive = visualization3DActive.containsKey(key);
        if (wasActive) {
            visualization3DActive.remove(key);
        } else {
            visualization3DActive.put(key, new Visualization3DConfig(minY, maxY));
        }

        if (!wasActive && visualization3DActive.containsKey(key)) {
            ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
            if (fakePlayer != null && fakePlayer.isAlive()) {
                fakePlayer.setVisibleAsMarker(true);
                forceEntitySync(fakePlayer);

            }
        }
    }
    
    public boolean isVisualization3DActive(ChunkKey key) {
        return visualization3DActive.containsKey(key);
    }
    
    private void renderChunkBorders(ServerLevel world, ChunkloaderTarget entry) {
        if (world == null) return;
        
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        if (!visualizationActive.contains(key)) return;
        
        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;
        
        int y = entry.blockY();
        
        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            int worldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
            for (int z = minChunkZ * ChunkloaderConstants.CHUNK_SIZE; z <= (maxChunkZ + 1) * ChunkloaderConstants.CHUNK_SIZE; z += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                sendParticlesToAllPlayers(world, ParticleTypes.ELECTRIC_SPARK, worldX, y, z, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
        
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int worldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
            for (int x = minChunkX * ChunkloaderConstants.CHUNK_SIZE; x <= (maxChunkX + 1) * ChunkloaderConstants.CHUNK_SIZE; x += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                sendParticlesToAllPlayers(world, ParticleTypes.ELECTRIC_SPARK, x, y, worldZ, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
    }
    
    private void sendParticlesToAllPlayers(ServerLevel world, net.minecraft.core.particles.ParticleOptions particleType, 
            double x, double y, double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        if (world == null) return;
        world.sendParticles(particleType, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }
    
    private void renderChunkBorders3D(ServerLevel world, ChunkloaderTarget entry) {
        if (world == null) return;
        
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        Visualization3DConfig config = visualization3DActive.get(key);
        if (config == null) return;
        
        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;
        
        int minY = config.minY();
        int maxY = config.maxY();
        
        var particleType = ParticleTypes.SCRAPE;
        
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int chunkWorldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldXEnd = chunkWorldX + ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZEnd = chunkWorldZ + ChunkloaderConstants.CHUNK_SIZE;
                
                if (tickCounter % 2 == 0) {
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldX, y, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldXEnd, y, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldX, y, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldXEnd, y, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }
                
                if (tickCounter % 3 == 0) {
                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, x, maxY, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        sendParticlesToAllPlayers(world, particleType, x, maxY, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldX, maxY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        sendParticlesToAllPlayers(world, particleType, chunkWorldXEnd, maxY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    
                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, x, minY, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        sendParticlesToAllPlayers(world, particleType, x, minY, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        sendParticlesToAllPlayers(world, particleType, chunkWorldX, minY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        sendParticlesToAllPlayers(world, particleType, chunkWorldXEnd, minY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }
            }
        }
    }
    
    public void tick() {
        processPendingChunkloaderActivations();

        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (!entry.enabled()) continue;
            ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
            ChunkloaderFakePlayer fp = activeFakePlayers.get(key);
            if (!activeTargets.containsKey(key) || fp == null || !fp.isAlive()) {
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world != null) {
                    try {
                        activateChunkloader(entry, world);
                        if (fp == null || !fp.isAlive()) {
                            respawnMarkerForChunkloader(key, entry);
                        }
                        updateMarkerForChunkloader(key);
                        
                        ChunkloaderFakePlayer currentFp = activeFakePlayers.get(key);
                        if (currentFp != null && currentFp.isAlive()) {
                            currentFp.setVisibleAsMarker(true);
                            currentFp.setInvisible(false);
                            forceEntitySync(currentFp);
                        }

                    } catch (Exception e) {

                    }
                }
            }
        }
        
        for (ChunkKey key : visualizationActive) {
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry == null) {
                entry = config.getEntry(key.x(), key.z());
            }
            if (entry != null) {
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world != null) {
                    renderChunkBorders(world, entry);
                }
            }
        }
        
        for (Map.Entry<ChunkKey, Visualization3DConfig> entry : visualization3DActive.entrySet()) {
            ChunkloaderTarget target = activeTargets.get(entry.getKey());
            if (target == null) {
                target = config.getEntry(entry.getKey().x(), entry.getKey().z());
            }
            if (target != null) {
                ServerLevel world = getWorldByDimension(target.dimension());
                if (world != null) {
                    renderChunkBorders3D(world, target);
                }
            }
        }
        
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            ensureChunksLoaded();
        }
        
        performRandomTicksForChunkplayers();
    }
    
    private void performRandomTicksForChunkplayers() {
        Map<String, Set<ChunkKey>> chunksByDimension = new HashMap<>();
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (target.enabled() && !target.allowMobSpawning()) {
                chunksByDimension.computeIfAbsent(target.dimension(), k -> new HashSet<>()).add(entry.getKey());
            }
        }
        
        for (Map.Entry<String, Set<ChunkKey>> dimensionEntry : chunksByDimension.entrySet()) {
            ServerLevel world = getWorldByDimension(dimensionEntry.getKey());
            if (world == null) {
                continue;
            }
            
            for (ChunkKey chunkKey : dimensionEntry.getValue()) {
                try {
                    ChunkPos chunkPos = new ChunkPos(chunkKey.x(), chunkKey.z());
                    net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
                    
                    if (chunk == null || !(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) {
                        continue;
                    }
                    
                    for (int i = 0; i < 3; i++) {
                        int x = chunkPos.getMinBlockX() + world.getRandom().nextInt(16);
                        int z = chunkPos.getMinBlockZ() + world.getRandom().nextInt(16);
                        int bottomY = world.getMinY();
                        int topY = 320;
                        int height = topY - bottomY;
                        int y = bottomY + world.getRandom().nextInt(height);
                        
                        if (y >= bottomY && y < topY) {
                            BlockPos pos = new BlockPos(x, y, z);
                            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
                            if (state.isRandomlyTicking()) {
                                state.randomTick(world, pos, world.getRandom());
                            }
                        }
                    }
                } catch (Exception e) {

                }
            }
        }
    }

    private void processPendingChunkloaderActivations() {
        if (pendingChunkloaderActivations.isEmpty()) {
            return;
        }
        
        List<ChunkKey> initializedKeys = new ArrayList<>();
        
        for (Map.Entry<ChunkKey, PendingChunkloaderState> pendingEntry : pendingChunkloaderActivations.entrySet()) {
            PendingChunkloaderState state = pendingEntry.getValue();
            if (state.ticksUntilNextAttempt() > 0) {
                state.setTicksUntilNextAttempt(state.ticksUntilNextAttempt() - 1);
                continue;
            }
            
            ChunkloaderTarget entry = state.entry();
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null) {

                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
                continue;
            }
            
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            try {
                world.getChunk(chunkPos.x, chunkPos.z);
            } catch (Exception e) {

                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
                continue;
            }
            
            try {
                if (entry.enabled()) {
                    activateChunkloader(entry, world);
                    initializedKeys.add(pendingEntry.getKey());

                } else {
                    initializedKeys.add(pendingEntry.getKey());

                }
            } catch (Exception e) {

                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
            }
        }
        
        for (ChunkKey key : initializedKeys) {
            pendingChunkloaderActivations.remove(key);
        }
    }
    
    private void ensureChunksLoaded() {
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        for (ChunkloaderTarget entry : entries) {
            ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null) continue;
            if (pendingChunkloaderActivations.containsKey(key)) {
                continue;
            }
            
            if (entry.enabled()) {
                if (!activeTargets.containsKey(key)) {
                    try {
                        activateChunkloader(entry, world);
                    } catch (Exception e) {

                    }
                } else {
                    ChunkloaderTarget activeEntry = activeTargets.get(key);
                    if (activeEntry != null && activeEntry.chunkRadius() != entry.chunkRadius()) {

                        deactivateChunkloader(key);
                        try {
                            activateChunkloader(entry, world);
                        } catch (Exception e) {

                        }
                    } else {
                        updateMarkerForChunkloader(key);
                    }
                }
            } else {
                if (activeTargets.containsKey(key)) {
                    deactivateChunkloader(key);
                }
            }
        }
        
        Set<ChunkKey> configuredKeys = new HashSet<>();
        for (ChunkloaderTarget entry : entries) {
            configuredKeys.add(new ChunkKey(entry.chunkX(), entry.chunkZ()));
        }
        
        for (ChunkKey key : new HashSet<>(activeTargets.keySet())) {
            if (!configuredKeys.contains(key)) {

                deactivateChunkloader(key);
            }
        }
    }
    
    public void loadPersistentChunkloaders() {

        String currentWorldName = null;
        try {
            if (server != null && server.getWorldData() != null) {
                currentWorldName = server.getWorldData().getLevelName();
            }
        } catch (Exception e) {

        }

        cleanup();
        
        Path expectedConfigPath = determineConfigPath(server);
        Path currentConfigPath = config.getConfigPath();

        if (expectedConfigPath != null && !expectedConfigPath.equals(currentConfigPath)) {

        } else if (currentWorldName != null) {
            String storedWorldName = getStoredWorldName();
            if (storedWorldName == null) {

            } else if (!currentWorldName.equals(storedWorldName)) {

            } else {

            }
        } else {

        }
        
        ChunkloaderConfig newConfig = ChunkloaderConfig.load(server);
        
        this.config = newConfig;
        ChunkloaderForgeMod.setConfig(newConfig);

        storeWorldName(currentWorldName);
        
        currentConfigPath = newConfig.getConfigPath();
        
        if (expectedConfigPath != null && !expectedConfigPath.equals(currentConfigPath)) {

            return;
        }
        
        Set<String> loadedDimensions = new HashSet<>();
        for (ServerLevel world : server.getAllLevels()) {
            loadedDimensions.add(getDimensionFromWorld(world));
        }

        Map<String, Integer> dimensionCounts = new HashMap<>();

        pendingChunkloaderActivations.clear();
        
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (!loadedDimensions.contains(entry.dimension())) {

                continue;
            }
            
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null) {

                continue;
            }

            scheduleChunkloaderInitialization(entry, PENDING_ACTIVATION_INITIAL_DELAY_TICKS);
                    dimensionCounts.put(entry.dimension(), dimensionCounts.getOrDefault(entry.dimension(), 0) + 1);
        }
    }
    
    public void savePersistentChunkloaders() {

    }
    
    public void cleanup() {
        if (server != null && server.getPlayerList() != null) {
            try {
                List<ServerPlayer> allPlayers = new ArrayList<>(server.getPlayerList().getPlayers());

                for (ServerPlayer player : allPlayers) {
                    if (player instanceof ChunkloaderFakePlayer fakePlayer) {
                        try {
                            fakePlayer.despawn();
                        } catch (Exception e) {

                        }
                    }
                }
            } catch (Exception e) {

            }
        }
        
        if (server != null) {
            for (ServerLevel world : server.getAllLevels()) {
                try {
                    net.minecraft.world.phys.AABB worldBox = new net.minecraft.world.phys.AABB(
                        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                    );
                    List<Entity> fakePlayersToRemove = new ArrayList<>();
                    for (Entity entity : world.getEntities().getAll()) {
                        if (!(entity instanceof ServerPlayer)) continue;
                        if (!worldBox.contains(entity.getX(), entity.getY(), entity.getZ())) continue;
                        if (entity instanceof ChunkloaderFakePlayer) {
                            fakePlayersToRemove.add(entity);
                        } else if (entity instanceof ServerPlayer player) {
                            String playerName = player.getName().getString();
                            if (playerName != null && playerName.matches("^(fakeplayer|chunkplayer)\\d+$")) {
                                if (player.connection == null) {
                                    fakePlayersToRemove.add(entity);

                                }
                            }
                        }
                    }
                    for (Entity entity : fakePlayersToRemove) {
                        try {
                            if (entity instanceof ChunkloaderFakePlayer fakePlayer) {
                                fakePlayer.despawn();
                            } else if (entity instanceof ServerPlayer player) {
                                if (server.getPlayerList() != null) {
                                    server.getPlayerList().remove(player);
                                }
                                if (player.connection != null) {
                                    player.connection.disconnect(Component.literal("chunkloader cleanup"));
                                }
                            }
                        } catch (Exception e) {

                        }
                    }
                } catch (Exception e) {

                }
            }
        }
        
        List<ChunkKey> keys = new ArrayList<>(activeTargets.keySet());
        for (ChunkKey key : keys) {
            try {
                ChunkloaderTarget entry = activeTargets.get(key);
                if (entry != null) {
                    ServerLevel world = getWorldByDimension(entry.dimension());
                    if (world != null) {
                        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
                        int radius = entry.chunkRadius();
                        world.getChunkSource().removeTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
                    }
                }
                
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
                if (fakePlayer != null) {
                    try {
                        fakePlayer.despawn();
                    } catch (Exception e) {
                    }
                }
                
                UUID markerId = markerEntities.remove(key);
                if (markerId != null) {
                    markerToChunkKey.remove(markerId);
                    if (entry != null) {
                        ServerLevel world = getWorldByDimension(entry.dimension());
                        if (world != null) {
                            Entity entity = world.getEntity(markerId);
                            if (entity != null) {
                                entity.remove(Entity.RemovalReason.DISCARDED);
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
        
        for (ServerLevel world : server.getAllLevels()) {
            List<Entity> entitiesToRemove = new ArrayList<>();
            for (ChunkloaderFakePlayer fakePlayer : activeFakePlayers.values()) {
                if (fakePlayer.level() == world && fakePlayer.isVisibleAsMarker()) {
                    entitiesToRemove.add(fakePlayer);
                }
            }
            for (Entity entity : entitiesToRemove) {
                try {
                    if (entity instanceof ChunkloaderFakePlayer fakePlayer) {
                        fakePlayer.despawn();
                    } else {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                    }
                } catch (Exception e) {

                }
            }
        }
        
        activeTargets.clear();
        activeFakePlayers.clear();
        markerEntities.clear();
        markerToChunkKey.clear();
        visualizationActive.clear();
        visualization3DActive.clear();
        pendingChunkloaderActivations.clear();

    }
    
    
    public boolean addChunkloader(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        String name = config.generateNextName(true);
        return addChunkloader(chunkX, chunkZ, blockPos, name);
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world) {
        return addChunkloader(chunkX, chunkZ, blockPos, name, world, null);
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world, String ownerName) {
        if (config.getChunkEntries().size() >= config.getMaxChunkloaders()) {

            return false;
        }
        
        if (name == null) {
            boolean isFakePlayer = true;
            name = config.generateNextName(isFakePlayer);

        }
        
        if (config.hasEntryByName(name)) {

            return false;
        }
        
        String dimension = getDimensionFromWorld(world);
        
        ChunkloaderTarget existingEntry = config.getEntry(chunkX, chunkZ);
        if (existingEntry != null && existingEntry.dimension().equals(dimension)) {

            return false;
        }
        
        int defaultRadius = 0;
        if (isPositionCoveredByOtherChunkloader(chunkX, chunkZ, defaultRadius, dimension, null)) {

            return false;
        }
        boolean success = config.addOrUpdateEntry(chunkX, chunkZ, blockPos.getX(), blockPos.getY(), blockPos.getZ(), name, dimension, null, null, ownerName);
        if (!success) {
            return false;
        }
        
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry != null) {
            try {
                activateChunkloader(entry, world);
                ChunkloaderNetworking.invalidateChunkCache();
                return true;
            } catch (Exception e) {

                config.removeEntry(chunkX, chunkZ);
                return false;
            }
        }
        return false;
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return false;
        return addChunkloader(chunkX, chunkZ, blockPos, name, overworld);
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos) {
        return addChunkloader(chunkX, chunkZ, blockPos, null);
    }
    
    public boolean removeChunkloaderByName(String name) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry != null) {
            return removeChunkloader(entry.chunkX(), entry.chunkZ());
        }
        return false;
    }
    
    public boolean removeChunkloader(int x, int z) {
        boolean removed = config.removeEntry(x, z);
        
        if (removed) {
            ChunkKey key = new ChunkKey(x, z);
            cancelPendingChunkloader(key);
            deactivateChunkloader(key);
            visualizationActive.remove(key);
            visualization3DActive.remove(key);

        }
        
        return removed;
    }
    
    private void activateChunkloader(ChunkloaderTarget entry, ServerLevel world) {
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        cancelPendingChunkloader(key);
        
        ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
        if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.ChatFormatting color;
            if (entry.enabled()) {
                if (entry.allowMobSpawning()) {
                    color = net.minecraft.ChatFormatting.GREEN;
                } else {
                    color = net.minecraft.ChatFormatting.BLUE;
                }
            } else {
                color = net.minecraft.ChatFormatting.RED;
            }
            Component nameText = Component.literal(displayName).withStyle(color);
            existingFakePlayer.setCustomName(nameText);
            existingFakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
            boolean nameVisible = entry.nameVisible();
            existingFakePlayer.setCustomNameVisible(nameVisible);
            existingFakePlayer.setVisibleAsMarker(true);
            
            String plainName = displayName;
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, nameVisible);
            
            updateFakePlayerTeam(existingFakePlayer, entry);
            
            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    oldWorld.getChunkSource().removeTicketWithRadius(getChunkTicketType(oldEntry.allowMobSpawning()), oldChunkPos, oldRadius);
                }
            }
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            world.getChunkSource().addTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
            activeTargets.put(key, entry);
            
            updateMarkerForChunkloader(key);
            
            return;
        }
        
        removeMarkerForChunkloader(key);
        
        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget oldEntry = activeTargets.get(key);
            ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
            if (oldWorld != null) {
                ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                oldWorld.getChunkSource().removeTicketWithRadius(getChunkTicketType(oldEntry.allowMobSpawning()), oldChunkPos, oldEntry.chunkRadius());
            }
        }
        
        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
        int radius = entry.chunkRadius();
        
        try {
            world.getChunkSource().addTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
            activeTargets.put(key, entry);
            
                ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                    server,
                    world,
                    createProfile(entry)
                );
                fakePlayer.setPos(entry.blockX() + 0.5, entry.blockY(), entry.blockZ() + 0.5);
                fakePlayer.setYRot(0.0F);
                fakePlayer.setXRot(0.0F);
            
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.ChatFormatting color;
            if (entry.allowMobSpawning()) {
                color = net.minecraft.ChatFormatting.GREEN;
            } else {
                color = net.minecraft.ChatFormatting.BLUE;
            }
            final Component nameText = Component.literal(displayName).withStyle(color);
            final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
            final ServerLevel finalWorld = world;
            
            fakePlayer.setCustomName(nameText);
            fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
            boolean nameVisible = entry.nameVisible();
            fakePlayer.setCustomNameVisible(nameVisible);
            
            fakePlayer.setVisibleAsMarker(true);
            
            String plainName = displayName;
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, nameVisible);
            
            updateFakePlayerTeam(fakePlayer, entry);
                
                try {
                    fakePlayer.spawn();
                
                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                            finalFakePlayer.setCustomName(nameText);
                            finalFakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
                            finalFakePlayer.setCustomNameVisible(entry.nameVisible());
                            finalFakePlayer.setVisibleAsMarker(true);
                            
                            forceEntitySync(finalFakePlayer);
                            
                            updateFakePlayerTeam(finalFakePlayer, entry);
                        }
                    });
                });
                
                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
                fakePlayer.setCustomNameVisible(entry.nameVisible());
                fakePlayer.setVisibleAsMarker(true);
                fakePlayer.setInvisible(false);
                
                forceEntitySync(fakePlayer);
                
                updateFakePlayerTeam(fakePlayer, entry);
                } catch (Exception e) {
                    world.getChunkSource().removeTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
                    activeTargets.remove(key);

                    throw new RuntimeException("Failed to spawn fake player", e);
                }
                
                activeFakePlayers.put(key, fakePlayer);
            
            UUID fakePlayerUuid = fakePlayer.getUUID();
            markerEntities.put(key, fakePlayerUuid);
            markerToChunkKey.put(fakePlayerUuid, key);
            
            BlockPos blockPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
            spawnParticles(world, blockPos, true, entry.allowMobSpawning());
            playSound(world, blockPos, true);
        } catch (Exception e) {

            activeTargets.remove(key);
            activeFakePlayers.remove(key);
            markerEntities.remove(key);
            throw e;
        }
    }
    
    private void deactivateChunkloader(ChunkKey key) {
        cancelPendingChunkloader(key);
        ChunkloaderTarget entry = activeTargets.remove(key);
        if (entry == null) {
            return;
        }
        
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null) {
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            world.getChunkSource().removeTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
        }
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
        if (fakePlayer != null) {
            fakePlayer.despawn();
        }
        removeMarkerForChunkloader(key);
        
        visualizationActive.remove(key);
        visualization3DActive.remove(key);
        
        ChunkloaderNetworking.invalidateChunkCache();
    }
    
    public List<ChunkloaderTarget> getActiveChunkloaderEntries() {
        return new ArrayList<>(config.getChunkEntries());
    }
    
    public record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
        @Override
        public int compareTo(ChunkKey other) {
            int cmp = Integer.compare(this.x, other.x);
            return cmp != 0 ? cmp : Integer.compare(this.z, other.z);
        }
    }
    
    public boolean allowsMobSpawning(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null) {
            return false;
        }
        
        UUID fakePlayerUuid = fakePlayer.getUUID();
        ChunkKey key = markerToChunkKey.get(fakePlayerUuid);
        if (key == null) {
            for (Map.Entry<ChunkKey, ChunkloaderFakePlayer> entry : activeFakePlayers.entrySet()) {
                if (entry.getValue() == fakePlayer) {
                    key = entry.getKey();
                    break;
                }
            }
        }
        
        if (key == null) {
            return false;
        }
        
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        
        if (entry == null) {
            return false;
        }
        
        return entry.allowMobSpawning();
    }
    
    public boolean needsRandomTicks(int chunkX, int chunkZ, String dimension) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        ChunkloaderTarget entry = activeTargets.get(key);
        if (entry == null) {
            return false;
        }
        return entry.enabled() && !entry.allowMobSpawning() && entry.dimension().equals(dimension);
    }
    
    public boolean isChunkplayerRandomTickChunk(int chunkX, int chunkZ, String dimension) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        ChunkloaderTarget entry = activeTargets.get(key);
        if (entry == null) {
            return false;
        }
        return entry.enabled() && !entry.allowMobSpawning() && entry.dimension().equals(dimension);
    }

    public boolean isFakeplayerRandomTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || !target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int radius = target.chunkRadius();
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= radius && dz <= radius) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyActiveLoaderInDimension(String dimension) {
        for (ChunkloaderTarget target : activeTargets.values()) {
            if (target != null && target.enabled() && target.dimension().equals(dimension)) {
                return true;
            }
        }
        return false;
    }
    
    public Set<ChunkKey> getChunkplayerChunksForRandomTicks(String dimension) {
        Set<ChunkKey> result = new HashSet<>();
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (target.enabled() && !target.allowMobSpawning() && target.dimension().equals(dimension)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    private ChatFormatting determineFakePlayerColor(ChunkloaderTarget entry) {
        if (entry.allowMobSpawning()) {
            return ChatFormatting.GREEN;
        }
        return ChatFormatting.BLUE;
    }

    private String determineDimensionPrefix(String dimension) {
        if (dimension == null) {
            return "O";
        }
        String dim = dimension.toLowerCase(Locale.ROOT);
        if (dim.contains("nether")) {
            return "N";
        }
        if (dim.contains("end")) {
            return "E";
        }
        return "O";
    }

    private TextColor dimensionColor(String dimension) {
        if (dimension == null) {
            return TextColor.fromRgb(0x55FF55);
        }
        String dim = dimension.toLowerCase(Locale.ROOT);
        if (dim.contains("nether")) {
            return TextColor.fromRgb(0xFF5555);
        }
        if (dim.contains("end")) {
            return TextColor.fromRgb(0x8A2BE2);
        }
        return TextColor.fromRgb(0x55FF55);
    }

    private Component buildTabListName(String displayName, ChatFormatting nameColor, String dimension) {
        TextColor dimColor = dimensionColor(dimension);
        String prefix = "[" + determineDimensionPrefix(dimension) + "] ";
        return Component.literal(prefix).withStyle(style -> style.withColor(dimColor))
            .append(Component.literal(displayName).withStyle(nameColor));
    }

    private String buildFakePlayerDisplayName(ChunkloaderTarget entry, ChunkKey key) {
        if (entry == null) {
            return "Chunkloader";
        }
        if (entry.name() != null) {
            return entry.name();
        }
        String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        return prefix + key.x() + "_" + key.z();
    }

    private void applyFakePlayerMetadata(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry, ChunkKey key) {
        String displayName = buildFakePlayerDisplayName(entry, key);
        ChatFormatting color = determineFakePlayerColor(entry);
        Component nameText = Component.literal(displayName).withStyle(color);
        fakePlayer.setCustomName(nameText);
        boolean nameVisible = entry.nameVisible();
        fakePlayer.setCustomNameVisible(nameVisible);
        fakePlayer.setVisibleAsMarker(true);
        fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
        de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, displayName, nameVisible);
        updateFakePlayerTeam(fakePlayer, entry);
    }

    private void updateFakePlayerTeam(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry) {
        if (server == null || server.getScoreboard() == null) {
            return;
        }
        
        try {
            net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
            net.minecraft.ChatFormatting teamColor;
            
            if (entry.allowMobSpawning()) {
                teamColor = net.minecraft.ChatFormatting.GREEN;
            } else {
                teamColor = net.minecraft.ChatFormatting.BLUE;
            }
            
            String teamName = "chunkloader_" + teamColor.getName().toLowerCase(java.util.Locale.ROOT);
            
            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                if (team != null) {
                    team.setColor(teamColor);
                    team.setDisplayName(Component.literal("Chunkloader " + teamColor.getName()).withStyle(teamColor));
                }
            } else {
                team.setColor(teamColor);
            }
            
            if (team != null) {
                String playerName = fakePlayer.getName().getString();
                if (playerName != null && !playerName.isEmpty()) {
                    net.minecraft.world.scores.PlayerTeam currentTeam = scoreboard.getPlayerTeam(playerName);
                    if (currentTeam != team) {
                        if (currentTeam != null) {
                            scoreboard.removePlayerFromTeam(playerName, currentTeam);
                        }
                        scoreboard.addPlayerToTeam(playerName, team);
                    }
                }
            }
        } catch (Exception e) {

        }
    }
    
    private void forceEntitySync(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null) return;
        
        UUID fakePlayerUuid = fakePlayer.getUUID();
        
        if (syncingFakePlayers.contains(fakePlayerUuid)) {
            return;
        }
        
        try {
            if (fakePlayer.level() instanceof ServerLevel serverWorld) {
                syncingFakePlayers.add(fakePlayerUuid);
                
                EntitySyncUtil.syncMetadataImmediately(serverWorld, fakePlayer);
                
                server.execute(() -> {
                    server.execute(() -> {
                        syncingFakePlayers.remove(fakePlayerUuid);
                    });
                });
            }
        } catch (Exception e) {

            syncingFakePlayers.remove(fakePlayerUuid);
        }
    }
    
    private GameProfile createProfile(ChunkloaderTarget entry) {
        String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        String name = entry.name() != null ? entry.name() : (prefix + entry.chunkX() + "_" + entry.chunkZ());
        String data = "chunkloader:" + entry.chunkX() + ":" + entry.chunkZ() + ":" + name;
        UUID uuid = UUID.nameUUIDFromBytes(data.getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, name);
    }

    private void spawnMarkerForChunkloader(ChunkKey key, ServerLevel world, BlockPos pos) {
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) return;
        
        if (!entry.enabled()) {
            return;
        }
        
        final ChunkloaderTarget finalEntry = entry;
        final ServerLevel finalWorld = world;
        
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        
        if (fakePlayer != null) {
            applyFakePlayerMetadata(fakePlayer, finalEntry, key);
            
            UUID fakePlayerUuid = fakePlayer.getUUID();
            markerEntities.put(key, fakePlayerUuid);
            markerToChunkKey.put(fakePlayerUuid, key);
            
            final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
            server.execute(() -> {
                if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                    applyFakePlayerMetadata(finalFakePlayer, finalEntry, key);
                    finalFakePlayer.setVisibleAsMarker(true);
                    forceEntitySync(finalFakePlayer);
                }
            });
        } else {
            fakePlayer = new ChunkloaderFakePlayer(server, world, createProfile(finalEntry));
            fakePlayer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            fakePlayer.setYRot(0.0F);
            fakePlayer.setXRot(0.0F);
            
            applyFakePlayerMetadata(fakePlayer, finalEntry, key);
            
            try {
                fakePlayer.spawn();
                activeFakePlayers.put(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.put(key, fakePlayerUuid);
                markerToChunkKey.put(fakePlayerUuid, key);
                
                final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
                server.execute(() -> {
                    if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                        applyFakePlayerMetadata(finalFakePlayer, finalEntry, key);
                        finalFakePlayer.setVisibleAsMarker(true);
                        forceEntitySync(finalFakePlayer);
                    }
                });
                
                fakePlayer.setVisibleAsMarker(true);
                fakePlayer.setInvisible(false);
                forceEntitySync(fakePlayer);
            } catch (Exception e) {

            }
        }
    }
    
    private void updateMarkerForChunkloader(ChunkKey key) {
        UUID markerId = markerEntities.get(key);
        if (markerId == null) return;
        
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) return;
        
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        if (fakePlayer != null) {
            applyFakePlayerMetadata(fakePlayer, entry, key);
            boolean nameVisible = entry.nameVisible();
            
            @SuppressWarnings("resource")
            ServerLevel serverWorld = fakePlayer.level() instanceof ServerLevel ? 
                (ServerLevel) fakePlayer.level() : null;
            if (serverWorld != null) {
                forceEntitySync(fakePlayer);
                
                final ChunkloaderFakePlayer finalFakePlayer2 = fakePlayer;
                final ServerLevel finalWorld2 = serverWorld;
                final boolean finalNameVisible2 = nameVisible;
                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer2.isAlive() && finalFakePlayer2.level() == finalWorld2) {
                            finalFakePlayer2.setCustomNameVisible(finalNameVisible2);
                            finalFakePlayer2.setVisibleAsMarker(true);
                            
                            forceEntitySync(finalFakePlayer2);
                            
                            updateFakePlayerTeam(finalFakePlayer2, entry);
                        }
                    });
                });
            } else {
                forceEntitySync(fakePlayer);
            }
            
            return;
        }
    }
    
    private void respawnMarkerForChunkloader(ChunkKey key, ChunkloaderTarget entry) {
        ChunkloaderFakePlayer existing = activeFakePlayers.remove(key);
        if (existing != null) {
            try {
                existing.despawn();
            } catch (Exception e) {

            }
        }
        
        UUID markerId = markerEntities.remove(key);
        if (markerId != null) {
            markerToChunkKey.remove(markerId);
        }
        
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null) {
            spawnMarkerForChunkloader(key, world, new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ()));
        }
    }
    
    private void removeMarkerForChunkloader(ChunkKey key) {
        UUID markerId = markerEntities.remove(key);
        if (markerId != null) {
            markerToChunkKey.remove(markerId);
        }
        
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        
        String expectedName = null;
        BlockPos expectedPos = null;
        
        if (entry != null) {
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            expectedName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            expectedPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
        } else {
            expectedName = "fakeplayer" + key.x() + "_" + key.z();
        }
        
        List<Entity> entitiesToRemove = new ArrayList<>();
        
        for (ServerLevel world : server.getAllLevels()) {
            if (expectedPos != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null) {
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    if ((markerId != null && fakePlayerUuid.equals(markerId)) ||
                        (markerToChunkKey.containsKey(fakePlayerUuid) && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
                        entitiesToRemove.add(fakePlayer);
                    }
                }
            } else if (expectedName != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null && fakePlayer.level() == world) {
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    if ((markerId != null && fakePlayerUuid.equals(markerId)) ||
                        (markerToChunkKey.containsKey(fakePlayerUuid) && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
                        entitiesToRemove.add(fakePlayer);
                    }
                }
            }
        }
        
        for (Entity entity : entitiesToRemove) {
            UUID entityUuid = entity.getUUID();
            markerToChunkKey.remove(entityUuid);
            if (markerEntities.get(key) != null && markerEntities.get(key).equals(entityUuid)) {
                markerEntities.remove(key);
            }
            try {
                if (entity instanceof ChunkloaderFakePlayer fakePlayer) {
                    fakePlayer.despawn();
                    activeFakePlayers.remove(key);
                } else {
                entity.remove(Entity.RemovalReason.DISCARDED);
                }
            } catch (Exception e) {

            }
        }
        
        if (!entitiesToRemove.isEmpty()) {

        }
    }
    
    public void handleMarkerDestroyed(UUID markerUuid) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key != null) {
            markerToChunkKey.remove(markerUuid);
            markerEntities.remove(key);
            
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry == null) {
                entry = config.getEntry(key.x(), key.z());
            }
            
            if (entry != null) {
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world != null) {
                    boolean hasOtherMarker = false;
                    ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
                    if (existingFakePlayer != null && !existingFakePlayer.getUUID().equals(markerUuid)) {
                        if (existingFakePlayer.isVisibleAsMarker()) {
                            hasOtherMarker = true;
                        }
                    }
                    
                    if (!hasOtherMarker) {

                        ChunkloaderFakePlayer fakePlayerToDespawn = activeFakePlayers.remove(key);
                        if (fakePlayerToDespawn != null) {
                            try {
                                fakePlayerToDespawn.despawn();

                            } catch (Exception e) {

                            }
                        }
                        
                        UUID removedMarkerUuid = markerEntities.remove(key);
                        if (removedMarkerUuid != null) {
                            markerToChunkKey.remove(removedMarkerUuid);
                        }
                        
                        if (activeTargets.containsKey(key)) {
                            deactivateChunkloader(key);
                        }
                        
                        config.updateEntryEnabled(key.x(), key.z(), false);
                        cancelPendingChunkloader(key);
                        ChunkloaderNetworking.invalidateChunkCache();

                    } else {

                    }
                }
            }
        }
    }
    
    public boolean isChunkloaderMarker(UUID markerUuid) {
        return markerToChunkKey.containsKey(markerUuid);
    }
    
    public boolean ensureMarkerMapping(UUID markerUuid, ChunkloaderFakePlayer fakePlayer) {
        if (markerUuid == null) {
            return false;
        }
        if (markerToChunkKey.containsKey(markerUuid)) {
            return true;
        }
        if (fakePlayer == null || !(fakePlayer.level() instanceof ServerLevel world)) {
            return false;
        }

        BlockPos pos = fakePlayer.blockPosition();
        String dim = getDimensionFromWorld(world);

        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (entry == null) continue;
            if (entry.blockX() != pos.getX() || entry.blockY() != pos.getY() || entry.blockZ() != pos.getZ()) {
                continue;
            }
            if (dim != null && entry.dimension() != null && !dim.equals(entry.dimension())) {
                continue;
            }

            ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
            markerToChunkKey.put(markerUuid, key);
            markerEntities.put(key, markerUuid);
            return true;
        }

        return false;
    }
    
    public void removeChunkloaderByMarkerUuid(UUID markerUuid) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key != null) {
            removeChunkloader(key.x(), key.z());
        }
    }

    public void openChunkMap(UUID markerUuid, ServerPlayer player) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key == null) {

            for (ServerLevel world : server.getAllLevels()) {
                Entity entity = world.getEntity(markerUuid);
                if (entity instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
                    BlockPos pos = fakePlayer.blockPosition();
                    for (ChunkloaderTarget entry : config.getChunkEntries()) {
                        if (entry.blockX() == pos.getX() && entry.blockY() == pos.getY() && entry.blockZ() == pos.getZ()) {
                            key = new ChunkKey(entry.chunkX(), entry.chunkZ());
                            markerToChunkKey.put(markerUuid, key);
                            markerEntities.put(key, markerUuid);
                            break;
                        }
                    }
                    if (key != null) break;
                }
            }
            if (key == null) {

                return;
            }
        }
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) {

            return;
        }
        
        if (!entry.enabled()) {

            return;
        }
        
        try {
            ChunkMapData data = buildChunkMapData(entry);
            ChunkloaderNetworking.sendOpenChunkMap(player, data);
        } catch (Exception e) {

        }
    }
    
    public boolean toggleChunkloaderByMarkerUuid(UUID markerUuid) {
        long currentTime = System.currentTimeMillis();
        Long lastToggle = lastToggleTime.get(markerUuid);
        if (lastToggle != null && (currentTime - lastToggle) < TOGGLE_COOLDOWN_MS) {
            return false;
        }
        
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key == null) {
            return false;
        }
        
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            return false;
        }
        
        lastToggleTime.put(markerUuid, currentTime);
        
        return toggleChunkloaderByName(entry.name());
    }
    
    public ChunkloaderTarget getEntryByMarkerUuid(UUID markerUuid) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key == null) {
            return null;
        }
        return config.getEntry(key.x(), key.z());
    }
    
    public boolean toggleChunkloaderByName(String name) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world == null) {
            return false;
        }
        
        boolean newEnabled = !entry.enabled();
        BlockPos pos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
        
        config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), newEnabled);
        
        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ());
        if (updatedEntry == null) {
            return false;
        }
        
        if (newEnabled) {
            ChunkPos chunkPos = new ChunkPos(updatedEntry.chunkX(), updatedEntry.chunkZ());
            int radius = updatedEntry.chunkRadius();
            
            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    oldWorld.getChunkSource().removeTicketWithRadius(getChunkTicketType(oldEntry.allowMobSpawning()), oldChunkPos, oldRadius);
                }
            }
            
            world.getChunkSource().addTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, radius);
            activeTargets.put(key, updatedEntry);
            
            ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                updateMarkerForChunkloader(key);
            } else {
                ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                    server,
                    world,
                    createProfile(updatedEntry)
                );
                fakePlayer.setPos(updatedEntry.blockX() + 0.5, updatedEntry.blockY(), updatedEntry.blockZ() + 0.5);
                fakePlayer.setYRot(0.0F);
                fakePlayer.setXRot(0.0F);
                
                String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                String displayName = updatedEntry.name() != null ? updatedEntry.name() : (prefix + key.x() + "_" + key.z());
                net.minecraft.ChatFormatting color;
                if (updatedEntry.enabled()) {
                    if (updatedEntry.allowMobSpawning()) {
                        color = net.minecraft.ChatFormatting.GREEN;
                    } else {
                        color = net.minecraft.ChatFormatting.BLUE;
                    }
            } else {
                color = net.minecraft.ChatFormatting.RED;
            }
                Component nameText = Component.literal(displayName).withStyle(color);
                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, updatedEntry.dimension()));
                fakePlayer.setCustomNameVisible(updatedEntry.nameVisible());
                fakePlayer.setVisibleAsMarker(true);
                
                String plainName = displayName;
                de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, updatedEntry.nameVisible());
                
                updateFakePlayerTeam(fakePlayer, updatedEntry);
                
                try {
                    fakePlayer.spawn();
                    activeFakePlayers.put(key, fakePlayer);
                    
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    markerEntities.put(key, fakePlayerUuid);
                    markerToChunkKey.put(fakePlayerUuid, key);
                } catch (Exception e) {

                }
            }
            spawnParticles(world, pos, true, updatedEntry.allowMobSpawning());
            playSound(world, pos, true);
        } else {
            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.remove(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    oldWorld.getChunkSource().removeTicketWithRadius(getChunkTicketType(oldEntry.allowMobSpawning()), oldChunkPos, oldEntry.chunkRadius());
                }
            }
            
            visualizationActive.remove(key);
            visualization3DActive.remove(key);
            
            ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
            if (fakePlayer != null) {
                try {
                    fakePlayer.despawn();
                } catch (Exception e) {

                }
            }
            
            UUID markerId = markerEntities.remove(key);
            if (markerId != null) {
                markerToChunkKey.remove(markerId);
            }
            
            String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = updatedEntry.name() != null ? updatedEntry.name() : (prefix + key.x() + "_" + key.z());
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, displayName, false);
            
            spawnParticles(world, pos, false, false);
            playSound(world, pos, false);
            
            ChunkloaderNetworking.broadcastCloseChunkMap(server);
        }
        
        ChunkloaderNetworking.invalidateChunkCache();
        
        return newEnabled;
    }
    
    public boolean setChunkloaderNameVisible(String name, boolean visible) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry != null) {
            config.updateEntryNameVisible(entry.chunkX(), entry.chunkZ(), visible);
            ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
            updateMarkerForChunkloader(key);
            return true;
        }
        return false;
    }
    
    public boolean setChunkloaderAllowMobSpawning(String name, boolean allowMobSpawning) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        
            config.updateEntryAllowMobSpawning(entry.chunkX(), entry.chunkZ(), allowMobSpawning);
            ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
            ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ());
            if (updatedEntry == null) {
                return false;
            }
            
            ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
            
            if (activeTargets.containsKey(key) && updatedEntry.enabled()) {
                activeTargets.put(key, updatedEntry);
                if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                    applyFakePlayerMetadata(existingFakePlayer, updatedEntry, key);
                    forceEntitySync(existingFakePlayer);
                    updateMarkerForChunkloader(key);
                } else {
                    ServerLevel world = getWorldByDimension(updatedEntry.dimension());
                    if (world != null) {
                        spawnMarkerForChunkloader(key, world, new BlockPos(updatedEntry.blockX(), updatedEntry.blockY(), updatedEntry.blockZ()));
                    }
                }
            } else {
                if (activeTargets.containsKey(key)) {
                    activeTargets.put(key, updatedEntry);
                }
                if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                    applyFakePlayerMetadata(existingFakePlayer, updatedEntry, key);
                    forceEntitySync(existingFakePlayer);
                } else {
                    updateMarkerForChunkloader(key);
                }
            }
        
            ChunkloaderNetworking.invalidateChunkCache();
            return true;
    }
    
    public int clearAllChunkloaders() {
        int count = config.getChunkEntries().size();
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        for (ChunkloaderTarget entry : entries) {
            removeChunkloader(entry.chunkX(), entry.chunkZ());
        }
        return count;
    }
    
    public void reloadConfig() {
        List<ChunkKey> keys = new ArrayList<>(activeTargets.keySet());
        for (ChunkKey key : keys) {
            deactivateChunkloader(key);
        }
        
        ChunkloaderConfig newConfig = ChunkloaderConfig.load(server);
        config.replaceAllEntries(newConfig.getChunkEntries());
        
        loadPersistentChunkloaders();
    }
    
    public ChunkloaderStats getStats() {
        int total = config.getChunkEntries().size();
        int enabled = 0;
        int disabled = 0;
        int loadedChunks = 0;
        
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (entry.enabled()) {
                enabled++;
                int chunksPerLoader;
                if (entry.allowMobSpawning()) {
                    int simulationDistance = entry.chunkRadius();
                    chunksPerLoader = (simulationDistance * 2 + 1) * (simulationDistance * 2 + 1);
                } else {
                    int radius = entry.chunkRadius();
                    chunksPerLoader = (radius * 2 + 1) * (radius * 2 + 1);
                }
                loadedChunks += chunksPerLoader;
            } else {
                disabled++;
            }
        }
        
        return new ChunkloaderStats(total, enabled, disabled, loadedChunks, activeFakePlayers.size());
    }
    
    public boolean setChunkloaderRadius(String name, int radius) {
        if (radius < 0 || radius > 3) {
            return false;
        }
        
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        
        int oldRadius = entry.chunkRadius();
        config.updateEntryChunkRadius(entry.chunkX(), entry.chunkZ(), radius);
        
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        
        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget activeEntry = activeTargets.get(key);
            ServerLevel world = getWorldByDimension(activeEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
                
                int effectiveOldRadius = oldRadius;
                int effectiveNewRadius = radius;
                
                world.getChunkSource().removeTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, effectiveOldRadius);
                
                world.getChunkSource().addTicketWithRadius(getChunkTicketType(entry.allowMobSpawning()), chunkPos, effectiveNewRadius);
                
                ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ());
                if (updatedEntry != null) {
                    activeTargets.put(key, updatedEntry);
                }
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        return true;
    }
    
    public int enableAllChunkloaders() {
        int count = 0;
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        ServerLevel overworld = server.overworld();
        
        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), true);
                ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
                if (overworld != null && !activeTargets.containsKey(key)) {
                    ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ());
                    if (updatedEntry != null) {
                        try {
                            activateChunkloader(updatedEntry, overworld);
                            count++;
                        } catch (Exception e) {

                        }
                    }
                }
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        return count;
    }
    
    public int disableAllChunkloaders() {
        int count = 0;
        List<ChunkKey> keys = new ArrayList<>(activeTargets.keySet());
        
        for (ChunkKey key : keys) {
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry != null && entry.enabled()) {
                config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), false);
                deactivateChunkloader(key);
                count++;
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        return count;
    }
    
    public int removeAllDisabledChunkloaders() {
        int count = 0;
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        
        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                removeChunkloader(entry.chunkX(), entry.chunkZ());
                count++;
            }
        }
        return count;
    }
    
    public int removeAllEnabledChunkloaders() {
        int count = 0;
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        
        for (ChunkloaderTarget entry : entries) {
            if (entry.enabled()) {
                removeChunkloader(entry.chunkX(), entry.chunkZ());
                count++;
            }
        }
        return count;
    }
    
    public ChunkloaderPerformanceStats getPerformanceStats() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = Runtime.getRuntime().maxMemory();
        
        int totalChunks = 0;
        
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (entry.enabled()) {
                int chunksPerLoader;
                if (entry.allowMobSpawning()) {
                    int simulationDistance = entry.chunkRadius();
                    chunksPerLoader = (simulationDistance * 2 + 1) * (simulationDistance * 2 + 1);
                } else {
                    int radius = entry.chunkRadius();
                    chunksPerLoader = (radius * 2 + 1) * (radius * 2 + 1);
                }
                totalChunks += chunksPerLoader;
            }
        }
        
        double memoryUsagePercent = maxMemory > 0 ? (usedMemory * 100.0 / maxMemory) : 0;
        
        return new ChunkloaderPerformanceStats(
            totalChunks,
            usedMemory,
            maxMemory,
            memoryUsagePercent,
            activeFakePlayers.size()
        );
    }

    public ChunkMapData buildChunkMapData(ChunkloaderTarget entry) {
        int mapDisplayRadius = entry.chunkRadius();
        int mapWidth = computeMapSize(mapDisplayRadius);
        int mapHeight = mapWidth;
        int half = (mapWidth - 1) / 2;
        int topLeftChunkX = entry.chunkX() - half;
        int topLeftChunkZ = entry.chunkZ() - half;

        List<ChunkMapCell> cells = new ArrayList<>(mapWidth * mapHeight);
        List<ChunkMapTile> tiles = new ArrayList<>(mapWidth * mapHeight);
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        ServerLevel serverLevel = getWorldByDimension(entry.dimension());
        
        int actualRadius = entry.chunkRadius();
        
        for (int row = 0; row < mapHeight; row++) {
            for (int column = 0; column < mapWidth; column++) {
                int chunkX = topLeftChunkX + column;
                int chunkZ = topLeftChunkZ + row;
                int offsetX = chunkX - entry.chunkX();
                int offsetZ = chunkZ - entry.chunkZ();
                boolean withinRange = Math.abs(offsetX) <= actualRadius && Math.abs(offsetZ) <= actualRadius;
                boolean loaded = withinRange && entry.enabled();
                
                String simulatingFakeplayerName = null;
                boolean simulated = false;
                boolean occupied = false;
                
                if (entry.allowMobSpawning()) {
                    simulated = withinRange && entry.enabled();
                    simulatingFakeplayerName = getSimulatingFakeplayerName(entries, entry, chunkX, chunkZ);
                    if (simulatingFakeplayerName != null && !simulated) {
                        occupied = true;
                    }
                } else {
                    occupied = isChunkClaimedByOther(entries, entry, chunkX, chunkZ);
                }
                
                cells.add(new ChunkMapCell(offsetX, offsetZ, loaded, withinRange, occupied, simulated, simulatingFakeplayerName));
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                int[] pixels = serverLevel != null
                    ? generateChunkTilePixels(serverLevel, chunkPos, entry.blockY())
                    : solidTile(DEFAULT_TILE_COLOR_ABGR);
                tiles.add(new ChunkMapTile(chunkX, chunkZ, pixels));
            }
        }

        String displayName = entry.name() != null ? entry.name() : String.format("Chunk (%d, %d)", entry.chunkX(), entry.chunkZ());

        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        boolean nameVisible = entry.nameVisible();
        boolean visualizeActive = isVisualizationActive(key);
        boolean visualize3DActive = isVisualization3DActive(key);
        boolean hideOtherDots = entry.hideOtherDots();
        
        boolean canIncreaseRadius = entry.chunkRadius() < 3;
        if (canIncreaseRadius) {
            int newRadius = entry.chunkRadius() + 1;
            canIncreaseRadius = !wouldRadiusIncreaseOverlap(
                entry.chunkX(), entry.chunkZ(), newRadius, entry.dimension());
        }
        
        List<de.chunkloader.network.ChunkloaderPosition> otherChunkloaders = new ArrayList<>();
        for (ChunkloaderTarget otherEntry : entries) {
            if (otherEntry == entry) {
                continue;
            }
            if (!otherEntry.enabled()) {
                continue;
            }
            if (!otherEntry.dimension().equals(entry.dimension())) {
                continue;
            }
            int offsetX = otherEntry.chunkX() - entry.chunkX();
            int offsetZ = otherEntry.chunkZ() - entry.chunkZ();
            int halfMap = (mapWidth - 1) / 2;
            if (Math.abs(offsetX) <= halfMap && Math.abs(offsetZ) <= halfMap) {
                String otherName = otherEntry.name() != null ? otherEntry.name() : 
                    String.format("%s at (%d, %d)", 
                        otherEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer",
                        otherEntry.chunkX(), otherEntry.chunkZ());
                otherChunkloaders.add(new de.chunkloader.network.ChunkloaderPosition(
                    otherEntry.chunkX(),
                    otherEntry.chunkZ(),
                    otherEntry.blockX(),
                    otherEntry.blockZ(),
                    otherName,
                    otherEntry.allowMobSpawning()
                ));
            }
        }

        return new ChunkMapData(
            displayName,
            entry.enabled(),
            entry.allowMobSpawning(),
            entry.chunkX(),
            entry.chunkZ(),
            entry.blockY(),
            entry.chunkRadius(),
            mapWidth,
            mapHeight,
            topLeftChunkX,
            topLeftChunkZ,
            entry.dimension(),
            cells,
            tiles,
            entry.chunkX(),
            entry.chunkZ(),
            entry.blockX(),
            entry.blockZ(),
            entry.name(),
            nameVisible,
            visualizeActive,
            visualize3DActive,
            canIncreaseRadius,
            otherChunkloaders,
            entry.ownerName(),
            hideOtherDots
        );
    }

    public boolean toggleChunkloaderAt(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        return toggleChunkloaderByName(entry.name());
    }

    public boolean toggleChunkloaderMobSpawning(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        return setChunkloaderAllowMobSpawning(entry.name(), !entry.allowMobSpawning());
    }

    public boolean adjustChunkloaderRadius(int chunkX, int chunkZ, int delta) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        int newRadius = entry.chunkRadius() + delta;
        newRadius = Mth.clamp(newRadius, 0, 3);
        if (newRadius == entry.chunkRadius()) {
            return false;
        }
        
        if (delta > 0 && newRadius > entry.chunkRadius()) {
            String overlappingName = getOverlappingChunkloaderName(
                entry.chunkX(), entry.chunkZ(), newRadius, entry.dimension(), entry);
            if (overlappingName != null) {

                return false;
            }
        }
        
        return setChunkloaderRadius(entry.name(), newRadius);
    }
    
    public boolean wouldRadiusIncreaseOverlap(int chunkX, int chunkZ, int newRadius, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        String overlappingName = getOverlappingChunkloaderName(
            chunkX, chunkZ, newRadius, dimension, entry);
        return overlappingName != null;
    }

    public boolean toggleChunkloaderNameVisible(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        boolean newVisible = !entry.nameVisible();
        config.updateEntryNameVisible(chunkX, chunkZ, newVisible);
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        updateMarkerForChunkloader(key);
        return true;
    }

    public boolean toggleChunkloaderVisualize(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        toggleVisualization(key);
        return true;
    }

    public boolean toggleChunkloaderVisualize3D(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        toggleVisualization3D(key);
        return true;
    }

    public boolean renameChunkloader(int chunkX, int chunkZ, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        newName = newName.trim();
        
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        
        if (newName.equals(entry.name())) {
            return false;
        }
        
        boolean success = config.updateEntryName(chunkX, chunkZ, newName);
        if (!success) {
            return false;
        }
        
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ);
        if (updatedEntry != null && activeTargets.containsKey(key)) {
            ServerLevel world = getWorldByDimension(updatedEntry.dimension());
            if (world != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null) {
                    applyFakePlayerMetadata(fakePlayer, updatedEntry, key);
                }
            }
        }
        return true;
    }

    public boolean toggleChunkloaderHideOtherDots(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        boolean newHideOtherDots = !entry.hideOtherDots();
        config.updateEntryHideOtherDots(chunkX, chunkZ, newHideOtherDots);
        return true;
    }

    public boolean resetChunkloaderToDefaults(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        
        config.updateEntryNameVisible(chunkX, chunkZ, true);
        config.updateEntryHideOtherDots(chunkX, chunkZ, false);
        
        int defaultRadius = 0;
        if (entry.chunkRadius() != defaultRadius) {
            setChunkloaderRadius(entry.name(), defaultRadius);
        }
        
        if (visualizationActive.contains(key)) {
            visualizationActive.remove(key);
        }
        if (visualization3DActive.containsKey(key)) {
            visualization3DActive.remove(key);
        }
        
        updateMarkerForChunkloader(key);
        
        return true;
    }

    private boolean isChunkClaimedByOther(List<ChunkloaderTarget> entries, ChunkloaderTarget current, int chunkX, int chunkZ) {
        for (ChunkloaderTarget other : entries) {
            if (other == current || other == null) {
                continue;
            }
            if (!other.enabled()) {
                continue;
            }
            if (!other.dimension().equals(current.dimension())) {
                continue;
            }
            
            int otherRadius = other.chunkRadius();
            
            int dx = Math.abs(other.chunkX() - chunkX);
            int dz = Math.abs(other.chunkZ() - chunkZ);
            if (dx <= otherRadius && dz <= otherRadius) {
                return true;
            }
        }
        return false;
    }

    private String getSimulatingFakeplayerName(List<ChunkloaderTarget> entries, ChunkloaderTarget current, int chunkX, int chunkZ) {
        for (ChunkloaderTarget other : entries) {
            if (other == null || !other.enabled() || !other.allowMobSpawning()) {
                continue;
            }
            if (!other.dimension().equals(current.dimension())) {
                continue;
            }
            
            int otherSimulationRadius = other.chunkRadius();
            int distanceX = Math.abs(chunkX - other.chunkX());
            int distanceZ = Math.abs(chunkZ - other.chunkZ());
            int maxDistance = Math.max(distanceX, distanceZ);
            
            if (maxDistance <= otherSimulationRadius) {
                return other.name() != null ? other.name() : String.format("Fakeplayer at (%d, %d)", other.chunkX(), other.chunkZ());
            }
        }
        return null;
    }

    
    public record SimulationStatus(
        boolean inSimulatedChunk,
        String fakeplayerName,
        int chunkX,
        int chunkZ,
        int simulationDistance,
        int distance
    ) {}
    
    public SimulationStatus getSimulationStatus(ServerPlayer player) {
        if (player == null) {
            return new SimulationStatus(false, null, 0, 0, 0, 0);
        }
        
        var world = (ServerLevel) player.level();
        if (world == null) {
            return new SimulationStatus(false, null, 0, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        
        String closestFakeplayerName = null;
        int closestDistance = Integer.MAX_VALUE;
        int closestChunkX = 0;
        int closestChunkZ = 0;
        int closestSimulationDistance = 0;
        
        for (Map.Entry<ChunkKey, ChunkloaderTarget> activeEntry : activeTargets.entrySet()) {
            ChunkloaderTarget entry = activeEntry.getValue();
            if (entry == null || !entry.enabled() || !entry.allowMobSpawning()) {
                continue;
            }
            if (!entry.dimension().equals(dimension)) {
                continue;
            }
            
            int simulationDistance = entry.chunkRadius();
            int distanceX = Math.abs(playerChunkX - entry.chunkX());
            int distanceZ = Math.abs(playerChunkZ - entry.chunkZ());
            int maxDistance = Math.max(distanceX, distanceZ);
            
            if (maxDistance <= simulationDistance && maxDistance < closestDistance) {
                closestDistance = maxDistance;
                closestFakeplayerName = entry.name() != null ? entry.name() : 
                    String.format("Fakeplayer at (%d, %d)", entry.chunkX(), entry.chunkZ());
                closestChunkX = entry.chunkX();
                closestChunkZ = entry.chunkZ();
                closestSimulationDistance = simulationDistance;
            }
        }
        
        return new SimulationStatus(
            closestFakeplayerName != null,
            closestFakeplayerName,
            closestChunkX,
            closestChunkZ,
            closestSimulationDistance,
            closestDistance != Integer.MAX_VALUE ? closestDistance : -1
        );
    }
    
    public record ChunkplayerStatus(
        boolean inLoadedChunk,
        String chunkplayerName,
        int chunkX,
        int chunkZ,
        int radius,
        int distance
    ) {}
    
    public ChunkplayerStatus getChunkplayerStatus(ServerPlayer player) {
        if (player == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }
        
        var world = (ServerLevel) player.level();
        if (world == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        
        String closestChunkplayerName = null;
        int closestDistance = Integer.MAX_VALUE;
        int closestChunkX = 0;
        int closestChunkZ = 0;
        int closestRadius = 0;
        
        for (Map.Entry<ChunkKey, ChunkloaderTarget> activeEntry : activeTargets.entrySet()) {
            ChunkloaderTarget entry = activeEntry.getValue();
            if (entry == null || !entry.enabled() || entry.allowMobSpawning()) {
                continue;
            }
            if (!entry.dimension().equals(dimension)) {
                continue;
            }
            
            int distanceX = Math.abs(playerChunkX - entry.chunkX());
            int distanceZ = Math.abs(playerChunkZ - entry.chunkZ());
            int maxDistance = Math.max(distanceX, distanceZ);
            int radius = entry.chunkRadius();
            
            if (maxDistance <= radius && maxDistance < closestDistance) {
                closestDistance = maxDistance;
                closestChunkplayerName = entry.name() != null ? entry.name() : 
                    String.format("Chunkplayer at (%d, %d)", entry.chunkX(), entry.chunkZ());
                closestChunkX = entry.chunkX();
                closestChunkZ = entry.chunkZ();
                closestRadius = radius;
            }
        }
        
        return new ChunkplayerStatus(
            closestChunkplayerName != null,
            closestChunkplayerName,
            closestChunkX,
            closestChunkZ,
            closestRadius,
            closestDistance != Integer.MAX_VALUE ? closestDistance : -1
        );
    }
    
    public String getOverlappingChunkloaderName(int chunkX, int chunkZ, int radius, String dimension, ChunkloaderTarget excludeEntry) {
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        
        for (ChunkloaderTarget other : entries) {
            if (other == null || other == excludeEntry) {
                continue;
            }
            
            if (!other.enabled()) {
                continue;
            }
            if (!other.dimension().equals(dimension)) {
                continue;
            }
            
            int otherRadius = other.chunkRadius();
            int otherMinX = other.chunkX() - otherRadius;
            int otherMaxX = other.chunkX() + otherRadius;
            int otherMinZ = other.chunkZ() - otherRadius;
            int otherMaxZ = other.chunkZ() + otherRadius;
            
            int newMinX = chunkX - radius;
            int newMaxX = chunkX + radius;
            int newMinZ = chunkZ - radius;
            int newMaxZ = chunkZ + radius;
            
            boolean overlapsX = Math.max(otherMinX, newMinX) <= Math.min(otherMaxX, newMaxX);
            boolean overlapsZ = Math.max(otherMinZ, newMinZ) <= Math.min(otherMaxZ, newMaxZ);
            
            if (overlapsX && overlapsZ) {
                return other.name() != null ? other.name() : String.format("Chunk (%d, %d)", other.chunkX(), other.chunkZ());
            }
        }
        
        return null;
    }

    private boolean isPositionCoveredByOtherChunkloader(int chunkX, int chunkZ, int radius, String dimension, ChunkloaderTarget excludeEntry) {
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        
        for (ChunkloaderTarget other : entries) {
            if (other == null || other == excludeEntry) {
                continue;
            }
            
            if (!other.enabled()) {
                continue;
            }
            if (!other.dimension().equals(dimension)) {
                continue;
            }
            
            int otherRadius = other.chunkRadius();
            
            int distanceX = Math.abs(chunkX - other.chunkX());
            int distanceZ = Math.abs(chunkZ - other.chunkZ());
            int maxDistance = Math.max(distanceX, distanceZ);
            
            if (maxDistance <= otherRadius) {

                return true;
            }
            
            if (radius > 0) {
                int newMinX = chunkX - radius;
                int newMaxX = chunkX + radius;
                int newMinZ = chunkZ - radius;
                int newMaxZ = chunkZ + radius;
                
                int otherMinX = other.chunkX() - otherRadius;
                int otherMaxX = other.chunkX() + otherRadius;
                int otherMinZ = other.chunkZ() - otherRadius;
                int otherMaxZ = other.chunkZ() + otherRadius;
                
                boolean overlapsX = Math.max(otherMinX, newMinX) <= Math.min(otherMaxX, newMaxX);
                boolean overlapsZ = Math.max(otherMinZ, newMinZ) <= Math.min(otherMaxZ, newMaxZ);
                
                if (overlapsX && overlapsZ) {

                    return true;
                }
            }
        }
        
        return false;
    }

    private int computeMapSize(int chunkRadius) {
        int desired = Math.max(9, chunkRadius * 2 + 3);
        if ((desired & 1) == 0) {
            desired++;
        }
        return Math.min(33, desired);
    }

    private static int[] generateChunkTilePixels(ServerLevel world, ChunkPos chunkPos, int yLevel) {
        if (!world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
            return solidTile(DEFAULT_TILE_COLOR_ABGR);
        }
        int[] pixels = new int[16 * 16];
        boolean sampleSameLayer = world.dimensionType().hasCeiling();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int index = localZ * 16 + localX;
                pixels[index] = sampleChunkPixel(world, chunkPos, localX, localZ, yLevel, sampleSameLayer);
            }
        }
        return pixels;
    }

    private static int sampleChunkPixel(ServerLevel world, ChunkPos chunkPos, int localX, int localZ, int yLevel, boolean sampleSameLayer) {
        int worldX = chunkPos.getMinBlockX() + localX;
        int worldZ = chunkPos.getMinBlockZ() + localZ;
        try {
            BlockPos samplePos;
            int northY = -1;
            int westY = -1;
            if (sampleSameLayer) {
                samplePos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ, 16);
                if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ - 1, 16);
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = findSurfaceWithFallback(world, worldX - 1, yLevel + 1, worldZ, 16);
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            } else {
                BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ)).below();
                if (world.isEmptyBlock(surfacePos)) {
                    surfacePos = firstSolidBlockBelowUnlimited(world, worldX, surfacePos.getY(), worldZ);
                }
                samplePos = surfacePos;
                if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ - 1)).below();
                    if (world.isEmptyBlock(northPos)) {
                        northPos = firstSolidBlockBelowUnlimited(world, worldX, northPos.getY(), worldZ - 1);
                    }
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX - 1, 0, worldZ)).below();
                    if (world.isEmptyBlock(westPos)) {
                        westPos = firstSolidBlockBelowUnlimited(world, worldX - 1, westPos.getY(), worldZ);
                    }
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            }

            if (!world.isInWorldBounds(samplePos)) {
                return ERROR_TILE_COLOR_ABGR;
            }

            BlockState state = world.getBlockState(samplePos);
            if (state.isAir()) {
                return AIR_TILE_COLOR_ABGR;
            }

            Block block = state.getBlock();
            boolean isLava = block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON;
            boolean isNether = world.dimension() == Level.NETHER;
            int rgb = state.getMapColor(world, samplePos) != null ? state.getMapColor(world, samplePos).col : 0x555555;
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            if (isLava && isNether) {
                red = 255;
                green = 100;
                blue = 0;
            }
            if (northY >= 0 || westY >= 0) {
                if ((northY >= 0 && samplePos.getY() > northY) || (westY >= 0 && samplePos.getY() > westY)) {
                    if (red == 0 && green == 0 && blue == 0) {
                        red = 3;
                        green = 3;
                        blue = 3;
                    } else {
                        if (red > 0 && red < 3) red = 3;
                        if (green > 0 && green < 3) green = 3;
                        if (blue > 0 && blue < 3) blue = 3;
                        red = Math.min((int)(red / 0.7F), 255);
                        green = Math.min((int)(green / 0.7F), 255);
                        blue = Math.min((int)(blue / 0.7F), 255);
                    }
                }
                if ((northY >= 0 && samplePos.getY() < northY) || (westY >= 0 && samplePos.getY() < westY)) {
                    red = Math.max((int)(red * 0.7F), 0);
                    green = Math.max((int)(green * 0.7F), 0);
                    blue = Math.max((int)(blue * 0.7F), 0);
                }
            }
            int argb = (255 << 24) | (red << 16) | (green << 8) | blue;
            return argbToAbgr(argb);
        } catch (Exception e) {

            return ERROR_TILE_COLOR_ABGR;
        }
    }

    private static BlockPos findSurfaceWithFallback(ServerLevel world, int x, int startY, int z, int maxSteps) {
        BlockPos pos = firstSolidBlockBelow(world, x, startY, z, maxSteps);
        if (!world.isInWorldBounds(pos) || world.getBlockState(pos).isAir()) {
            return firstSolidBlockBelowUnlimited(world, x, startY, z);
        }
        return pos;
    }

    private static BlockPos firstSolidBlockBelow(ServerLevel world, int x, int y, int z, int maxSteps) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int steps = 0;
        int bottomY = world.dimensionType().minY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable) && steps++ < maxSteps) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
    }

    private static BlockPos firstSolidBlockBelowUnlimited(ServerLevel world, int x, int y, int z) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int bottomY = world.dimensionType().minY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable)) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
    }

    private static int[] solidTile(int colorAbgr) {
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, colorAbgr);
        return pixels;
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0x000000FF) | ((argb << 16) & 0x00FF0000);
    }
    
    public record ChunkloaderStats(int total, int enabled, int disabled, int loadedChunks, int activeFakePlayers) {}
    public record ChunkloaderPerformanceStats(
        int totalLoadedChunks,
        long usedMemory,
        long maxMemory,
        double memoryUsagePercent,
        int activeFakePlayers
    ) {}
    
    public List<DisabledChunkloaderEntry> getDisabledChunkloadersList() {
        List<DisabledChunkloaderEntry> result = new ArrayList<>();
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        
        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                result.add(new DisabledChunkloaderEntry(
                    entry.chunkX(),
                    entry.chunkZ(),
                    entry.blockX(),
                    entry.blockY(),
                    entry.blockZ(),
                    entry.name(),
                    entry.allowMobSpawning(),
                    entry.dimension(),
                    false
                ));
            }
        }
        
        return Collections.unmodifiableList(result);
    }
    
    public record DisabledChunkloaderEntry(
        int chunkX, int chunkZ,
        int blockX, int blockY, int blockZ,
        String name, boolean allowMobSpawning, String dimension, boolean isFakeplayer
    ) {}
    
    public void deleteDisabledChunkloader(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry != null && !entry.enabled()) {
            removeChunkloader(chunkX, chunkZ);
        }
    }
    
    public void restoreDisabledChunkloader(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry != null && !entry.enabled()) {
            config.updateEntryEnabled(chunkX, chunkZ, true);
            ChunkKey key = new ChunkKey(chunkX, chunkZ);
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world != null && !activeTargets.containsKey(key)) {
                ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ);
                if (updatedEntry != null) {
                    try {
                        activateChunkloader(updatedEntry, world);
                        ChunkloaderNetworking.broadcastCloseChunkMap(server);
                    } catch (Exception e) {

                    }
                }
            }
            ChunkloaderNetworking.invalidateChunkCache();
        }
    }
    
    public boolean updateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        String errorMessage = updateDisabledChunkloaderCoordsWithMessage(oldChunkX, oldChunkZ, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ);
        return errorMessage == null;
    }
    
    public String updateDisabledChunkloaderCoordsWithMessage(int oldChunkX, int oldChunkZ, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ChunkloaderTarget entry = config.getEntry(oldChunkX, oldChunkZ);
        if (entry == null) {
            return "Entry not found at the specified position.";
        }
        if (entry.enabled()) {
            return "Cannot update coordinates: Entry is enabled and must be disabled first.";
        }
        
        if (oldChunkX == newChunkX && oldChunkZ == newChunkZ && 
            entry.blockX() == newBlockX && entry.blockY() == newBlockY && entry.blockZ() == newBlockZ) {

            return "Cannot update coordinates: Coordinates are identical to the current position.";
        }
        
        if (isPositionCoveredByOtherChunkloader(newChunkX, newChunkZ, 0, entry.dimension(), entry)) {

            return "Cannot update coordinates: Position is already covered by another enabled chunkloader.";
        }
        
        ChunkloaderTarget existingAtNewPos = config.getEntry(newChunkX, newChunkZ);
        if (existingAtNewPos != null && existingAtNewPos != entry && existingAtNewPos.dimension().equals(entry.dimension())) {

            return "Cannot update coordinates: Position is already occupied by another chunkloader.";
        }
        
        boolean success = config.addOrUpdateEntry(
            newChunkX, newChunkZ,
            newBlockX, newBlockY, newBlockZ,
            entry.name(),
            entry.dimension(),
            entry,
            false
        );
        
        if (!success) {

            return "Failed to update coordinates: The name may already exist or the position is invalid.";
        }
        
        if (oldChunkX != newChunkX || oldChunkZ != newChunkZ) {
            config.removeEntry(oldChunkX, oldChunkZ);
        }

        return null;
    }
    
}


