package de.chunkloader.manager;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.util.EntitySyncUtil;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final ChunkTicketType CHUNK_TICKET = ChunkTicketType.FORCED;
    
    final ConcurrentMap<ServerWorld, String> dimensionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerWorld> dimensionToWorldCache = new ConcurrentHashMap<>();
    
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
        String displayName = entry.name() != null ? entry.name() : "unnamed";
        ChunkloaderMod.LOGGER.info("Scheduled chunkloader '{}' at chunk ({}, {}) for delayed initialization in {} ticks",
            displayName, key.x(), key.z(), delayTicks);
    }

    private void cancelPendingChunkloader(ChunkKey key) {
        pendingChunkloaderActivations.remove(key);
    }
    
    private ServerWorld getWorldByDimension(String dimension) {
        ServerWorld cached = dimensionToWorldCache.get(dimension);
        if (cached != null) {
            return cached;
        }
        
        try {
            for (ServerWorld world : server.getWorlds()) {
                String worldDimension = getDimensionFromWorld(world);
                if (worldDimension.equals(dimension)) {
                    dimensionToWorldCache.put(dimension, world);
                    return world;
                }
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn("Failed to get world for dimension: {}", dimension, e);
        }
        return server.getOverworld();
    }
    
    private String getDimensionFromWorld(ServerWorld world) {
        if (world == null) {
            return "unknown";
        }
        return dimensionCache.computeIfAbsent(world, w -> w.getRegistryKey().getValue().toString());
    }
    
    public static String getDimensionString(ServerWorld world) {
        if (world == null) {
            return "unknown";
        }
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            return manager.dimensionCache.computeIfAbsent(world, w -> w.getRegistryKey().getValue().toString());
        }
        return world.getRegistryKey().getValue().toString();
    }
    
    private Path determineConfigPath(MinecraftServer server) {
        try {
            if (server != null) {
                ServerWorld overworld = server.getOverworld();
                if (overworld != null) {
                    Path serverPath = server.getRunDirectory();
                    if (serverPath != null) {
                        Path savesDir = serverPath.resolve("saves");
                        if (java.nio.file.Files.exists(savesDir)) {
                            try {
                                String currentLevelName = null;
                                try {
                                    if (server.getSaveProperties() != null) {
                                        currentLevelName = server.getSaveProperties().getLevelName();
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
                                    ChunkloaderMod.LOGGER.warn("Error searching for world directory: {}", e.getMessage());
                                }
                                
                                if (mostRecentWorldDir != null) {
                                    ChunkloaderMod.LOGGER.info("Using most recently modified world directory: {} (modified: {})", 
                                        mostRecentWorldDir.getFileName(), new java.util.Date(mostRecentTime));
                                    return mostRecentWorldDir.resolve("chunkloader_config.json");
                                }
                                
                                if (currentLevelName != null && !currentLevelName.isEmpty()) {
                                    ChunkloaderMod.LOGGER.warn("Could not find world directory by modification time, using level name: {}", currentLevelName);
                                    return savesDir.resolve(currentLevelName).resolve("chunkloader_config.json");
                                }
                            } catch (Exception e) {
                                ChunkloaderMod.LOGGER.warn("Error determining config path: {}", e.getMessage());
                            }
                        } else {
                            return serverPath.resolve("world").resolve("chunkloader_config.json");
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error determining config path", e);
        }
        return null;
    }
    
    private String getStoredWorldName() {
        return storedWorldName;
    }
    
    private void storeWorldName(String worldName) {
        this.storedWorldName = worldName;
    }
    
    private void spawnParticles(ServerWorld world, BlockPos pos, boolean enabled, boolean allowMobSpawning) {
        if (world == null) return;
        
        if (enabled) {
            if (allowMobSpawning) {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.random.nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0.02);
                }
            } else {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.random.nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                    world.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0.02);
                }
            }
        } else {
            for (int i = 0; i < 10; i++) {
                double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                double y = pos.getY() + world.random.nextDouble() * 2;
                double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 2;
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
    }
    
    private void playSound(ServerWorld world, BlockPos pos, boolean enabled) {
        if (world == null) return;
        
        if (enabled) {
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.5f, 1.2f);
        } else {
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
    }
    
    public void toggleVisualization(ChunkKey key) {
        if (visualizationActive.contains(key)) {
            visualizationActive.remove(key);
        } else {
            visualizationActive.add(key);
        }
    }
    
    public boolean isVisualizationActive(ChunkKey key) {
        return visualizationActive.contains(key);
    }
    
    public void toggleVisualization3D(ChunkKey key) {
        toggleVisualization3D(key, -64, 320);
    }
    
    public void toggleVisualization3D(ChunkKey key, int minY, int maxY) {
        if (visualization3DActive.containsKey(key)) {
            visualization3DActive.remove(key);
        } else {
            visualization3DActive.put(key, new Visualization3DConfig(minY, maxY));
        }
    }
    
    public boolean isVisualization3DActive(ChunkKey key) {
        return visualization3DActive.containsKey(key);
    }
    
    private void renderChunkBorders(ServerWorld world, ChunkloaderTarget entry) {
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
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, worldX, y, z, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
        
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int worldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
            for (int x = minChunkX * ChunkloaderConstants.CHUNK_SIZE; x <= (maxChunkX + 1) * ChunkloaderConstants.CHUNK_SIZE; x += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, worldZ, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0, 
                    ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
    }
    
    private void renderChunkBorders3D(ServerWorld world, ChunkloaderTarget entry) {
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
                        world.spawnParticles(particleType, chunkWorldX, y, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.spawnParticles(particleType, chunkWorldXEnd, y, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.spawnParticles(particleType, chunkWorldX, y, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.spawnParticles(particleType, chunkWorldXEnd, y, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }
                
                if (tickCounter % 3 == 0) {
                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.spawnParticles(particleType, x, maxY, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.spawnParticles(particleType, x, maxY, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.spawnParticles(particleType, chunkWorldX, maxY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.spawnParticles(particleType, chunkWorldXEnd, maxY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    
                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.spawnParticles(particleType, x, minY, chunkWorldZ, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.spawnParticles(particleType, x, minY, chunkWorldZEnd, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.spawnParticles(particleType, chunkWorldX, minY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.spawnParticles(particleType, chunkWorldXEnd, minY, z, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0, 
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }
            }
        }
    }
    
    public void tick() {
        processPendingChunkloaderActivations();
        
        for (ChunkKey key : visualizationActive) {
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry != null && entry.enabled()) {
                ServerWorld world = getWorldByDimension(entry.dimension());
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
            if (target != null && target.enabled()) {
                ServerWorld world = getWorldByDimension(target.dimension());
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
            ServerWorld world = getWorldByDimension(dimensionEntry.getKey());
            if (world == null) {
                continue;
            }
            
            for (ChunkKey chunkKey : dimensionEntry.getValue()) {
                try {
                    ChunkPos chunkPos = new ChunkPos(chunkKey.x(), chunkKey.z());
                    net.minecraft.world.chunk.Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
                    
                    if (chunk == null || !(chunk instanceof net.minecraft.world.chunk.WorldChunk)) {
                        continue;
                    }
                    
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.debug("Error performing random ticks for chunkplayer chunk ({}, {}): {}", 
                        chunkKey.x(), chunkKey.z(), e.getMessage());
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
            String displayName = entry.name() != null ? entry.name() : "unnamed";
            ServerWorld world = getWorldByDimension(entry.dimension());
            if (world == null) {
                ChunkloaderMod.LOGGER.debug("World {} not loaded yet for chunkloader '{}', retrying in {} ticks",
                    entry.dimension(), displayName, PENDING_ACTIVATION_RETRY_TICKS);
                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
                continue;
            }
            
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            try {
                world.getChunk(chunkPos.x, chunkPos.z);
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.debug("Chunk ({}, {}) not ready for chunkloader '{}', retrying in {} ticks",
                    chunkPos.x, chunkPos.z, displayName, PENDING_ACTIVATION_RETRY_TICKS);
                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
                continue;
            }
            
            try {
                if (entry.enabled()) {
                    activateChunkloader(entry, world);
                    initializedKeys.add(pendingEntry.getKey());
                    ChunkloaderMod.LOGGER.info("Initialized chunkloader '{}' at chunk ({}, {}) after delayed activation",
                        displayName, entry.chunkX(), entry.chunkZ());
                } else {
                    initializedKeys.add(pendingEntry.getKey());
                    ChunkloaderMod.LOGGER.debug("Skipped disabled chunkloader '{}' at chunk ({}, {}) - no marker spawned",
                        displayName, entry.chunkX(), entry.chunkZ());
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Failed to initialize chunkloader '{}' at chunk ({}, {}), retrying in {} ticks",
                    displayName, entry.chunkX(), entry.chunkZ(), PENDING_ACTIVATION_RETRY_TICKS, e);
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
            ServerWorld world = getWorldByDimension(entry.dimension());
            if (world == null) continue;
            if (pendingChunkloaderActivations.containsKey(key)) {
                continue;
            }
            
            if (entry.enabled()) {
                if (!activeTargets.containsKey(key)) {
                    try {
                        activateChunkloader(entry, world);
                    } catch (Exception e) {
                        ChunkloaderMod.LOGGER.error("Failed to activate chunkloader during sync check", e);
                    }
                } else {
                    ChunkloaderTarget activeEntry = activeTargets.get(key);
                    if (activeEntry != null && activeEntry.chunkRadius() != entry.chunkRadius()) {
                        ChunkloaderMod.LOGGER.info("Chunk radius changed for chunk ({}, {}), reactivating", entry.chunkX(), entry.chunkZ());
                        deactivateChunkloader(key);
                        try {
                            activateChunkloader(entry, world);
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Failed to reactivate chunkloader after radius change", e);
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
                ChunkloaderMod.LOGGER.info("Removing orphaned chunkloader at chunk ({}, {})", key.x(), key.z());
                deactivateChunkloader(key);
            }
        }
    }
    
    public void loadPersistentChunkloaders() {
        ChunkloaderMod.LOGGER.info("Loading persistent chunkloaders...");
        
        String currentWorldName = null;
        try {
            if (server != null && server.getSaveProperties() != null) {
                currentWorldName = server.getSaveProperties().getLevelName();
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn("Could not determine current world name", e);
        }
        ChunkloaderMod.LOGGER.info("Current world name: '{}'", currentWorldName != null ? currentWorldName : "unknown");
        
        cleanup();
        
        Path expectedConfigPath = determineConfigPath(server);
        Path currentConfigPath = config.getConfigPath();
        
        ChunkloaderMod.LOGGER.info("Expected config path: {}, Current config path: {}", 
            expectedConfigPath != null ? expectedConfigPath.toString() : "null",
            currentConfigPath != null ? currentConfigPath.toString() : "null");
        
        if (expectedConfigPath != null && !expectedConfigPath.equals(currentConfigPath)) {
            ChunkloaderMod.LOGGER.info("Config path changed - reloading config from {}", expectedConfigPath);
        } else if (currentWorldName != null) {
            String storedWorldName = getStoredWorldName();
            if (storedWorldName == null) {
                ChunkloaderMod.LOGGER.info("First load for world '{}' - reloading config", currentWorldName);
            } else if (!currentWorldName.equals(storedWorldName)) {
                ChunkloaderMod.LOGGER.info("World name changed from '{}' to '{}' - reloading config", 
                    storedWorldName, currentWorldName);
            } else {
                ChunkloaderMod.LOGGER.info("Reloading config for world '{}' to ensure consistency", currentWorldName);
            }
        } else {
            ChunkloaderMod.LOGGER.info("Cannot determine world name - reloading config to be safe");
        }
        
        ChunkloaderConfig newConfig = ChunkloaderConfig.load(server);
        
        this.config = newConfig;
        ChunkloaderMod.setConfig(newConfig);
        ChunkloaderMod.LOGGER.info("Config reloaded for world '{}' - {} entries loaded", 
            currentWorldName != null ? currentWorldName : "unknown", newConfig.getChunkEntries().size());
        
        storeWorldName(currentWorldName);
        
        currentConfigPath = newConfig.getConfigPath();
        
        if (expectedConfigPath != null && !expectedConfigPath.equals(currentConfigPath)) {
            ChunkloaderMod.LOGGER.error("Config path mismatch after reload! Expected: {}, Actual: {}. Skipping load to prevent cross-world contamination.", 
                expectedConfigPath, currentConfigPath);
            return;
        }
        
        Set<String> loadedDimensions = new HashSet<>();
        for (ServerWorld world : server.getWorlds()) {
            loadedDimensions.add(getDimensionFromWorld(world));
        }
        ChunkloaderMod.LOGGER.info("Currently loaded dimensions: {}", loadedDimensions);
        
        Map<String, Integer> dimensionCounts = new HashMap<>();
        
        ChunkloaderMod.LOGGER.info("Loading {} chunkloader entries from config", config.getChunkEntries().size());
        pendingChunkloaderActivations.clear();
        
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (!loadedDimensions.contains(entry.dimension())) {
                ChunkloaderMod.LOGGER.debug("Skipping chunkloader at ({}, {}) - dimension {} not currently loaded", 
                    entry.chunkX(), entry.chunkZ(), entry.dimension());
                continue;
            }
            
            ServerWorld world = getWorldByDimension(entry.dimension());
            if (world == null) {
                ChunkloaderMod.LOGGER.warn("World for dimension {} not available, skipping chunkloader at ({}, {})", 
                    entry.dimension(), entry.chunkX(), entry.chunkZ());
                continue;
            }
            
            ChunkloaderMod.LOGGER.info("Scheduling chunkloader '{}' at chunk ({}, {}) in dimension {}", 
                entry.name() != null ? entry.name() : "unnamed", entry.chunkX(), entry.chunkZ(), entry.dimension());
            
            scheduleChunkloaderInitialization(entry, PENDING_ACTIVATION_INITIAL_DELAY_TICKS);
                    dimensionCounts.put(entry.dimension(), dimensionCounts.getOrDefault(entry.dimension(), 0) + 1);
        }
        
        int totalScheduled = dimensionCounts.values().stream().mapToInt(Integer::intValue).sum();
        ChunkloaderMod.LOGGER.info("Scheduled {} chunkloaders for delayed initialization in world '{}' across {} dimensions ({} pending entries)", 
            totalScheduled, currentWorldName != null ? currentWorldName : "unknown", dimensionCounts.size(), pendingChunkloaderActivations.size());
    }
    
    public void savePersistentChunkloaders() {
        ChunkloaderMod.LOGGER.info("Saving chunkloader data...");
    }
    
    public void cleanup() {
        ChunkloaderMod.LOGGER.info("Cleaning up chunkloaders...");
        
        int despawnedCount = 0;
        
        if (server != null && server.getPlayerManager() != null) {
            try {
                List<ServerPlayerEntity> allPlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
                ChunkloaderMod.LOGGER.debug("Found {} players in PlayerManager", allPlayers.size());
                for (ServerPlayerEntity player : allPlayers) {
                    if (player instanceof ChunkloaderFakePlayer fakePlayer) {
                        try {
                            fakePlayer.despawn();
                            despawnedCount++;
                            ChunkloaderMod.LOGGER.info("Despawned fakeplayer from PlayerManager: {}", player.getName().getString());
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Error despawning fakeplayer from PlayerManager: {}", e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.warn("Error accessing PlayerManager: {}", e.getMessage());
            }
        }
        
        if (server != null) {
            for (ServerWorld world : server.getWorlds()) {
                try {
                    net.minecraft.util.math.Box worldBox = new net.minecraft.util.math.Box(
                        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                    );
                    List<Entity> fakePlayersToRemove = new ArrayList<>();
                    for (Entity entity : world.getEntitiesByClass(ServerPlayerEntity.class, worldBox, e -> true)) {
                        if (entity instanceof ChunkloaderFakePlayer) {
                            fakePlayersToRemove.add(entity);
                        } else if (entity instanceof ServerPlayerEntity player) {
                            String playerName = player.getName().getString();
                            if (playerName != null && playerName.matches("^(fakeplayer|chunkplayer)\\d+$")) {
                                if (player.networkHandler == null) {
                                    fakePlayersToRemove.add(entity);
                                    ChunkloaderMod.LOGGER.debug("Found potential fakeplayer by name pattern: {}", playerName);
                                }
                            }
                        }
                    }
                    for (Entity entity : fakePlayersToRemove) {
                        try {
                            if (entity instanceof ChunkloaderFakePlayer fakePlayer) {
                                fakePlayer.despawn();
                                despawnedCount++;
                                ChunkloaderMod.LOGGER.info("Despawned fakeplayer from world {}: {}", 
                                    world.getRegistryKey().getValue(), entity.getName().getString());
                            } else if (entity instanceof ServerPlayerEntity player) {
                                if (server.getPlayerManager() != null) {
                                    server.getPlayerManager().remove(player);
                                }
                                if (player.networkHandler != null) {
                                    player.networkHandler.disconnect(Text.literal("chunkloader cleanup"));
                                }
                                despawnedCount++;
                                ChunkloaderMod.LOGGER.info("Despawned potential fakeplayer from world {} by name: {}", 
                                    world.getRegistryKey().getValue(), entity.getName().getString());
                            }
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Error despawning fakeplayer from world: {}", e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.warn("Error searching for fakeplayers in world {}: {}", 
                        world.getRegistryKey().getValue(), e.getMessage());
                }
            }
        }
        
        List<ChunkKey> keys = new ArrayList<>(activeTargets.keySet());
        for (ChunkKey key : keys) {
            try {
                ChunkloaderTarget entry = activeTargets.get(key);
                if (entry != null) {
                    ServerWorld world = getWorldByDimension(entry.dimension());
                    if (world != null) {
                        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
                        int radius = entry.chunkRadius();
                        world.getChunkManager().removeTicket(CHUNK_TICKET, chunkPos, radius);
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
                        ServerWorld world = getWorldByDimension(entry.dimension());
                        if (world != null) {
                            Entity entity = world.getEntity(markerId);
                            if (entity != null) {
                                entity.remove(Entity.RemovalReason.DISCARDED);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error during cleanup of chunkloader at chunk ({}, {})", key.x(), key.z(), e);
            }
        }
        
        for (ServerWorld world : server.getWorlds()) {
            List<Entity> entitiesToRemove = new ArrayList<>();
            for (ChunkloaderFakePlayer fakePlayer : activeFakePlayers.values()) {
                if (fakePlayer.getEntityWorld() == world && fakePlayer.isVisibleAsMarker()) {
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
                    ChunkloaderMod.LOGGER.error("Error removing marker entity: {}", e.getMessage(), e);
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
        
        ChunkloaderMod.LOGGER.info("Cleanup completed. Despawned {} fakeplayers, deactivated {} chunkloaders and cleared all maps", 
            despawnedCount, keys.size());
    }
    
    
    public boolean addChunkloader(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        String name = config.generateNextName(true);
        return addChunkloader(chunkX, chunkZ, blockPos, name);
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerWorld world) {
        return addChunkloader(chunkX, chunkZ, blockPos, name, world, null);
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerWorld world, String ownerName) {
        if (config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader: Maximum limit ({}) reached", config.getMaxChunkloaders());
            return false;
        }
        
        if (name == null) {
            boolean isFakePlayer = true;
            name = config.generateNextName(isFakePlayer);
            ChunkloaderMod.LOGGER.info("Generated name for chunkloader at ({}, {}): {}", chunkX, chunkZ, name);
        }
        
        if (config.hasEntryByName(name)) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader: Name '{}' already exists", name);
            return false;
        }
        
        String dimension = getDimensionFromWorld(world);
        
        ChunkloaderTarget existingEntry = config.getEntry(chunkX, chunkZ);
        if (existingEntry != null && existingEntry.dimension().equals(dimension)) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader at ({}, {}) in {}: entry already exists (enabled={})",
                chunkX, chunkZ, dimension, existingEntry.enabled());
            return false;
        }
        
        int defaultRadius = 0;
        if (isPositionCoveredByOtherChunkloader(chunkX, chunkZ, defaultRadius, dimension, null)) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader at ({}, {}): Position is already covered by another active chunkloader", chunkX, chunkZ);
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
                ChunkloaderMod.LOGGER.error("Failed to activate chunkloader at chunk ({}, {})", chunkX, chunkZ, e);
                config.removeEntry(chunkX, chunkZ);
                return false;
            }
        }
        return false;
    }
    
    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name) {
        ServerWorld overworld = server.getOverworld();
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
            ChunkloaderMod.LOGGER.info("Removed chunkloader at chunk {}, {}", x, z);
        }
        
        return removed;
    }
    
    private void activateChunkloader(ChunkloaderTarget entry, ServerWorld world) {
        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        cancelPendingChunkloader(key);
        
        ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
        if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.util.Formatting color;
            if (entry.enabled()) {
                if (entry.allowMobSpawning()) {
                    color = net.minecraft.util.Formatting.GREEN;
                } else {
                    color = net.minecraft.util.Formatting.BLUE;
                }
            } else {
                color = net.minecraft.util.Formatting.RED;
            }
            Text nameText = Text.literal(displayName).formatted(color);
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
                ServerWorld oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    oldWorld.getChunkManager().removeTicket(CHUNK_TICKET, oldChunkPos, oldRadius);
                }
            }
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            world.getChunkManager().addTicket(CHUNK_TICKET, chunkPos, radius);
            activeTargets.put(key, entry);
            
            updateMarkerForChunkloader(key);
            
            return;
        }
        
        removeMarkerForChunkloader(key);
        
        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget oldEntry = activeTargets.get(key);
            ServerWorld oldWorld = getWorldByDimension(oldEntry.dimension());
            if (oldWorld != null) {
                ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                oldWorld.getChunkManager().removeTicket(CHUNK_TICKET, oldChunkPos, oldEntry.chunkRadius());
            }
        }
        
        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
        int radius = entry.chunkRadius();
        
        try {
            world.getChunkManager().addTicket(CHUNK_TICKET, chunkPos, radius);
            activeTargets.put(key, entry);
            
                ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                    server,
                    world,
                    createProfile(entry)
                );
                fakePlayer.refreshPositionAndAngles(entry.blockX() + 0.5, entry.blockY(), entry.blockZ() + 0.5, 0.0F, 0.0F);
            
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.util.Formatting color;
            if (entry.allowMobSpawning()) {
                color = net.minecraft.util.Formatting.GREEN;
            } else {
                color = net.minecraft.util.Formatting.BLUE;
            }
            final Text nameText = Text.literal(displayName).formatted(color);
            final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
            final ServerWorld finalWorld = world;
            
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
                        if (finalFakePlayer.isAlive() && finalFakePlayer.getEntityWorld() == finalWorld) {
                            finalFakePlayer.setCustomName(nameText);
                            finalFakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
                            finalFakePlayer.setCustomNameVisible(entry.nameVisible());
                            
                            forceEntitySync(finalFakePlayer);
                            
                            updateFakePlayerTeam(finalFakePlayer, entry);
                        }
                    });
                });
                
                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
                fakePlayer.setCustomNameVisible(entry.nameVisible());
                
                forceEntitySync(fakePlayer);
                
                updateFakePlayerTeam(fakePlayer, entry);
                } catch (Exception e) {
                    world.getChunkManager().removeTicket(CHUNK_TICKET, chunkPos, radius);
                    activeTargets.remove(key);
                    ChunkloaderMod.LOGGER.error("Failed to spawn fake player: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to spawn fake player", e);
                }
                
                activeFakePlayers.put(key, fakePlayer);
            
            UUID fakePlayerUuid = fakePlayer.getUuid();
            markerEntities.put(key, fakePlayerUuid);
            markerToChunkKey.put(fakePlayerUuid, key);
            
            BlockPos blockPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
            spawnParticles(world, blockPos, true, entry.allowMobSpawning());
            playSound(world, blockPos, true);
            
            String mode = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            ChunkloaderMod.LOGGER.info("Activated {} at chunk {}, {} (block {}, {}, {})",
                mode, entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to activate chunkloader at chunk ({}, {})", entry.chunkX(), entry.chunkZ(), e);
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
        
        ServerWorld world = getWorldByDimension(entry.dimension());
        if (world != null) {
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            world.getChunkManager().removeTicket(CHUNK_TICKET, chunkPos, radius);
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
        
        UUID fakePlayerUuid = fakePlayer.getUuid();
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
    
    private Formatting determineFakePlayerColor(ChunkloaderTarget entry) {
        if (entry.allowMobSpawning()) {
            return Formatting.GREEN;
        }
        return Formatting.BLUE;
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

    private Text buildTabListName(String displayName, Formatting nameColor, String dimension) {
        TextColor dimColor = dimensionColor(dimension);
        String prefix = "[" + determineDimensionPrefix(dimension) + "] ";
        return Text.literal(prefix).styled(style -> style.withColor(dimColor))
            .append(Text.literal(displayName).formatted(nameColor));
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
        Formatting color = determineFakePlayerColor(entry);
        Text nameText = Text.literal(displayName).formatted(color);
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
        
        Scoreboard scoreboard = server.getScoreboard();
        Formatting teamColor;
        
        if (entry.allowMobSpawning()) {
            teamColor = Formatting.GREEN;
        } else {
            teamColor = Formatting.BLUE;
        }
        
        String teamName = "chunkloader_" + teamColor.getName().toLowerCase();
        
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            try {
                team = scoreboard.addTeam(teamName);
                if (team == null) {
                    return;
                }
                team.setColor(teamColor);
                team.setDisplayName(Text.literal("Chunkloader " + teamColor.getName()));
            } catch (Exception e) {
                team = scoreboard.getTeam(teamName);
                if (team == null) {
                    return;
                }
            }
        } else {
            team.setColor(teamColor);
        }
        
        String playerName = fakePlayer.getName().getString();
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        
        Team currentTeam = null;
        for (Team existingTeam : scoreboard.getTeams()) {
            if (existingTeam.getPlayerList().contains(playerName)) {
                currentTeam = existingTeam;
                break;
            }
        }
        
        if (currentTeam == team && team.getPlayerList().contains(playerName)) {
            if (team.getColor() == teamColor) {
                return;
            }
            team.setColor(teamColor);
            sendTeamUpdatePackets(team, playerName);
            return;
        }
        
        if (currentTeam != null && currentTeam != team) {
            currentTeam.getPlayerList().remove(playerName);
        }
        
        team.setColor(teamColor);
        
        if (!team.getPlayerList().contains(playerName)) {
            team.getPlayerList().add(playerName);
        }
        
        sendTeamUpdatePackets(team, playerName);
    }
    
    private void sendTeamUpdatePackets(Team team, String playerName) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        
        try {
            net.minecraft.network.packet.s2c.play.TeamS2CPacket teamPacket = 
                net.minecraft.network.packet.s2c.play.TeamS2CPacket.updateTeam(team, true);
            
            net.minecraft.network.packet.s2c.play.TeamS2CPacket playerPacket = 
                net.minecraft.network.packet.s2c.play.TeamS2CPacket.changePlayerTeam(
                    team,
                    playerName,
                    net.minecraft.network.packet.s2c.play.TeamS2CPacket.Operation.ADD
                );
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player != null && player.networkHandler != null) {
                    player.networkHandler.sendPacket(teamPacket);
                    player.networkHandler.sendPacket(playerPacket);
                }
            }
        } catch (Exception e) {
        }
    }
    
    private void forceEntitySync(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null) return;
        
        UUID fakePlayerUuid = fakePlayer.getUuid();
        
        if (syncingFakePlayers.contains(fakePlayerUuid)) {
            return;
        }
        
        try {
            if (fakePlayer.getEntityWorld() instanceof ServerWorld serverWorld) {
                syncingFakePlayers.add(fakePlayerUuid);
                
                EntitySyncUtil.syncMetadataImmediately(serverWorld, fakePlayer);
                
                server.execute(() -> {
                    server.execute(() -> {
                        syncingFakePlayers.remove(fakePlayerUuid);
                    });
                });
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.debug("Could not force entity sync: {}", e.getMessage());
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

    private void spawnMarkerForChunkloader(ChunkKey key, ServerWorld world, BlockPos pos) {
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) return;
        
        if (!entry.enabled()) {
            return;
        }
        
        final ChunkloaderTarget finalEntry = entry;
        final ServerWorld finalWorld = world;
        
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        
        if (fakePlayer != null) {
            applyFakePlayerMetadata(fakePlayer, finalEntry, key);
            
            UUID fakePlayerUuid = fakePlayer.getUuid();
            markerEntities.put(key, fakePlayerUuid);
            markerToChunkKey.put(fakePlayerUuid, key);
            
            final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
            server.execute(() -> {
                if (finalFakePlayer.isAlive() && finalFakePlayer.getEntityWorld() == finalWorld) {
                    applyFakePlayerMetadata(finalFakePlayer, finalEntry, key);
                    forceEntitySync(finalFakePlayer);
                }
            });
        } else {
            fakePlayer = new ChunkloaderFakePlayer(server, world, createProfile(finalEntry));
            fakePlayer.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            
            applyFakePlayerMetadata(fakePlayer, finalEntry, key);
            
            try {
                fakePlayer.spawn();
                activeFakePlayers.put(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUuid();
                markerEntities.put(key, fakePlayerUuid);
                markerToChunkKey.put(fakePlayerUuid, key);
                
                final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
                server.execute(() -> {
                    if (finalFakePlayer.isAlive() && finalFakePlayer.getEntityWorld() == finalWorld) {
                        applyFakePlayerMetadata(finalFakePlayer, finalEntry, key);
                        forceEntitySync(finalFakePlayer);
                    }
                });
                
                forceEntitySync(fakePlayer);
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Failed to spawn fake player marker: {}", e.getMessage(), e);
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
            ServerWorld serverWorld = fakePlayer.getEntityWorld() instanceof ServerWorld ? 
                (ServerWorld) fakePlayer.getEntityWorld() : null;
            if (serverWorld != null) {
                forceEntitySync(fakePlayer);
                
                final ChunkloaderFakePlayer finalFakePlayer2 = fakePlayer;
                final ServerWorld finalWorld2 = serverWorld;
                final boolean finalNameVisible2 = nameVisible;
                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer2.isAlive() && finalFakePlayer2.getEntityWorld() == finalWorld2) {
                            finalFakePlayer2.setCustomNameVisible(finalNameVisible2);
                            
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
                ChunkloaderMod.LOGGER.debug("Failed to despawn marker during respawn: {}", e.getMessage());
            }
        }
        
        UUID markerId = markerEntities.remove(key);
        if (markerId != null) {
            markerToChunkKey.remove(markerId);
        }
        
        ServerWorld world = getWorldByDimension(entry.dimension());
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
        
        for (ServerWorld world : server.getWorlds()) {
            if (expectedPos != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null) {
                    UUID fakePlayerUuid = fakePlayer.getUuid();
                    if ((markerId != null && fakePlayerUuid.equals(markerId)) ||
                        (markerToChunkKey.containsKey(fakePlayerUuid) && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
                        entitiesToRemove.add(fakePlayer);
                    }
                }
            } else if (expectedName != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null && fakePlayer.getEntityWorld() == world) {
                    UUID fakePlayerUuid = fakePlayer.getUuid();
                    if ((markerId != null && fakePlayerUuid.equals(markerId)) ||
                        (markerToChunkKey.containsKey(fakePlayerUuid) && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
                        entitiesToRemove.add(fakePlayer);
                    }
                }
            }
        }
        
        for (Entity entity : entitiesToRemove) {
            UUID entityUuid = entity.getUuid();
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
                ChunkloaderMod.LOGGER.warn("Error removing marker entity: {}", e.getMessage());
            }
        }
        
        if (!entitiesToRemove.isEmpty()) {
            ChunkloaderMod.LOGGER.debug("Removed {} marker(s) for chunkloader at ({}, {})", 
                entitiesToRemove.size(), key.x(), key.z());
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
                ServerWorld world = getWorldByDimension(entry.dimension());
                if (world != null) {
                    BlockPos pos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
                    double expectedX = pos.getX() + 0.5;
                    double expectedY = pos.getY();
                    double expectedZ = pos.getZ() + 0.5;
                    
                    boolean hasOtherMarker = false;
                    ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
                    if (existingFakePlayer != null && !existingFakePlayer.getUuid().equals(markerUuid)) {
                        if (existingFakePlayer.isVisibleAsMarker()) {
                            hasOtherMarker = true;
                        }
                    }
                    
                    if (!hasOtherMarker) {
                        ChunkloaderMod.LOGGER.info("Marker destroyed at ({}, {}, {}), deactivating chunkloader", 
                            expectedX, expectedY, expectedZ);
                        
                        ChunkloaderFakePlayer fakePlayerToDespawn = activeFakePlayers.remove(key);
                        if (fakePlayerToDespawn != null) {
                            try {
                                fakePlayerToDespawn.despawn();
                                ChunkloaderMod.LOGGER.info("Despawned fakeplayer marker at ({}, {})", 
                                    key.x(), key.z());
                            } catch (Exception e) {
                                ChunkloaderMod.LOGGER.error("Error despawning fakeplayer marker: {}", e.getMessage(), e);
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
                        ChunkloaderMod.LOGGER.info("Chunkloader at chunk ({}, {}) deactivated and added to disabled list", 
                            key.x(), key.z());
                    } else {
                        ChunkloaderMod.LOGGER.info("Other marker found at ({}, {}, {}), keeping chunkloader active", 
                            expectedX, expectedY, expectedZ);
                    }
                }
            }
        }
    }
    
    public boolean isChunkloaderMarker(UUID markerUuid) {
        return markerToChunkKey.containsKey(markerUuid);
    }
    
    public void removeChunkloaderByMarkerUuid(UUID markerUuid) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key != null) {
            removeChunkloader(key.x(), key.z());
        }
    }

    public void openChunkMap(UUID markerUuid, ServerPlayerEntity player) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key == null) {
            ChunkloaderMod.LOGGER.warn("Marker UUID {} not found in markerToChunkKey, searching by position", markerUuid);
            for (ServerWorld world : server.getWorlds()) {
                Entity entity = world.getEntity(markerUuid);
                if (entity instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
                    BlockPos pos = fakePlayer.getBlockPos();
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
                ChunkloaderMod.LOGGER.error("Could not find chunkloader for marker UUID {}", markerUuid);
                return;
            }
        }
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) {
            ChunkloaderMod.LOGGER.error("Could not find entry for chunk ({}, {})", key.x(), key.z());
            return;
        }
        
        if (!entry.enabled()) {
            ChunkloaderMod.LOGGER.warn("Cannot open ChunkMap for disabled chunkloader at chunk ({}, {})", key.x(), key.z());
            return;
        }
        
        try {
            ChunkMapData data = buildChunkMapData(entry);
            ChunkloaderNetworking.sendOpenChunkMap(player, data);
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to open chunk map for chunk ({}, {})", entry.chunkX(), entry.chunkZ(), e);
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
        ServerWorld world = getWorldByDimension(entry.dimension());
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
                ServerWorld oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    oldWorld.getChunkManager().removeTicket(CHUNK_TICKET, oldChunkPos, oldRadius);
                }
            }
            
            world.getChunkManager().addTicket(CHUNK_TICKET, chunkPos, radius);
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
                fakePlayer.refreshPositionAndAngles(updatedEntry.blockX() + 0.5, updatedEntry.blockY(), updatedEntry.blockZ() + 0.5, 0.0F, 0.0F);
                
                String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                String displayName = updatedEntry.name() != null ? updatedEntry.name() : (prefix + key.x() + "_" + key.z());
                net.minecraft.util.Formatting color;
                if (updatedEntry.enabled()) {
                    if (updatedEntry.allowMobSpawning()) {
                        color = net.minecraft.util.Formatting.GREEN;
                    } else {
                        color = net.minecraft.util.Formatting.BLUE;
                    }
            } else {
                color = net.minecraft.util.Formatting.RED;
            }
                Text nameText = Text.literal(displayName).formatted(color);
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
                    
                    UUID fakePlayerUuid = fakePlayer.getUuid();
                    markerEntities.put(key, fakePlayerUuid);
                    markerToChunkKey.put(fakePlayerUuid, key);
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.error("Failed to spawn fake player during toggle: {}", e.getMessage(), e);
                }
            }
            spawnParticles(world, pos, true, updatedEntry.allowMobSpawning());
            playSound(world, pos, true);
        } else {
            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.remove(key);
                ServerWorld oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    oldWorld.getChunkManager().removeTicket(CHUNK_TICKET, oldChunkPos, oldEntry.chunkRadius());
                }
            }
            
            visualizationActive.remove(key);
            visualization3DActive.remove(key);
            
            ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
            if (fakePlayer != null) {
                try {
                    fakePlayer.despawn();
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.error("Error despawning fakeplayer when disabling: {}", e.getMessage(), e);
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
        boolean nameChanged = entry.name() != null && updatedEntry.name() != null && !entry.name().equals(updatedEntry.name());
        if (nameChanged && existingFakePlayer != null && existingFakePlayer.isAlive()) {
            respawnMarkerForChunkloader(key, updatedEntry);
            existingFakePlayer = activeFakePlayers.get(key);
        }
        
        if (activeTargets.containsKey(key) && updatedEntry.enabled()) {
            activeTargets.put(key, updatedEntry);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                        updateMarkerForChunkloader(key);
                } else {
                    ServerWorld world = getWorldByDimension(updatedEntry.dimension());
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
            ServerWorld world = getWorldByDimension(activeEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
                
                int effectiveOldRadius = oldRadius;
                int effectiveNewRadius = radius;
                
                world.getChunkManager().removeTicket(CHUNK_TICKET, chunkPos, effectiveOldRadius);
                
                world.getChunkManager().addTicket(CHUNK_TICKET, chunkPos, effectiveNewRadius);
                
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
        ServerWorld overworld = server.getOverworld();
        
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
                            ChunkloaderMod.LOGGER.error("Failed to enable chunkloader at chunk ({}, {})", entry.chunkX(), entry.chunkZ(), e);
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
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        
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
            }
        }

        String displayName = entry.name() != null ? entry.name() : String.format("Chunk (%d, %d)", entry.chunkX(), entry.chunkZ());

        ChunkKey key = new ChunkKey(entry.chunkX(), entry.chunkZ());
        boolean nameVisible = entry.nameVisible();
        boolean visualizeActive = isVisualizationActive(key);
        boolean visualize3DActive = isVisualization3DActive(key);
        
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
            entry.ownerName()
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
        newRadius = MathHelper.clamp(newRadius, 0, 3);
        if (newRadius == entry.chunkRadius()) {
            return false;
        }
        
        if (delta > 0 && newRadius > entry.chunkRadius()) {
            String overlappingName = getOverlappingChunkloaderName(
                entry.chunkX(), entry.chunkZ(), newRadius, entry.dimension(), entry);
            if (overlappingName != null) {
                ChunkloaderMod.LOGGER.warn("Cannot increase radius: Would overlap with chunkloader '{}'", overlappingName);
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

    public boolean resetChunkloaderToDefaults(int chunkX, int chunkZ) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ);
        if (entry == null || entry.name() == null) {
            return false;
        }
        
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        
        config.updateEntryNameVisible(chunkX, chunkZ, true);
        
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
        int distance
    ) {}
    
    public SimulationStatus getSimulationStatus(ServerPlayerEntity player) {
        if (player == null) {
            return new SimulationStatus(false, null, 0, 0, 0);
        }
        
        var world = (ServerWorld) player.getEntityWorld();
        if (world == null) {
            return new SimulationStatus(false, null, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.getChunkPos().x;
        int playerChunkZ = player.getChunkPos().z;
        
        String closestFakeplayerName = null;
        int closestDistance = Integer.MAX_VALUE;
        int closestChunkX = 0;
        int closestChunkZ = 0;
        
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
            }
        }
        
        return new SimulationStatus(
            closestFakeplayerName != null,
            closestFakeplayerName,
            closestChunkX,
            closestChunkZ,
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
    
    public ChunkplayerStatus getChunkplayerStatus(ServerPlayerEntity player) {
        if (player == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }
        
        var world = (ServerWorld) player.getEntityWorld();
        if (world == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.getChunkPos().x;
        int playerChunkZ = player.getChunkPos().z;
        
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
                ChunkloaderMod.LOGGER.debug("Position ({}, {}) is within the loaded area of chunkloader '{}' at ({}, {}) with radius {}", 
                    chunkX, chunkZ, other.name(), other.chunkX(), other.chunkZ(), otherRadius);
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
                    ChunkloaderMod.LOGGER.debug("Position ({}, {}) with radius {} overlaps with chunkloader '{}' at ({}, {}) with radius {}", 
                        chunkX, chunkZ, radius, other.name(), other.chunkX(), other.chunkZ(), otherRadius);
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
    
    public record ChunkloaderStats(int total, int enabled, int disabled, int loadedChunks, int activeFakePlayers) {}
    public record ChunkloaderPerformanceStats(
        int totalLoadedChunks,
        long usedMemory,
        long maxMemory,
        double memoryUsagePercent,
        int activeFakePlayers
    ) {}
    
    public List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> getDisabledChunkloadersList() {
        List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> result = new ArrayList<>();
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        
        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                result.add(new de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry(
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
            ServerWorld world = getWorldByDimension(entry.dimension());
            if (world != null && !activeTargets.containsKey(key)) {
                ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ);
                if (updatedEntry != null) {
                    try {
                        activateChunkloader(updatedEntry, world);
                        ChunkloaderNetworking.broadcastCloseChunkMap(server);
                    } catch (Exception e) {
                        ChunkloaderMod.LOGGER.error("Failed to restore chunkloader at chunk ({}, {})", chunkX, chunkZ, e);
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
            ChunkloaderMod.LOGGER.warn("Cannot update disabled chunkloader coordinates: Coordinates are identical");
            return "Cannot update coordinates: Coordinates are identical to the current position.";
        }
        
        if (isPositionCoveredByOtherChunkloader(newChunkX, newChunkZ, 0, entry.dimension(), entry)) {
            ChunkloaderMod.LOGGER.warn("Cannot update disabled chunkloader coordinates: Position ({}, {}) is already covered by another enabled chunkloader", 
                newChunkX, newChunkZ);
            return "Cannot update coordinates: Position is already covered by another enabled chunkloader.";
        }
        
        ChunkloaderTarget existingAtNewPos = config.getEntry(newChunkX, newChunkZ);
        if (existingAtNewPos != null && existingAtNewPos != entry && existingAtNewPos.dimension().equals(entry.dimension())) {
            ChunkloaderMod.LOGGER.warn("Cannot update disabled chunkloader coordinates: Position ({}, {}) is already occupied", 
                newChunkX, newChunkZ);
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
            ChunkloaderMod.LOGGER.warn("Failed to update disabled chunkloader coordinates from ({}, {}) to ({}, {}): addOrUpdateEntry failed", 
                oldChunkX, oldChunkZ, newChunkX, newChunkZ);
            return "Failed to update coordinates: The name may already exist or the position is invalid.";
        }
        
        if (oldChunkX != newChunkX || oldChunkZ != newChunkZ) {
            config.removeEntry(oldChunkX, oldChunkZ);
        }
        
        ChunkloaderMod.LOGGER.info("Updated disabled chunkloader coordinates from ({}, {}) to ({}, {})", 
            oldChunkX, oldChunkZ, newChunkX, newChunkZ);
        return null;
    }
    
}


