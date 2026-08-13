package de.chunkloader.manager;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.CustomFakePlayerSkinStore;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.util.EntitySyncUtil;
import de.chunkloader.mixin.ServerChunkManagerAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public class ChunkloaderManager {
    private final MinecraftServer server;
    private ChunkloaderConfig config;
    private final CustomFakePlayerSkinStore customSkinStore;
    private final Map<ChunkKey, ChunkloaderTarget> activeTargets = new ConcurrentHashMap<>();
    private final Map<ChunkKey, ChunkloaderFakePlayer> activeFakePlayers = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, UUID> markerEntities = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ChunkKey> markerToChunkKey = new ConcurrentHashMap<>();
    private final Set<ChunkKey> visualizationActive = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<ChunkKey, Visualization3DConfig> visualization3DActive = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, PendingChunkloaderState> pendingChunkloaderActivations = new ConcurrentHashMap<>();
    private final Set<UUID> syncingFakePlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastToggleTime = new ConcurrentHashMap<>();
    private final Set<ChunkKey> tabListHidden = ConcurrentHashMap.newKeySet();
    private boolean hideAllFromTabList = false;
    private long chunkMapGeneration = 0L;
    private final ConcurrentMap<UUID, Integer> pendingPlayerJoinSyncs = new ConcurrentHashMap<>();
    private static final long TOGGLE_COOLDOWN_MS = 200;
    private static final int PENDING_ACTIVATION_INITIAL_DELAY_TICKS = 0;
    private static final int PENDING_ACTIVATION_RETRY_TICKS = 2;
    private static final int MAX_PROFILE_NAME_LENGTH = 16;

    private static final int CHUNKLOADER_TICKET_FLAG = 1 << 30;
    private static final TicketType CHUNKLOADER_LOADING_TICKET = new TicketType(
            TicketType.PLAYER_LOADING.timeout(),
            TicketType.PLAYER_LOADING.flags() | CHUNKLOADER_TICKET_FLAG);
    private static final TicketType CHUNKLOADER_SIMULATION_TICKET = new TicketType(
            TicketType.PLAYER_SIMULATION.timeout(),
            TicketType.PLAYER_SIMULATION.flags() | CHUNKLOADER_TICKET_FLAG);

    private static final int FAKEPLAYER_EXTRA_BLOCK_TICK_RINGS = Integer
            .getInteger("chunkloader.fakeplayerExtraBlockTickRings", 1);
    private static final int FAKEPLAYER_EXTRA_LOADING_RINGS = Integer
            .getInteger("chunkloader.fakeplayerExtraLoadingRings", 2);
    private static final int FAKEPLAYER_MOB_SPAWN_CHUNK_RADIUS = Integer
            .getInteger("chunkloader.fakeplayerMobSpawnChunkRadius", 8);
    private static final AtomicBoolean LOGGED_MOB_SPAWN_RADIUS_WARNING = new AtomicBoolean(false);
    private static final int CHUNKPLAYER_EXTRA_BLOCK_TICK_RINGS = Integer
            .getInteger("chunkloader.chunkplayerExtraBlockTickRings", 1);
    private static final int CHUNKPLAYER_EXTRA_LOADING_RINGS = Integer
            .getInteger("chunkloader.chunkplayerExtraLoadingRings", 2);

    private static final long DISABLE_TICK_CONTROL_GRACE_MS = Long.getLong("chunkloader.disableTickControlGraceMs",
            30_000L);
    private final ConcurrentMap<String, Long> tickControlUntilByDimension = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private final Map<String, Set<ChunkKey>> randomTickChunksByDimension = new HashMap<>();
    private String storedWorldName = null;

    private static final int DEFAULT_EASTER_EGG_DENOMINATOR = 500;
    private volatile int easterEggDenominator = DEFAULT_EASTER_EGG_DENOMINATOR;
    private static final String EASTER_EGG_MESSAGE = "An old friend has returned to watch over the world, their presence keeping the chunks alive.";
    private final ConcurrentMap<ChunkKey, Integer> easterEggSkinByKey = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, Long> easterEggEmoteStartByUuid = new ConcurrentHashMap<>();
    private static final double JOIN_EMOTE_MAX_DISTANCE = 24.0;
    private static final long EMOTE_MAX_DURATION_TICKS = 101L;

    private static final int CHUNKPLAYER_MOB_CLEANUP_INTERVAL_TICKS = Integer
            .getInteger("chunkloader.chunkplayerMobCleanupIntervalTicks", 20);

    private static int fakeplayerLoadingRadius(int simulationRadius) {
        return Math.max(0, simulationRadius + FAKEPLAYER_EXTRA_LOADING_RINGS);
    }

    private static int fakeplayerBlockTickRadius(int simulationRadius) {
        return Math.max(0, simulationRadius + FAKEPLAYER_EXTRA_BLOCK_TICK_RINGS);
    }

    private static int chunkplayerLoadingRadius(int radius) {
        return Math.max(0, radius + CHUNKPLAYER_EXTRA_LOADING_RINGS);
    }

    private static int chunkplayerBlockTickRadius(int radius) {
        return Math.max(0, radius + CHUNKPLAYER_EXTRA_BLOCK_TICK_RINGS);
    }

    private static int baseLevelForRadius(int coreRadius) {
        int r = Math.max(0, coreRadius);
        return ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - r;
    }

    public static int getEffectiveFakeplayerSpawnChunkRadius(ChunkloaderTarget entry) {
        if (entry == null) {
            return 0;
        }
        if (!entry.allowMobSpawning()) {
            return 0;
        }
        int selected = Math.max(0, entry.chunkRadius());
        return getEffectiveFakeplayerSpawnChunkRadius(selected);
    }

    private static int getEffectiveFakeplayerSpawnChunkRadius(int selectedChunkRadius) {
        int selected = Math.max(0, selectedChunkRadius);
        int spawn = FAKEPLAYER_MOB_SPAWN_CHUNK_RADIUS;
        if (spawn < 0) {
            return selected;
        }
        int effective = Math.max(selected, spawn);
        if (effective > selected && LOGGED_MOB_SPAWN_RADIUS_WARNING.compareAndSet(false, true)) {
            ChunkloaderMod.LOGGER.warn(
                    "Fakeplayer with allowMobSpawning uses effective spawn/ticket radius {} (UI radius {}, system min {}). "
                    + "Extra loading rings still apply via chunkloader.fakeplayerExtraLoadingRings (default 2).",
                    effective, selected, spawn);
        }
        return effective;
    }

    private static int getEffectiveTicketSimulationRadius(ChunkloaderTarget entry) {
        if (entry == null) {
            return 0;
        }
        if (entry.allowMobSpawning()) {
            return getEffectiveFakeplayerSpawnChunkRadius(entry);
        }
        return Math.max(0, entry.chunkRadius());
    }

    private void extendTickControlGrace(String dimension) {
        if (dimension == null) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(0L, DISABLE_TICK_CONTROL_GRACE_MS);
        tickControlUntilByDimension.put(dimension, until);
    }

    public boolean shouldControlTicksInDimension(String dimension) {
        boolean active = hasAnyActiveLoaderInDimension(dimension);
        if (!active) {
            if (dimension != null) {
                tickControlUntilByDimension.remove(dimension);
            }
            return false;
        }
        return true;
    }

    public long getTickControlGraceRemainingMs(String dimension) {
        if (dimension == null) {
            return 0L;
        }
        Long until = tickControlUntilByDimension.get(dimension);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    private static void addChunkloaderTickets(ServerLevel world, ChunkPos chunkPos, ChunkloaderTarget entry,
            int radius) {
        if (world == null || chunkPos == null || entry == null) {
            return;
        }

        int coreRadius = Math.max(0, radius);
        int loadingRadius = entry.allowMobSpawning() ? fakeplayerLoadingRadius(coreRadius)
                : chunkplayerLoadingRadius(coreRadius);
        addTickets(world.getChunkSource(), CHUNKLOADER_LOADING_TICKET, chunkPos, loadingRadius);
        addTickets(world.getChunkSource(), CHUNKLOADER_SIMULATION_TICKET, chunkPos, coreRadius);
    }

    private static void removeAllChunkloaderTickets(ServerLevel world, ChunkPos chunkPos, ChunkloaderTarget entry,
            int radius) {
        if (world == null || chunkPos == null || entry == null) {
            return;
        }

        int coreRadius = Math.max(0, radius);
        int loadingRadius = entry.allowMobSpawning() ? fakeplayerLoadingRadius(coreRadius)
                : chunkplayerLoadingRadius(coreRadius);
        removeTickets(world.getChunkSource(), CHUNKLOADER_LOADING_TICKET, chunkPos, loadingRadius);
        removeTickets(world.getChunkSource(), CHUNKLOADER_SIMULATION_TICKET, chunkPos, coreRadius);
    }

    private static void addTickets(ServerChunkCache chunkSource, TicketType ticketType, ChunkPos center, int radius) {
        int effectiveRadius = Math.max(0, radius);
        int ticketLevel = baseLevelForRadius(effectiveRadius);
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                chunkSource.addTicket(
                        new Ticket(ticketType, ticketLevel),
                        new ChunkPos(center.x() + dx, center.z() + dz));
            }
        }
    }

    private static void removeTickets(ServerChunkCache chunkSource, TicketType ticketType, ChunkPos center, int radius) {
        int effectiveRadius = Math.max(0, radius);
        int ticketLevel = baseLevelForRadius(effectiveRadius);
        ServerChunkManagerAccessor accessor = (ServerChunkManagerAccessor) chunkSource;
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                accessor.chunkloader$getTicketStorage()
                        .removeTicket(
                                new Ticket(ticketType, ticketLevel),
                                new ChunkPos(center.x() + dx, center.z() + dz));
            }
        }
    }

    private static final boolean DEBUG_LOADED_CHUNKS = Boolean.getBoolean("chunkloader.debugLoadedChunks");
    private static final int DEBUG_LOADED_CHUNKS_INTERVAL_TICKS = Integer
            .getInteger("chunkloader.debugLoadedChunksIntervalTicks", 100);
    private int debugLoadedChunksCounter = 0;

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
        this.customSkinStore = new CustomFakePlayerSkinStore(server);
        this.customSkinStore.load();
        if (config != null) {
            this.hideAllFromTabList = !config.isTabListVisibleAll();
        }
    }

    public CustomFakePlayerSkinStore getCustomSkinStore() {
        return customSkinStore;
    }

    public MinecraftServer getServer() {
        return server;
    }

    private void scheduleChunkloaderInitialization(ChunkloaderTarget entry, int delayTicks) {
        ChunkKey key = chunkKey(entry);
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
            ChunkloaderMod.LOGGER.warn("Failed to get world for dimension: {}", dimension, e);
        }
        return null;
    }

    private String getDimensionFromWorld(ServerLevel world) {
        if (world == null) {
            return "unknown";
        }
        return dimensionCache.computeIfAbsent(world, w -> w.dimension().identifier().toString());
    }

    public static String getDimensionString(ServerLevel world) {
        if (world == null) {
            return "unknown";
        }
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            return manager.dimensionCache.computeIfAbsent(world, w -> w.dimension().identifier().toString());
        }
        return world.dimension().identifier().toString();
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
                                    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files
                                            .newDirectoryStream(savesDir, filter)) {
                                        for (java.nio.file.Path worldDir : stream) {
                                            try {
                                                Path levelDat = worldDir.resolve("level.dat");
                                                if (java.nio.file.Files.exists(levelDat)) {
                                                    long levelDatTime = java.nio.file.Files
                                                            .getLastModifiedTime(levelDat).toMillis();
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
                                    ChunkloaderMod.LOGGER.warn("Error searching for world directory: {}",
                                            e.getMessage());
                                }

                                if (mostRecentWorldDir != null) {
                                    ChunkloaderMod.LOGGER.info(
                                            "Using most recently modified world directory: {} (modified: {})",
                                            mostRecentWorldDir.getFileName(), new java.util.Date(mostRecentTime));
                                    return mostRecentWorldDir.resolve("chunkloader_config.json");
                                }

                                if (currentLevelName != null && !currentLevelName.isEmpty()) {
                                    ChunkloaderMod.LOGGER.warn(
                                            "Could not find world directory by modification time, using level name: {}",
                                            currentLevelName);
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

    private void spawnParticles(ServerLevel world, BlockPos pos, boolean enabled, boolean allowMobSpawning) {
        if (world == null)
            return;

        if (enabled) {
            if (allowMobSpawning) {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.getRandom().nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0.02);
                }
            } else {
                for (int i = 0; i < 10; i++) {
                    double x = pos.getX() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                    double y = pos.getY() + world.getRandom().nextDouble() * 2;
                    double z = pos.getZ() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                    world.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0.02);
                }
            }
        } else {
            for (int i = 0; i < 10; i++) {
                double x = pos.getX() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                double y = pos.getY() + world.getRandom().nextDouble() * 2;
                double z = pos.getZ() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 2;
                world.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
    }

    private void spawnEasterEggSpawnEffects(ServerLevel world, BlockPos pos, boolean allowMobSpawning) {
        if (world == null) {
            return;
        }

        var random = world.getRandom();
        for (int i = 0; i < 28; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.25 + random.nextDouble() * 0.85;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 0.1 + random.nextDouble() * 2.2;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            world.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.08, 0.0, 0.0);
        }
        for (int i = 0; i < 18; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.8;
            double y = pos.getY() + 0.4 + random.nextDouble() * 1.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.8;
            world.sendParticles(ParticleTypes.GLOW, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        if (allowMobSpawning) {

            world.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.35f, 1.45f);
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.7f, 1.5f);
        } else {

            world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.15f, 1.1f);
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.25f, 0.8f);
        }
    }

    private void playSpawnEffects(ServerLevel world, BlockPos pos, ChunkKey key, ChunkloaderTarget entry, boolean enabled) {
        boolean allowMobSpawning = entry != null && entry.allowMobSpawning();
        if (!enabled) {
            spawnParticles(world, pos, false, false);
            playSound(world, pos, false, allowMobSpawning);
            return;
        }
        boolean easterEgg = isEasterEgg(key) || (entry != null && entry.easterEggSkinIndex() != null);
        if (easterEgg) {
            spawnEasterEggSpawnEffects(world, pos, allowMobSpawning);
            return;
        }
        spawnParticles(world, pos, true, allowMobSpawning);
        playSound(world, pos, true, allowMobSpawning);
    }

    private void playSound(ServerLevel world, BlockPos pos, boolean enabled, boolean allowMobSpawning) {
        if (world == null)
            return;

        if (enabled) {
            if (allowMobSpawning) {

                world.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.65f, 1.2f);
            } else {

                world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.25f);
            }
        } else {
            world.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5f, 0.8f);
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

    private void renderChunkBorders(ServerLevel world, ChunkloaderTarget entry) {
        if (world == null)
            return;

        ChunkKey key = chunkKey(entry);
        if (!visualizationActive.contains(key))
            return;

        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;

        int y = entry.blockY();

        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            int worldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
            for (int z = minChunkZ * ChunkloaderConstants.CHUNK_SIZE; z <= (maxChunkZ + 1)
                    * ChunkloaderConstants.CHUNK_SIZE; z += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldX, y, z,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int worldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
            for (int x = minChunkX * ChunkloaderConstants.CHUNK_SIZE; x <= (maxChunkX + 1)
                    * ChunkloaderConstants.CHUNK_SIZE; x += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, worldZ,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
    }

    private net.minecraft.core.particles.ParticleOptions getVisualization3DParticle(ServerLevel world) {
        return world != null && world.isBrightOutside() ? ParticleTypes.SCRAPE : ParticleTypes.FLAME;
    }

    private void renderChunkBorders3D(ServerLevel world, ChunkloaderTarget entry) {
        if (world == null)
            return;

        ChunkKey key = chunkKey(entry);
        Visualization3DConfig config = visualization3DActive.get(key);
        if (config == null)
            return;

        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;

        int minY = config.minY();
        int maxY = config.maxY();

        var particleType = getVisualization3DParticle(world);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                boolean onPerimeter = chunkX == minChunkX || chunkX == maxChunkX || chunkZ == minChunkZ || chunkZ == maxChunkZ;
                if (!onPerimeter) {
                    continue;
                }
                int chunkWorldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldXEnd = chunkWorldX + ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZEnd = chunkWorldZ + ChunkloaderConstants.CHUNK_SIZE;

                if (tickCounter % 10 == 0) {
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldX, y, chunkWorldZ,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldXEnd, y, chunkWorldZ,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldX, y, chunkWorldZEnd,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldXEnd, y, chunkWorldZEnd,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }

                if (tickCounter % 10 == 0) {
                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.sendParticles(particleType, x, maxY, chunkWorldZ,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.sendParticles(particleType, x, maxY, chunkWorldZEnd,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldX, maxY, z,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.sendParticles(particleType, chunkWorldXEnd, maxY, z,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }

                    for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.sendParticles(particleType, x, minY, chunkWorldZ,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.sendParticles(particleType, x, minY, chunkWorldZEnd,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                    for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                        world.sendParticles(particleType, chunkWorldX, minY, z,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                        world.sendParticles(particleType, chunkWorldXEnd, minY, z,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                                ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    }
                }
            }
        }
    }

    public void tick() {
        processPendingChunkloaderActivations();
        processPendingPlayerJoinSyncs();

        if (tickCounter % 10 == 0) {
            for (ChunkKey key : visualizationActive) {
                ChunkloaderTarget entry = activeTargets.get(key);
                if (entry != null && entry.enabled()) {
                    ServerLevel world = getWorldByDimension(entry.dimension());
                    if (world != null) {
                        renderChunkBorders(world, entry);
                    }
                }
            }
        }

        for (Map.Entry<ChunkKey, Visualization3DConfig> entry : visualization3DActive.entrySet()) {
            ChunkloaderTarget target = activeTargets.get(entry.getKey());
            if (target == null) {
                target = config.getEntry(entry.getKey().x(), entry.getKey().z(), entry.getKey().dimension());
            }
            if (target != null && target.enabled()) {
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
        cleanupFrozenMobsInChunkplayerAreas();

        if (DEBUG_LOADED_CHUNKS) {
            debugLoadedChunksCounter++;
            if (debugLoadedChunksCounter >= DEBUG_LOADED_CHUNKS_INTERVAL_TICKS) {
                debugLoadedChunksCounter = 0;
                debugDumpLoadedChunks();
            }
        }
    }

    public void schedulePlayerJoinSync(ServerPlayer player, int delayTicks) {
        if (player == null) {
            return;
        }
        pendingPlayerJoinSyncs.put(player.getUUID(), Math.max(0, delayTicks));
    }

    public void forceImmediateSync() {
        processPendingChunkloaderActivations();
        ensureChunksLoaded();
    }

    private void processPendingPlayerJoinSyncs() {
        if (pendingPlayerJoinSyncs.isEmpty() || server == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = pendingPlayerJoinSyncs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Integer remaining = entry.getValue();
            if (remaining != null && remaining > 0) {
                entry.setValue(remaining - 1);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.connection != null) {
                sendEasterEggSkinsToPlayer(player);
                sendFakePlayerVisibilitiesToPlayer(player);
                sendCustomSkinsToPlayer(player);
                sendEasterEggEmotesToPlayer(player);
            }
            it.remove();
        }
    }

    private void cleanupFrozenMobsInChunkplayerAreas() {
        int interval = Math.max(0, CHUNKPLAYER_MOB_CLEANUP_INTERVAL_TICKS);
        if (interval == 0) {
            return;
        }
        if ((server.getTickCount() % interval) != 0) {
            return;
        }
        if (activeTargets.isEmpty()) {
            return;
        }

        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkKey key = entry.getKey();
            ChunkloaderTarget target = entry.getValue();
            if (target == null || !target.enabled() || target.allowMobSpawning()) {
                continue;
            }

            ServerLevel world = getWorldByDimension(target.dimension());
            if (world == null) {
                continue;
            }
            String dimension = getDimensionFromWorld(world);
            if (dimension == null || !dimension.equals(target.dimension())) {
                continue;
            }

            if (isChunkNearRealPlayer(world, key.x(), key.z(), getServerSimulationDistance())) {
                continue;
            }

            int r = Math.max(0, target.chunkRadius());
            if (r <= 0) {
                r = 0;
            }

            int cx0 = key.x();
            int cz0 = key.z();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;

                    if (!world.getChunkSource().hasChunk(cx, cz)) {
                        continue;
                    }
                    if (!isChunkplayerEntityTickChunk(cx, cz, target.dimension())) {
                        continue;
                    }
                    if (isChunkNearRealPlayer(world, cx, cz, getServerSimulationDistance())) {
                        continue;
                    }

                    AABB box = new AABB(
                            cx * 16.0, Double.NEGATIVE_INFINITY, cz * 16.0,
                            cx * 16.0 + 16.0, Double.POSITIVE_INFINITY, cz * 16.0 + 16.0);

                    List<Mob> mobs = world.getEntitiesOfClass(Mob.class, box, m -> true);
                    if (mobs == null || mobs.isEmpty()) {
                        continue;
                    }

                    for (Mob mob : mobs) {
                        if (mob == null || !mob.isAlive()) {
                            continue;
                        }
                        ChunkPos mp = mob.chunkPosition();
                        if (isChunkNearRealPlayer(world, mp.x(), mp.z(), getServerSimulationDistance())) {
                            continue;
                        }
                        mob.remove(Entity.RemovalReason.DISCARDED);
                    }
                }
            }
        }
    }

    private int getServerSimulationDistance() {
        try {
            if (server != null && server.getPlayerList() != null) {
                return Math.max(0, server.getPlayerList().getSimulationDistance());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static boolean isChunkNearRealPlayer(ServerLevel world, int chunkX, int chunkZ, int radius) {
        if (world == null) {
            return false;
        }
        List<ServerPlayer> players = world.players();
        if (players == null || players.isEmpty()) {
            return false;
        }
        int r = Math.max(0, radius);
        for (ServerPlayer p : players) {
            if (p == null || p instanceof ChunkloaderFakePlayer) {
                continue;
            }
            ChunkPos pc = p.chunkPosition();
            int dx = Math.abs(pc.x() - chunkX);
            int dz = Math.abs(pc.z() - chunkZ);
            if (dx <= r && dz <= r) {
                return true;
            }
        }
        return false;
    }

    private void debugDumpLoadedChunks() {
        if (activeTargets.isEmpty()) {
            ChunkloaderMod.LOGGER.info("[chunkloader-debug] activeTargets is empty");
            return;
        }

        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkKey key = entry.getKey();
            ChunkloaderTarget target = entry.getValue();
            if (target == null) {
                continue;
            }

            ServerLevel world = getWorldByDimension(target.dimension());
            if (world == null) {
                ChunkloaderMod.LOGGER.info(
                        "[chunkloader-debug] target=({}, {}) enabled={} mode={} radius={} dim={} world=null",
                        key.x(), key.z(), target.enabled(), target.allowMobSpawning() ? "fakeplayer" : "chunkplayer",
                        target.chunkRadius(), target.dimension());
                continue;
            }

            String worldDim = getDimensionFromWorld(world);
            int realPlayers = 0;
            try {
                List<ServerPlayer> players = world.players();
                if (players != null) {
                    for (ServerPlayer p : players) {
                        if (!(p instanceof ChunkloaderFakePlayer)) {
                            realPlayers++;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            ChunkloaderMod.LOGGER.info(
                    "[chunkloader-debug] target=({}, {}) enabled={} mode={} radius={} entryDim={} worldDim={} realPlayersInWorld={}",
                    key.x(), key.z(), target.enabled(), target.allowMobSpawning() ? "fakeplayer" : "chunkplayer",
                    target.chunkRadius(), target.dimension(), worldDim, realPlayers);

            int radius = Math.max(0, target.chunkRadius());
            int debugExtra = 2;
            int minX = key.x() - radius - debugExtra;
            int maxX = key.x() + radius + debugExtra;
            int minZ = key.z() - radius - debugExtra;
            int maxZ = key.z() + radius + debugExtra;

            ServerChunkCache chunkManager = world.getChunkSource();
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    boolean loaded = chunkManager.hasChunk(cx, cz);
                    boolean allowedTick = isFakeplayerRandomTickChunk(cx, cz, worldDim)
                            || isChunkplayerRandomTickChunk(cx, cz, worldDim);

                    if (!loaded && !allowedTick) {
                        continue;
                    }

                    String info;
                    try {
                        info = chunkManager.getChunkDebugData(new ChunkPos(cx, cz));
                    } catch (Exception e) {
                        info = "<debugInfoError:" + e.getClass().getSimpleName() + ">";
                    }

                    ChunkloaderMod.LOGGER.info(
                            "[chunkloader-debug] chunk=({}, {}) loaded={} allowedTick={} info={}",
                            cx, cz, loaded, allowedTick, info);
                }
            }
        }
    }

    private void performRandomTicksForChunkplayers() {
        randomTickChunksByDimension.clear();
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (target.enabled() && !target.allowMobSpawning()) {
                randomTickChunksByDimension.computeIfAbsent(target.dimension(), k -> new HashSet<>())
                        .add(entry.getKey());
            }
        }

        for (Map.Entry<String, Set<ChunkKey>> dimensionEntry : randomTickChunksByDimension.entrySet()) {
            ServerLevel world = getWorldByDimension(dimensionEntry.getKey());
            if (world == null) {
                continue;
            }

            for (ChunkKey chunkKey : dimensionEntry.getValue()) {
                try {
                    ChunkPos chunkPos = new ChunkPos(chunkKey.x(), chunkKey.z());
                    net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());

                    if (chunk == null || !(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) {
                        continue;
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
            String displayName = entry.name() != null ? entry.name() : "unnamed";
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null) {
                state.setTicksUntilNextAttempt(PENDING_ACTIVATION_RETRY_TICKS);
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            try {
                world.getChunk(chunkPos.x(), chunkPos.z());
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
                ChunkloaderMod.LOGGER.error(
                        "Failed to initialize chunkloader '{}' at chunk ({}, {}), retrying in {} ticks",
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
            ChunkKey key = chunkKey(entry);
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null)
                continue;
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
            configuredKeys.add(chunkKey(entry));
        }

        for (ChunkKey key : new HashSet<>(activeTargets.keySet())) {
            if (!configuredKeys.contains(key)) {
                deactivateChunkloader(key);
            }
        }
    }

    public void loadPersistentChunkloaders() {
        ChunkloaderMod.LOGGER.info("Loading persistent chunkloaders...");

        String currentWorldName = null;
        try {
            if (server != null && server.getWorldData() != null) {
                currentWorldName = server.getWorldData().getLevelName();
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
                ChunkloaderMod.LOGGER.info("Level name changed from '{}' to '{}' - reloading config",
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
        this.hideAllFromTabList = !newConfig.isTabListVisibleAll();
        ChunkloaderMod.LOGGER.info("Config reloaded for world '{}' - {} entries loaded",
                currentWorldName != null ? currentWorldName : "unknown", newConfig.getChunkEntries().size());

        storeWorldName(currentWorldName);

        currentConfigPath = newConfig.getConfigPath();

        if (expectedConfigPath != null && !expectedConfigPath.equals(currentConfigPath)) {
            ChunkloaderMod.LOGGER.error(
                    "Config path mismatch after reload! Expected: {}, Actual: {}. Skipping load to prevent cross-world contamination.",
                    expectedConfigPath, currentConfigPath);
            return;
        }

        Set<String> loadedDimensions = new HashSet<>();
        for (ServerLevel world : server.getAllLevels()) {
            loadedDimensions.add(getDimensionFromWorld(world));
        }
        ChunkloaderMod.LOGGER.info("Currently loaded dimensions: {}", loadedDimensions);

        Map<String, Integer> dimensionCounts = new HashMap<>();

        ChunkloaderMod.LOGGER.info("Loading {} chunkloader entries from config", config.getChunkEntries().size());
        pendingChunkloaderActivations.clear();

        easterEggSkinByKey.clear();
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (entry.easterEggSkinIndex() != null) {
                ChunkKey key = chunkKey(entry);
                easterEggSkinByKey.put(key, entry.easterEggSkinIndex());
            }
        }

        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (!loadedDimensions.contains(entry.dimension())) {
                continue;
            }

            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world == null) {
                ChunkloaderMod.LOGGER.warn("Level for dimension {} not available, skipping chunkloader at ({}, {})",
                        entry.dimension(), entry.chunkX(), entry.chunkZ());
                continue;
            }

            scheduleChunkloaderInitialization(entry, PENDING_ACTIVATION_INITIAL_DELAY_TICKS);
            dimensionCounts.put(entry.dimension(), dimensionCounts.getOrDefault(entry.dimension(), 0) + 1);
        }

        int totalScheduled = dimensionCounts.values().stream().mapToInt(Integer::intValue).sum();
        ChunkloaderMod.LOGGER.info(
                "Scheduled {} chunkloaders for delayed initialization in world '{}' across {} dimensions ({} pending entries)",
                totalScheduled, currentWorldName != null ? currentWorldName : "unknown", dimensionCounts.size(),
                pendingChunkloaderActivations.size());
    }

    public void savePersistentChunkloaders() {
        ChunkloaderMod.LOGGER.info("Saving chunkloader data...");
    }

    public void cleanup() {
        ChunkloaderMod.LOGGER.info("Cleaning up chunkloaders...");

        int despawnedCount = 0;

        if (server != null && server.getPlayerList() != null) {
            try {
                List<ServerPlayer> allPlayers = new ArrayList<>(server.getPlayerList().getPlayers());
                for (ServerPlayer player : allPlayers) {
                    if (player instanceof ChunkloaderFakePlayer fakePlayer) {
                        try {
                            fakePlayer.despawn();
                            despawnedCount++;
                            ChunkloaderMod.LOGGER.info("Despawned fakeplayer from PlayerList: {}",
                                    player.getName().getString());
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Error despawning fakeplayer from PlayerList: {}",
                                    e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.warn("Error accessing PlayerList: {}", e.getMessage());
            }
        }

        boolean scanWorlds = Boolean.getBoolean("chunkloader.cleanupScanWorlds");
        if (server != null && scanWorlds) {
            for (ServerLevel world : server.getAllLevels()) {
                try {
                    net.minecraft.world.phys.AABB worldBox = new net.minecraft.world.phys.AABB(
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
                    List<Entity> fakePlayersToRemove = new ArrayList<>();
                    for (Entity entity : world.getEntitiesOfClass(ServerPlayer.class, worldBox, e -> true)) {
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
                                despawnedCount++;
                                ChunkloaderMod.LOGGER.info("Despawned fakeplayer from world {}: {}",
                                        world.dimension().identifier(), entity.getName().getString());
                            } else if (entity instanceof ServerPlayer player) {
                                if (server.getPlayerList() != null) {
                                    server.getPlayerList().remove(player);
                                }
                                if (player.connection != null) {
                                    player.connection.disconnect(Component.literal("cleanup"));
                                }
                                despawnedCount++;
                                ChunkloaderMod.LOGGER.info("Despawned potential fakeplayer from world {} by name: {}",
                                        world.dimension().identifier(), entity.getName().getString());
                            }
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Error despawning fakeplayer from world: {}", e.getMessage(),
                                    e);
                        }
                    }
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.warn("Error searching for fakeplayers in world {}: {}",
                            world.dimension().identifier(), e.getMessage());
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
                        int radius = getEffectiveTicketSimulationRadius(entry);
                        removeAllChunkloaderTickets(world, chunkPos, entry, radius);
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
                ChunkloaderMod.LOGGER.error("Error during cleanup of chunkloader at chunk ({}, {})", key.x(), key.z(),
                        e);
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

        ChunkloaderMod.LOGGER.info(
                "Cleanup completed. Despawned {} fakeplayers, deactivated {} chunkloaders and cleared all maps",
                despawnedCount, keys.size());
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

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world,
            String ownerName) {
        return addChunkloader(chunkX, chunkZ, blockPos, name, world, ownerName, 0.0f);
    }

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world,
            String ownerName, float spawnYaw) {
        if (config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader: Maximum limit ({}) reached",
                    config.getMaxChunkloaders());
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

        ChunkloaderTarget existingEntry = config.getEntry(chunkX, chunkZ, dimension);
        if (existingEntry != null && existingEntry.dimension().equals(dimension)) {
            ChunkloaderMod.LOGGER.warn("Cannot add chunkloader at ({}, {}) in {}: entry already exists (enabled={})",
                    chunkX, chunkZ, dimension, existingEntry.enabled());
            return false;
        }

        int defaultRadius = 0;
        if (isPositionCoveredByOtherChunkloader(chunkX, chunkZ, defaultRadius, dimension, null)) {
            ChunkloaderMod.LOGGER.warn(
                    "Cannot add chunkloader at ({}, {}): Position is already covered by another active chunkloader",
                    chunkX, chunkZ);
            return false;
        }
        float normalizedSpawnYaw = normalizeSpawnYaw(spawnYaw);
        boolean success = config.addOrUpdateEntry(chunkX, chunkZ, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                name, dimension, null, null, ownerName, normalizedSpawnYaw);
        if (!success) {
            return false;
        }

        chunkMapGeneration++;
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null) {
            try {
                activateChunkloader(entry, world, true);
                ChunkloaderNetworking.invalidateChunkCache();
                ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
                return true;
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Failed to activate chunkloader at chunk ({}, {})", chunkX, chunkZ, e);
                config.removeEntry(chunkX, chunkZ, dimension);
                return false;
            }
        }
        return false;
    }

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name) {
        ServerLevel overworld = server.overworld();
        if (overworld == null)
            return false;
        return addChunkloader(chunkX, chunkZ, blockPos, name, overworld);
    }

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos) {
        return addChunkloader(chunkX, chunkZ, blockPos, null);
    }

    public boolean removeChunkloaderByName(String name) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry != null) {
            return removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
        }
        return false;
    }

    public boolean removeChunkloader(int x, int z, String dimension) {
        ChunkloaderTarget entryToRemove = config.getEntry(x, z, dimension);
        String removedName = entryToRemove != null ? entryToRemove.name() : null;
        boolean removed = config.removeEntry(x, z, dimension);

        if (removed) {
            chunkMapGeneration++;
            ChunkKey key = new ChunkKey(dimension, x, z);
            cancelPendingChunkloader(key);
            deactivateChunkloader(key);
            visualizationActive.remove(key);
            visualization3DActive.remove(key);
            if (removedName != null && !removedName.isBlank()) {
                customSkinStore.remove(removedName);
                ChunkloaderNetworking.broadcastClearCustomSkin(server, removedName);
            }
            ChunkloaderNetworking.closeOpenChunkMapsFor(server, x, z, dimension);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
            ChunkloaderMod.LOGGER.info("Removed chunkloader at chunk {}, {}", x, z);
        }

        return removed;
    }

    private void activateChunkloader(ChunkloaderTarget entry, ServerLevel world) {
        activateChunkloader(entry, world, false, false);
    }

    private void activateChunkloader(ChunkloaderTarget entry, ServerLevel world, boolean allowRandomEasterEggAssign) {

        activateChunkloader(entry, world, allowRandomEasterEggAssign, allowRandomEasterEggAssign);
    }

    private void activateChunkloader(ChunkloaderTarget entry, ServerLevel world, boolean allowRandomEasterEggAssign, boolean playEffects) {
        ChunkKey key = chunkKey(entry);
        cancelPendingChunkloader(key);
        seedEasterEggFromEntry(key, entry);
        if (allowRandomEasterEggAssign && entry.easterEggSkinIndex() == null && easterEggSkinByKey.get(key) == null) {
            getOrAssignEasterEggSkinIndex(key);
        }

        ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
        if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.ChatFormatting color = determineFakePlayerColor(entry, key);
            Component nameText = Component.literal(displayName).withStyle(color);
            existingFakePlayer.setCustomName(nameText);
            existingFakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
            boolean nameVisible = entry.nameVisible();
            existingFakePlayer.setCustomNameVisible(nameVisible);
            existingFakePlayer.setVisibleAsMarker(true);
            existingFakePlayer.setMobTarget(entry.allowMobSpawning() && entry.mobTarget());

            String plainName = displayName;
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, nameVisible);

            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = getEffectiveTicketSimulationRadius(oldEntry);
                    removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldRadius);
                }
            }
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = getEffectiveTicketSimulationRadius(entry);
            addChunkloaderTickets(world, chunkPos, entry, radius);
            activeTargets.put(key, entry);

            updateMarkerForChunkloader(key);

            applyEasterEggAfterSpawn(key, existingFakePlayer, allowRandomEasterEggAssign, allowRandomEasterEggAssign);

            ChunkloaderTarget updatedEntry = activeTargets.get(key);
            if (updatedEntry != null) {
                updateFakePlayerTeam(existingFakePlayer, updatedEntry);
            }

            return;
        }

        removeMarkerForChunkloader(key);

        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget oldEntry = activeTargets.get(key);
            ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
            if (oldWorld != null) {
                ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry,
                        getEffectiveTicketSimulationRadius(oldEntry));
            }
        }

        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
        int radius = getEffectiveTicketSimulationRadius(entry);

        try {
            addChunkloaderTickets(world, chunkPos, entry, radius);
            activeTargets.put(key, entry);

            ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                    server,
                    world,
                    createProfile(entry));
            float normalizedSpawnYaw = normalizeSpawnYaw(entry.spawnYaw());
            fakePlayer.snapTo(entry.blockX() + 0.5, entry.blockY(), entry.blockZ() + 0.5, normalizedSpawnYaw, 0.0F);

            String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = entry.name() != null ? entry.name() : (prefix + key.x() + "_" + key.z());
            net.minecraft.ChatFormatting color = determineFakePlayerColor(entry, key);
            final Component nameText = Component.literal(displayName).withStyle(color);
            final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
            final ServerLevel finalWorld = world;

            fakePlayer.setCustomName(nameText);
            fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
            boolean nameVisible = entry.nameVisible();
            fakePlayer.setCustomNameVisible(nameVisible);

            fakePlayer.setVisibleAsMarker(true);
            fakePlayer.setMobTarget(entry.allowMobSpawning() && entry.mobTarget());

            String plainName = displayName;
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, nameVisible);

            try {
                activeFakePlayers.put(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.put(key, fakePlayerUuid);
                markerToChunkKey.put(fakePlayerUuid, key);

                boolean spawned = fakePlayer.spawn();
                if (!spawned || !fakePlayer.isRegistered()) {
                    removeAllChunkloaderTickets(world, chunkPos, entry, radius);
                    activeTargets.remove(key);
                    activeFakePlayers.remove(key, fakePlayer);
                    markerEntities.remove(key, fakePlayerUuid);
                    markerToChunkKey.remove(fakePlayerUuid, key);
                    ChunkloaderMod.LOGGER.error(
                            "Failed to activate chunkloader at chunk ({}, {}): fake player spawn did not register",
                            key.x(), key.z());
                    return;
                }
                ChunkloaderNetworking.broadcastEasterEggEmote(server, fakePlayer.getUUID(),
                        fakePlayer.level().getGameTime());
                noteEasterEggEmoteStart(fakePlayer.getUUID(), fakePlayer.level().getGameTime());
                applyEasterEggAfterSpawn(key, fakePlayer, allowRandomEasterEggAssign, allowRandomEasterEggAssign);

                ChunkloaderTarget updatedEntry = activeTargets.get(key);
                if (updatedEntry != null) {
                    updateFakePlayerTeam(fakePlayer, updatedEntry);
                }
                if (hideAllFromTabList || tabListHidden.contains(key)) {
                    if (hideAllFromTabList) {
                        tabListHidden.add(key);
                    }
                    hideFromTabList(fakePlayer);
                }

                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                            applyFakePlayerMetadata(finalFakePlayer, entry, key);
                            forceEntitySync(finalFakePlayer);

                            updateFakePlayerTeam(finalFakePlayer, entry);
                        }
                    });
                });

                applyFakePlayerMetadata(fakePlayer, entry, key);

                forceEntitySync(fakePlayer);

                updateFakePlayerTeam(fakePlayer, entry);
            } catch (Exception e) {
                removeAllChunkloaderTickets(world, chunkPos, entry, radius);
                activeTargets.remove(key);
                activeFakePlayers.remove(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.remove(key, fakePlayerUuid);
                markerToChunkKey.remove(fakePlayerUuid, key);
                if (e.getMessage() != null && e.getMessage().contains("packettweaker")) {
                    ChunkloaderMod.LOGGER.warn(
                            "Failed to activate chunkloader at chunk ({}, {}) due to mod incompatibility (polymer-core/packet_tweaker). "
                                    +
                                    "The chunkloader will not be activated. Error: {}",
                            key.x(), key.z(), e.getMessage());
                } else {
                    ChunkloaderMod.LOGGER.error("Failed to spawn fake player: {}", e.getMessage(), e);
                }
                return;
            }

            BlockPos blockPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
            if (playEffects) {
                playSpawnEffects(world, blockPos, key, activeTargets.getOrDefault(key, entry), true);
            }

            String mode = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            ChunkloaderMod.LOGGER.info("Activated {} at chunk {}, {} (block {}, {}, {})",
                    mode, entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to activate chunkloader at chunk ({}, {})", entry.chunkX(),
                    entry.chunkZ(), e);
            activeTargets.remove(key);
            activeFakePlayers.remove(key);
            UUID removedMarker = markerEntities.remove(key);
            if (removedMarker != null) {
                markerToChunkKey.remove(removedMarker);
            }
            throw e;
        }
    }

    private void deactivateChunkloader(ChunkKey key) {
        cancelPendingChunkloader(key);
        ChunkloaderTarget entry = activeTargets.remove(key);
        if (entry == null) {
            entry = config.getEntry(key.x(), key.z(), key.dimension());
        }
        if (entry == null) {
            return;
        }

        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null) {
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = getEffectiveTicketSimulationRadius(entry);
            removeAllChunkloaderTickets(world, chunkPos, entry, radius);
        }
        extendTickControlGrace(entry.dimension());
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
        UUID fakePlayerUuid = fakePlayer != null ? fakePlayer.getUUID() : null;

        Integer removedEasterEgg = easterEggSkinByKey.remove(key);
        if (fakePlayerUuid != null) {
            easterEggEmoteStartByUuid.remove(fakePlayerUuid);
        }
        if (fakePlayer != null) {
            fakePlayer.despawn();
        }
        if ((removedEasterEgg != null || entry.easterEggSkinIndex() != null) && fakePlayerUuid != null) {
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayerUuid, -1);
        }
        removeMarkerForChunkloader(key);

        visualizationActive.remove(key);
        visualization3DActive.remove(key);

        ChunkloaderNetworking.invalidateChunkCache();
    }

    public List<ChunkloaderTarget> getActiveChunkloaderEntries() {
        return new ArrayList<>(config.getChunkEntries());
    }

    public void sendEasterEggSkinsToPlayer(ServerPlayer player) {
        if (player == null || server == null) {
            return;
        }
        for (Map.Entry<ChunkKey, ChunkloaderFakePlayer> e : activeFakePlayers.entrySet()) {
            ChunkKey key = e.getKey();
            ChunkloaderFakePlayer fp = e.getValue();
            if (key == null || fp == null) {
                continue;
            }
            Integer idx = easterEggSkinByKey.get(key);
            if (idx == null) {
                ChunkloaderTarget entry = activeTargets.get(key);
                if (entry != null && entry.easterEggSkinIndex() != null) {
                    idx = entry.easterEggSkinIndex();
                    easterEggSkinByKey.put(key, idx);
                }
            }
            if (idx == null) {
                ChunkloaderNetworking.sendEasterEggSkin(player, fp.getUUID(), -1);
                continue;
            }
            ChunkloaderNetworking.sendEasterEggSkin(player, fp.getUUID(), idx);
        }
    }

    public void sendEasterEggEmotesToPlayer(ServerPlayer player) {
        if (player == null || server == null) {
            return;
        }
        double maxDistSq = JOIN_EMOTE_MAX_DISTANCE * JOIN_EMOTE_MAX_DISTANCE;
        for (ChunkloaderFakePlayer fp : activeFakePlayers.values()) {
            if (fp == null || !fp.isAlive()) {
                continue;
            }
            if (fp.level() != player.level()) {
                continue;
            }
            if (player.distanceToSqr(fp) > maxDistSq) {
                continue;
            }
            long now = fp.level().getGameTime();
            Long started = easterEggEmoteStartByUuid.get(fp.getUUID());
            if (started != null && now >= started && (now - started) <= EMOTE_MAX_DURATION_TICKS) {

                ChunkloaderNetworking.sendEasterEggEmote(player, fp.getUUID(), started);
            } else {

                easterEggEmoteStartByUuid.put(fp.getUUID(), now);
                ChunkloaderNetworking.broadcastEasterEggEmote(server, fp.getUUID(), now);
            }
        }
    }

    private void noteEasterEggEmoteStart(UUID fakePlayerUuid, long startGameTime) {
        if (fakePlayerUuid == null) {
            return;
        }
        easterEggEmoteStartByUuid.put(fakePlayerUuid, startGameTime);
    }

    public void sendFakePlayerVisibilitiesToPlayer(ServerPlayer player) {
        if (player == null || server == null) {
            return;
        }
        for (Map.Entry<ChunkKey, ChunkloaderFakePlayer> e : activeFakePlayers.entrySet()) {
            ChunkKey key = e.getKey();
            if (key == null) {
                continue;
            }
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry == null) {
                entry = config.getEntry(key.x(), key.z(), key.dimension());
            }
            if (entry == null) {
                continue;
            }
            String displayName = buildFakePlayerDisplayName(entry, key);
            ChunkloaderNetworking.sendFakePlayerVisibility(player, displayName, entry.nameVisible());
        }
    }

    public void sendCustomSkinsToPlayer(ServerPlayer player) {
        if (player == null || server == null || customSkinStore == null) {
            return;
        }
        for (CustomFakePlayerSkinStore.StoredSkin skin : customSkinStore.getAll().values()) {
            if (skin == null || skin.pngBytes() == null || skin.pngBytes().length == 0) {
                continue;
            }
            ChunkloaderNetworking.sendSyncCustomSkin(
                player,
                skin.playerName(),
                skin.layerMask(),
                skin.model(),
                skin.pngBytes()
            );
        }
    }

    public boolean applyCustomSkin(String playerName, byte[] pngBytes, int layerMask, String model) {
        if (playerName == null || playerName.isBlank() || pngBytes == null) {
            return false;
        }
        try {
            CustomFakePlayerSkinStore.StoredSkin stored =
                customSkinStore.put(playerName, pngBytes, layerMask, model);
            ChunkloaderNetworking.broadcastSyncCustomSkin(
                server,
                stored.playerName(),
                stored.layerMask(),
                stored.model(),
                stored.pngBytes()
            );
            return true;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn("Failed to apply custom skin for '{}': {}", playerName, e.getMessage());
            return false;
        }
    }

    public boolean clearCustomSkin(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        boolean removed = customSkinStore.remove(playerName);
        ChunkloaderNetworking.broadcastClearCustomSkin(server, playerName);
        return removed;
    }

    public void migrateCustomSkinName(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.isBlank() || newName.isBlank()) {
            return;
        }
        if (oldName.equalsIgnoreCase(newName)) {
            return;
        }
        CustomFakePlayerSkinStore.StoredSkin renamed = customSkinStore.rename(oldName, newName);
        ChunkloaderNetworking.broadcastClearCustomSkin(server, oldName);
        if (renamed != null) {
            ChunkloaderNetworking.broadcastSyncCustomSkin(
                server,
                renamed.playerName(),
                renamed.layerMask(),
                renamed.model(),
                renamed.pngBytes()
            );
        }
    }

    public boolean setEasterEggSkinByName(String name, Integer skinIndexOrNull) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (config == null) {
            return false;
        }
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        ChunkKey key = chunkKey(entry);
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        if (fakePlayer == null) {
            return false;
        }

        Integer finalIndex;
        if (skinIndexOrNull == null) {
            int idx = ThreadLocalRandom.current().nextInt(2);
            easterEggSkinByKey.put(key, idx);
            finalIndex = idx;
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), idx);
        } else if (skinIndexOrNull < 0) {
            easterEggSkinByKey.remove(key);
            finalIndex = null;
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), -1);
        } else {
            int idx = Math.floorMod(skinIndexOrNull, 2);
            easterEggSkinByKey.put(key, idx);
            finalIndex = idx;
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), idx);
        }

        config.updateEntryEasterEggSkinIndex(entry.chunkX(), entry.chunkZ(), entry.dimension(), finalIndex);
        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (updatedEntry != null) {
            activeTargets.put(key, updatedEntry);
        }

        applyFakePlayerMetadata(fakePlayer, updatedEntry != null ? updatedEntry : entry, key);
        forceEntitySync(fakePlayer);
        return true;
    }

    public record ChunkKey(String dimension, int x, int z) implements Comparable<ChunkKey> {
        public ChunkKey {
            dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension;
        }

        @Override
        public int compareTo(ChunkKey other) {
            int cmp = this.dimension.compareTo(other.dimension);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(this.x, other.x);
            return cmp != 0 ? cmp : Integer.compare(this.z, other.z);
        }
    }

    private static ChunkKey chunkKey(ChunkloaderTarget entry) {
        return new ChunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());
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

        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
        if (entry == null) {
            entry = activeTargets.get(key);
        }

        if (entry == null) {
            return false;
        }

        return entry.allowMobSpawning();
    }

    public boolean needsRandomTicks(int chunkX, int chunkZ, String dimension) {
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ChunkloaderTarget entry = activeTargets.get(key);
        if (entry == null) {
            return false;
        }
        return entry.enabled() && !entry.allowMobSpawning() && entry.dimension().equals(dimension);
    }

    public boolean isChunkplayerRandomTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey center = entry.getKey();
            int radius = Math.max(0, target.chunkRadius());
            int dx = Math.abs(center.x() - chunkX);
            int dz = Math.abs(center.z() - chunkZ);
            if (dx <= radius && dz <= radius) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkplayerEntityTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int r = Math.max(0, target.chunkRadius());
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= r && dz <= r) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkplayerBlockTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int r = chunkplayerBlockTickRadius(target.chunkRadius());
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= r && dz <= r) {
                return true;
            }
        }
        return false;
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

    public boolean isFakeplayerEntityTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || !target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int r = getEffectiveFakeplayerSpawnChunkRadius(target.chunkRadius());
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= r && dz <= r) {
                return true;
            }
        }
        return false;
    }

    public boolean isFakeplayerBlockTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || !target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int blockRadius = fakeplayerBlockTickRadius(target.chunkRadius());
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= blockRadius && dz <= blockRadius) {
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

    private boolean isEasterEgg(ChunkKey key) {
        return key != null && easterEggSkinByKey.containsKey(key);
    }

    private Integer getOrAssignEasterEggSkinIndex(ChunkKey key) {
        if (key == null) {
            return null;
        }
        Integer existing = easterEggSkinByKey.get(key);
        if (existing != null) {
            return existing;
        }
        int denominator = Math.max(1, easterEggDenominator);
        if (ThreadLocalRandom.current().nextInt(denominator) != 0) {
            return null;
        }
        int idx = ThreadLocalRandom.current().nextInt(2);
        easterEggSkinByKey.put(key, idx);
        return idx;
    }

    public int getEasterEggDenominator() {
        return Math.max(1, easterEggDenominator);
    }

    public void setEasterEggDenominator(int denominator) {
        easterEggDenominator = Math.max(1, denominator);
    }

    private void applyEasterEggAfterSpawn(ChunkKey key, ChunkloaderFakePlayer fakePlayer, boolean allowRandomAssign,
            boolean allowAnnounce) {
        if (fakePlayer == null || key == null || server == null) {
            return;
        }
        ChunkloaderTarget entry = activeTargets.get(key);
        if (entry == null) {
            return;
        }

        Integer idxBefore = easterEggSkinByKey.get(key);
        if (idxBefore == null && entry.easterEggSkinIndex() != null) {
            idxBefore = entry.easterEggSkinIndex();
            easterEggSkinByKey.put(key, idxBefore);
        }

        Integer idx = idxBefore;
        if (idx == null && allowRandomAssign) {
            idx = getOrAssignEasterEggSkinIndex(key);
        }
        if (idx == null) {
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), -1);
            applyFakePlayerMetadata(fakePlayer, entry, key);
            return;
        }

        boolean needsPersist = entry.easterEggSkinIndex() == null;
        if (needsPersist) {
            config.updateEntryEasterEggSkinIndex(entry.chunkX(), entry.chunkZ(), entry.dimension(), idx);
            ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
            if (updatedEntry != null) {
                activeTargets.put(key, updatedEntry);
                entry = updatedEntry;
            }
        }

        ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), idx);

        if (allowAnnounce && needsPersist) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal(EASTER_EGG_MESSAGE).withStyle(ChatFormatting.GOLD));
                }
            }
            ChunkloaderNetworking.broadcastEasterEggEmote(server, fakePlayer.getUUID(),
                    fakePlayer.level().getGameTime());
            noteEasterEggEmoteStart(fakePlayer.getUUID(), fakePlayer.level().getGameTime());
        }

        applyFakePlayerMetadata(fakePlayer, entry, key);
    }

    private void seedEasterEggFromEntry(ChunkKey key, ChunkloaderTarget entry) {
        if (key == null || entry == null) {
            return;
        }
        Integer idx = entry.easterEggSkinIndex();
        if (idx == null) {
            easterEggSkinByKey.remove(key);
        } else {
            easterEggSkinByKey.put(key, idx);
        }
    }

    private ChatFormatting determineFakePlayerColor(ChunkloaderTarget entry, ChunkKey key) {
        if (entry != null && entry.easterEggSkinIndex() != null) {
            return ChatFormatting.GOLD;
        }
        if (isEasterEgg(key)) {
            return ChatFormatting.GOLD;
        }
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
            return "Fakeplayer";
        }
        if (entry.name() != null) {
            String name = entry.name();
            String desiredPrefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            if (name.startsWith("Fakeplayer")) {
                return desiredPrefix + name.substring("Fakeplayer".length());
            }
            if (name.startsWith("Chunkplayer")) {
                return desiredPrefix + name.substring("Chunkplayer".length());
            }
            return name;
        }
        String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        return prefix + key.x() + "_" + key.z();
    }

    private void applyFakePlayerMetadata(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry, ChunkKey key) {
        String displayName = buildFakePlayerDisplayName(entry, key);
        ChatFormatting color = determineFakePlayerColor(entry, key);
        Component nameText = Component.literal(displayName).withStyle(color);
        fakePlayer.setCustomName(nameText);
        boolean nameVisible = entry.nameVisible();
        fakePlayer.setCustomNameVisible(nameVisible);
        fakePlayer.setVisibleAsMarker(true);
        fakePlayer.setMobTarget(entry.allowMobSpawning() && entry.mobTarget());
        fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
        de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, displayName, nameVisible);
        updateFakePlayerTeam(fakePlayer, entry);
        applySpawnFacing(fakePlayer, entry);

        if (hideAllFromTabList || tabListHidden.contains(key)) {
            if (hideAllFromTabList) {
                tabListHidden.add(key);
            }
            hideFromTabList(fakePlayer);
        }
    }

    private void hideFromTabList(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null || server == null || server.getPlayerList() == null) {
            return;
        }
        try {
            server.getPlayerList()
                    .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, fakePlayer));
        } catch (Exception ignored) {
        }
    }

    private void showInTabList(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null || server == null || server.getPlayerList() == null) {
            return;
        }
        try {
            server.getPlayerList()
                    .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, fakePlayer));
            server.getPlayerList()
                    .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, fakePlayer));
        } catch (Exception ignored) {
        }
    }

    public boolean setTabListVisible(String name, boolean visible) {
        if (name == null || name.isBlank()) {
            return false;
        }
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        ChunkKey key = chunkKey(entry);
        if (visible) {
            tabListHidden.remove(key);
        } else {
            tabListHidden.add(key);
        }

        ChunkloaderFakePlayer fp = activeFakePlayers.get(key);
        if (fp != null && fp.isAlive()) {
            if (visible) {
                showInTabList(fp);
            } else {
                hideFromTabList(fp);
            }
        }
        return true;
    }

    public int setTabListVisibleAll(boolean visible) {
        hideAllFromTabList = !visible;

        if (config != null) {
            try {
                config.setTabListVisibleAll(visible);
                config.save();
            } catch (Exception ignored) {
            }
        }
        int changed = 0;
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (entry == null || entry.name() == null) {
                continue;
            }
            ChunkKey key = chunkKey(entry);
            if (visible) {
                tabListHidden.remove(key);
            } else {
                tabListHidden.add(key);
            }
            ChunkloaderFakePlayer fp = activeFakePlayers.get(key);
            if (fp != null && fp.isAlive()) {
                if (visible) {
                    showInTabList(fp);
                } else {
                    hideFromTabList(fp);
                }
            }
            changed++;
        }
        return changed;
    }

    public boolean isTabListHidden(ServerPlayer player) {
        if (!(player instanceof ChunkloaderFakePlayer)) {
            return false;
        }
        ChunkKey key = markerToChunkKey.get(player.getUUID());
        return key != null && tabListHidden.contains(key);
    }

    public boolean isTabListVisibleAll() {
        return !hideAllFromTabList;
    }

    private void updateFakePlayerTeam(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry) {
        if (server == null || server.getScoreboard() == null) {
            return;
        }

        Scoreboard scoreboard = server.getScoreboard();
        ChatFormatting teamColor = determineFakePlayerColor(entry, chunkKey(entry));

        String teamName = "chunkloader_" + teamColor.getName().toLowerCase();

        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            try {
                team = scoreboard.addPlayerTeam(teamName);
                if (team == null) {
                    return;
                }
                team.setColor(teamColor);
                team.setDisplayName(Component.literal(
                        (entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer") + " " + teamColor.getName()));
            } catch (Exception e) {
                team = scoreboard.getPlayerTeam(teamName);
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

        PlayerTeam currentTeam = null;
        for (PlayerTeam existingTeam : scoreboard.getPlayerTeams()) {
            if (existingTeam.getPlayers().contains(playerName)) {
                currentTeam = existingTeam;
                break;
            }
        }

        if (currentTeam == team && team.getPlayers().contains(playerName)) {
            if (team.getColor() == teamColor) {
                return;
            }
            team.setColor(teamColor);
            sendTeamUpdatePackets(team, playerName);
            return;
        }

        if (currentTeam != null && currentTeam != team) {
            currentTeam.getPlayers().remove(playerName);
        }

        team.setColor(teamColor);

        if (!team.getPlayers().contains(playerName)) {
            team.getPlayers().add(playerName);
        }

        sendTeamUpdatePackets(team, playerName);
    }

    private void sendTeamUpdatePackets(PlayerTeam team, String playerName) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }

        try {
            net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket teamPacket = net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
                    .createAddOrModifyPacket(team, true);

            net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket playerPacket = net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
                    .createPlayerPacket(
                            team,
                            playerName,
                            net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.ADD);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null && player.connection != null) {
                    player.connection.send(teamPacket);
                    player.connection.send(playerPacket);
                }
            }
        } catch (Exception e) {
        }
    }

    private void forceEntitySync(ChunkloaderFakePlayer fakePlayer) {
        if (fakePlayer == null)
            return;

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
        String profileName = sanitizeProfileName(name, prefix, entry.chunkX(), entry.chunkZ());
        String data = "chunkloader:" + entry.dimension() + ":" + entry.chunkX() + ":" + entry.chunkZ();
        UUID uuid = UUID.nameUUIDFromBytes(data.getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, profileName);
    }

    private void spawnMarkerForChunkloader(ChunkKey key, ServerLevel world, BlockPos pos) {
        spawnMarkerForChunkloader(key, world, pos, false);
    }

    private void spawnMarkerForChunkloader(ChunkKey key, ServerLevel world, BlockPos pos, boolean allowRandomAssign) {
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null)
            return;
        seedEasterEggFromEntry(key, entry);

        if (!entry.enabled()) {
            return;
        }

        if (server != null && entry.name() != null) {
            for (net.minecraft.server.level.ServerPlayer onlinePlayer : server.getPlayerList()
                    .getPlayers()) {
                if (!(onlinePlayer instanceof ChunkloaderFakePlayer) &&
                        entry.name().equalsIgnoreCase(onlinePlayer.getGameProfile().name())) {
                    String suffix = entry.allowMobSpawning() ? "_Fakeplayer" : "_Chunkplayer";
                    String newName = entry.name() + suffix;
                    boolean success = config.updateEntryNameForced(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);
                    if (success) {

                        String type = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                        net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.literal(type + " '")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW)
                                .append(net.minecraft.network.chat.Component.literal(onlinePlayer.getGameProfile().name())
                                        .withStyle(net.minecraft.ChatFormatting.GOLD))
                                .append(net.minecraft.network.chat.Component.literal("' was automatically renamed to '")
                                        .withStyle(net.minecraft.ChatFormatting.YELLOW))
                                .append(net.minecraft.network.chat.Component.literal(newName)
                                        .withStyle(net.minecraft.ChatFormatting.GOLD))
                                .append(net.minecraft.network.chat.Component.literal("' because player '")
                                        .withStyle(net.minecraft.ChatFormatting.YELLOW))
                                .append(net.minecraft.network.chat.Component.literal(onlinePlayer.getGameProfile().name())
                                        .withStyle(net.minecraft.ChatFormatting.GOLD))
                                .append(net.minecraft.network.chat.Component.literal("' is already online.")
                                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList()
                                .getPlayers()) {
                            if (!(p instanceof ChunkloaderFakePlayer)) {
                                p.sendSystemMessage(message);
                            }
                        }

                        spawnMarkerForChunkloader(key, world, pos, allowRandomAssign);
                        return;
                    }
                    break;
                }
            }
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
                    forceEntitySync(finalFakePlayer);
                }
            });
        } else {
            fakePlayer = new ChunkloaderFakePlayer(server, world, createProfile(finalEntry));
            fakePlayer.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, normalizeSpawnYaw(finalEntry.spawnYaw()), 0.0F);

            applyFakePlayerMetadata(fakePlayer, finalEntry, key);

            try {
                activeFakePlayers.put(key, fakePlayer);
                boolean spawned = fakePlayer.spawn();
                if (!spawned || !fakePlayer.isRegistered()) {
                    activeFakePlayers.remove(key, fakePlayer);
                    ChunkloaderMod.LOGGER.error(
                            "Failed to spawn fake player marker at chunk ({}, {}): spawn did not register",
                            key.x(), key.z());
                    return;
                }
                applyEasterEggAfterSpawn(key, fakePlayer, allowRandomAssign, false);
                if (hideAllFromTabList || tabListHidden.contains(key)) {
                    if (hideAllFromTabList) {
                        tabListHidden.add(key);
                    }
                    hideFromTabList(fakePlayer);
                }
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.put(key, fakePlayerUuid);
                markerToChunkKey.put(fakePlayerUuid, key);

                final ChunkloaderFakePlayer finalFakePlayer = fakePlayer;
                server.execute(() -> {
                    if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                        applyFakePlayerMetadata(finalFakePlayer, finalEntry, key);
                        forceEntitySync(finalFakePlayer);
                    }
                });

                forceEntitySync(fakePlayer);
            } catch (Exception e) {
                activeFakePlayers.remove(key, fakePlayer);
                ChunkloaderMod.LOGGER.error("Failed to spawn fake player marker: {}", e.getMessage(), e);
            }
        }
    }

    private void updateMarkerForChunkloader(ChunkKey key) {
        UUID markerId = markerEntities.get(key);
        if (markerId == null)
            return;

        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
        if (entry == null)
            return;

        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        if (fakePlayer != null) {
            applyFakePlayerMetadata(fakePlayer, entry, key);
            boolean nameVisible = entry.nameVisible();

            @SuppressWarnings("resource")
            ServerLevel serverWorld = fakePlayer.level() instanceof ServerLevel
                    ? (ServerLevel) fakePlayer.level()
                    : null;
            if (serverWorld != null) {
                forceEntitySync(fakePlayer);

                final ChunkloaderFakePlayer finalFakePlayer2 = fakePlayer;
                final ServerLevel finalWorld2 = serverWorld;
                final boolean finalNameVisible2 = nameVisible;
                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer2.isAlive() && finalFakePlayer2.level() == finalWorld2) {
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

        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
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
                            (markerToChunkKey.containsKey(fakePlayerUuid)
                                    && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
                        entitiesToRemove.add(fakePlayer);
                    }
                }
            } else if (expectedName != null) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null && fakePlayer.level() == world) {
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    if ((markerId != null && fakePlayerUuid.equals(markerId)) ||
                            (markerToChunkKey.containsKey(fakePlayerUuid)
                                    && markerToChunkKey.get(fakePlayerUuid).equals(key))) {
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
                ChunkloaderMod.LOGGER.warn("Error removing marker entity: {}", e.getMessage());
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
                entry = config.getEntry(key.x(), key.z(), key.dimension());
            }

            if (entry != null) {
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world != null) {
                    BlockPos pos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
                    double expectedX = pos.getX() + 0.5;
                    double expectedY = pos.getY();
                    double expectedZ = pos.getZ() + 0.5;

                    boolean hasOtherMarker = false;
                    ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
                    if (existingFakePlayer != null && !existingFakePlayer.getUUID().equals(markerUuid)) {
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
                                ChunkloaderMod.LOGGER.error("Error despawning fakeplayer marker: {}", e.getMessage(),
                                        e);
                            }
                        }

                        UUID removedMarkerUuid = markerEntities.remove(key);
                        if (removedMarkerUuid != null) {
                            markerToChunkKey.remove(removedMarkerUuid);
                        }

                        if (activeTargets.containsKey(key)) {
                            deactivateChunkloader(key);
                        }

                        config.updateEntryEnabled(key.x(), key.z(), key.dimension(), false);
                        cancelPendingChunkloader(key);
                        ChunkloaderNetworking.closeOpenChunkMapsFor(server, key.x(), key.z(), key.dimension());
                        ChunkloaderNetworking.invalidateChunkCache();
                        ChunkloaderMod.LOGGER.info(
                                "Chunkloader at chunk ({}, {}) deactivated and added to disabled list",
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
            removeChunkloader(key.x(), key.z(), key.dimension());
        }
    }

    public void openChunkMap(UUID markerUuid, ServerPlayer player) {
        ChunkKey key = markerToChunkKey.get(markerUuid);
        if (key == null) {
            ChunkloaderMod.LOGGER.warn("Marker UUID {} not found in markerToChunkKey, searching by position",
                    markerUuid);
            for (ServerLevel world : server.getAllLevels()) {
                Entity entity = world.getEntity(markerUuid);
                if (entity instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
                    BlockPos pos = fakePlayer.blockPosition();
                    for (ChunkloaderTarget entry : config.getChunkEntries()) {
                        if (entry.blockX() == pos.getX() && entry.blockY() == pos.getY()
                                && entry.blockZ() == pos.getZ()) {
                            key = chunkKey(entry);
                            markerToChunkKey.put(markerUuid, key);
                            markerEntities.put(key, markerUuid);
                            break;
                        }
                    }
                    if (key != null)
                        break;
                }
            }
            if (key == null) {
                ChunkloaderMod.LOGGER.error("Could not find chunkloader for marker UUID {}", markerUuid);
                return;
            }
        }
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) {
            ChunkloaderMod.LOGGER.error("Could not find entry for chunk ({}, {})", key.x(), key.z());
            return;
        }

        if (!entry.enabled()) {
            ChunkloaderMod.LOGGER.warn("Cannot open ChunkMap for disabled chunkloader at chunk ({}, {})", key.x(),
                    key.z());
            return;
        }

        try {
            ChunkMapData data = buildChunkMapData(entry);
            ChunkloaderNetworking.sendOpenChunkMap(player, data);
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to open chunk map for chunk ({}, {})", entry.chunkX(), entry.chunkZ(),
                    e);
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

        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
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
        return config.getEntry(key.x(), key.z(), key.dimension());
    }

    public boolean toggleChunkloaderByName(String name) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }

        ChunkKey key = chunkKey(entry);
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world == null) {
            return false;
        }

        boolean newEnabled = !entry.enabled();
        BlockPos pos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());

        config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), newEnabled);

        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (updatedEntry == null) {
            return false;
        }

        if (newEnabled) {
            ChunkPos chunkPos = new ChunkPos(updatedEntry.chunkX(), updatedEntry.chunkZ());
            int radius = getEffectiveTicketSimulationRadius(updatedEntry);

            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = getEffectiveTicketSimulationRadius(oldEntry);
                    removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldRadius);
                }
            }

            addChunkloaderTickets(world, chunkPos, updatedEntry, radius);
            activeTargets.put(key, updatedEntry);

            ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                updateMarkerForChunkloader(key);
            } else {
                ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                        server,
                        world,
                        createProfile(updatedEntry));
                fakePlayer.snapTo(updatedEntry.blockX() + 0.5, updatedEntry.blockY(),
                        updatedEntry.blockZ() + 0.5, normalizeSpawnYaw(updatedEntry.spawnYaw()), 0.0F);

                String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                String displayName = updatedEntry.name() != null ? updatedEntry.name()
                        : (prefix + key.x() + "_" + key.z());
                net.minecraft.ChatFormatting color = determineFakePlayerColor(updatedEntry, key);
                Component nameText = Component.literal(displayName).withStyle(color);
                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, updatedEntry.dimension()));
                fakePlayer.setCustomNameVisible(updatedEntry.nameVisible());
                fakePlayer.setVisibleAsMarker(true);
                fakePlayer.setMobTarget(updatedEntry.allowMobSpawning() && updatedEntry.mobTarget());

                String plainName = displayName;
                de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName,
                        updatedEntry.nameVisible());

                try {
                    activeFakePlayers.put(key, fakePlayer);
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    markerEntities.put(key, fakePlayerUuid);
                    markerToChunkKey.put(fakePlayerUuid, key);

                    boolean spawned = fakePlayer.spawn();
                    if (!spawned || !fakePlayer.isRegistered()) {
                        removeAllChunkloaderTickets(world, chunkPos, updatedEntry, radius);
                        activeTargets.remove(key);
                        activeFakePlayers.remove(key, fakePlayer);
                        markerEntities.remove(key, fakePlayerUuid);
                        markerToChunkKey.remove(fakePlayerUuid, key);
                        ChunkloaderMod.LOGGER.error(
                                "Failed to spawn fake player during toggle at chunk ({}, {}): spawn did not register",
                                key.x(), key.z());
                        return newEnabled;
                    }
                    applyEasterEggAfterSpawn(key, fakePlayer, false, false);

                    ChunkloaderTarget finalUpdatedEntry = activeTargets.get(key);
                    if (finalUpdatedEntry != null) {
                        updateFakePlayerTeam(fakePlayer, finalUpdatedEntry);
                    }
                    if (hideAllFromTabList || tabListHidden.contains(key)) {
                        if (hideAllFromTabList) {
                            tabListHidden.add(key);
                        }
                        hideFromTabList(fakePlayer);
                    }
                } catch (Exception e) {
                    activeFakePlayers.remove(key, fakePlayer);
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    markerEntities.remove(key, fakePlayerUuid);
                    markerToChunkKey.remove(fakePlayerUuid, key);
                    ChunkloaderMod.LOGGER.error("Failed to spawn fake player during toggle: {}", e.getMessage(), e);
                }
            }
            playSpawnEffects(world, pos, key, updatedEntry, true);
        } else {
            deactivateChunkloader(key);

            String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            String displayName = updatedEntry.name() != null ? updatedEntry.name() : (prefix + key.x() + "_" + key.z());
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, displayName, false);

            playSpawnEffects(world, pos, key, updatedEntry, false);

            ChunkloaderNetworking.closeOpenChunkMapsFor(
                server,
                updatedEntry.chunkX(),
                updatedEntry.chunkZ(),
                updatedEntry.dimension()
            );
        }

        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);

        return newEnabled;
    }

    public boolean setChunkloaderNameVisible(String name, boolean visible) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry != null) {
            config.updateEntryNameVisible(entry.chunkX(), entry.chunkZ(), entry.dimension(), visible);
            ChunkKey key = chunkKey(entry);
            updateMarkerForChunkloader(key);
            return true;
        }
        return false;
    }


    public boolean setChunkloaderMobTarget(String name, boolean mobTarget) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }
        if (!entry.allowMobSpawning()) {
            return false;
        }
        config.updateEntryMobTarget(entry.chunkX(), entry.chunkZ(), entry.dimension(), mobTarget);
        ChunkKey key = chunkKey(entry);
        ChunkloaderTarget updated = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (updated != null && activeTargets.containsKey(key)) {
            activeTargets.put(key, updated);
        }
        updateMarkerForChunkloader(key);
        return true;
    }
    public boolean setChunkloaderAllowMobSpawning(String name, boolean allowMobSpawning) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }

        ChunkKey key = chunkKey(entry);
        boolean modeChanged = entry.allowMobSpawning() != allowMobSpawning;

        String pendingName = null;
        if (modeChanged && entry.name() != null) {
            String currentName = entry.name();
            boolean isStandardName = currentName.matches("^(?i)(fakeplayer|chunkplayer)\\d+$");
            if (!isStandardName) {
                String desiredPrefix = allowMobSpawning ? "Fakeplayer" : "Chunkplayer";
                if (currentName.startsWith("Fakeplayer")) {
                    pendingName = desiredPrefix + currentName.substring("Fakeplayer".length());
                } else if (currentName.startsWith("Chunkplayer")) {
                    pendingName = desiredPrefix + currentName.substring("Chunkplayer".length());
                }
                if (pendingName != null && !pendingName.equals(currentName)) {
                    ChunkloaderTarget conflict = config.getEntryByName(pendingName);
                    if (conflict != null
                            && (conflict.chunkX() != entry.chunkX() || conflict.chunkZ() != entry.chunkZ())) {
                        return false;
                    }
                }
            }
        }

        ChunkloaderTarget oldActiveEntry = activeTargets.get(key);

        config.updateEntryAllowMobSpawning(entry.chunkX(), entry.chunkZ(), entry.dimension(), allowMobSpawning);
        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (updatedEntry == null) {
            return false;
        }
        if (pendingName != null && !pendingName.equals(entry.name())) {
            config.updateEntryName(entry.chunkX(), entry.chunkZ(), entry.dimension(), pendingName);
            updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
            if (updatedEntry == null) {
                return false;
            }
        }

        seedEasterEggFromEntry(key, updatedEntry);

        ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
        boolean nameChanged = entry.name() != null && updatedEntry.name() != null
                && !entry.name().equals(updatedEntry.name());
        if (nameChanged && existingFakePlayer != null && existingFakePlayer.isAlive()) {
            respawnMarkerForChunkloader(key, updatedEntry);
            existingFakePlayer = activeFakePlayers.get(key);
        }

        if (updatedEntry.enabled() && oldActiveEntry != null) {
            ServerLevel world = getWorldByDimension(updatedEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(updatedEntry.chunkX(), updatedEntry.chunkZ());
                int oldRadius = getEffectiveTicketSimulationRadius(oldActiveEntry);
                int newRadius = getEffectiveTicketSimulationRadius(updatedEntry);
                removeAllChunkloaderTickets(world, chunkPos, oldActiveEntry, oldRadius);
                addChunkloaderTickets(world, chunkPos, updatedEntry, newRadius);
            }
        }

        if (activeTargets.containsKey(key) && updatedEntry.enabled()) {
            activeTargets.put(key, updatedEntry);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                if (modeChanged) {
                    applyEasterEggAfterSpawn(key, existingFakePlayer, true, true);
                    ChunkloaderNetworking.broadcastEasterEggEmote(server, existingFakePlayer.getUUID(),
                            existingFakePlayer.level().getGameTime());
                    noteEasterEggEmoteStart(existingFakePlayer.getUUID(), existingFakePlayer.level().getGameTime());
                } else {
                    Integer easterEggIdx = easterEggSkinByKey.get(key);
                    if (easterEggIdx != null) {
                        ChunkloaderNetworking.broadcastEasterEggSkin(server, existingFakePlayer.getUUID(),
                                easterEggIdx);
                    }
                    applyFakePlayerMetadata(existingFakePlayer, updatedEntry, key);
                }
                forceEntitySync(existingFakePlayer);
                updateMarkerForChunkloader(key);
            } else {
                ServerLevel world = getWorldByDimension(updatedEntry.dimension());
                if (world != null) {
                    spawnMarkerForChunkloader(key, world,
                            new BlockPos(updatedEntry.blockX(), updatedEntry.blockY(), updatedEntry.blockZ()),
                            modeChanged);
                }
            }
        } else {
            if (activeTargets.containsKey(key)) {
                activeTargets.put(key, updatedEntry);
            }
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                if (modeChanged) {
                    applyEasterEggAfterSpawn(key, existingFakePlayer, true, true);
                    ChunkloaderNetworking.broadcastEasterEggEmote(server, existingFakePlayer.getUUID(),
                            existingFakePlayer.level().getGameTime());
                    noteEasterEggEmoteStart(existingFakePlayer.getUUID(), existingFakePlayer.level().getGameTime());
                } else {
                    Integer easterEggIdx = easterEggSkinByKey.get(key);
                    if (easterEggIdx != null) {
                        ChunkloaderNetworking.broadcastEasterEggSkin(server, existingFakePlayer.getUUID(),
                                easterEggIdx);
                    }
                    applyFakePlayerMetadata(existingFakePlayer, updatedEntry, key);
                }
                forceEntitySync(existingFakePlayer);
            } else {
                updateMarkerForChunkloader(key);
            }
        }

        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return true;
    }

    public int clearAllChunkloaders() {
        int count = config.getChunkEntries().size();
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        for (ChunkloaderTarget entry : entries) {
            removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
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
        config.updateEntryChunkRadius(entry.chunkX(), entry.chunkZ(), entry.dimension(), radius);

        ChunkKey key = chunkKey(entry);

        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget activeEntry = activeTargets.get(key);
            ServerLevel world = getWorldByDimension(activeEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());

                int effectiveOldRadius = activeEntry.allowMobSpawning()
                        ? getEffectiveFakeplayerSpawnChunkRadius(oldRadius)
                        : oldRadius;
                int effectiveNewRadius = activeEntry.allowMobSpawning()
                        ? getEffectiveFakeplayerSpawnChunkRadius(radius)
                        : radius;

                removeAllChunkloaderTickets(world, chunkPos, activeEntry, effectiveOldRadius);
                addChunkloaderTickets(world, chunkPos, activeEntry, effectiveNewRadius);

                ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
                if (updatedEntry != null) {
                    activeTargets.put(key, updatedEntry);
                }
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return true;
    }

    public int enableAllChunkloaders() {
        int count = 0;
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());

        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), true);
                ChunkKey key = chunkKey(entry);
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world == null) {
                    ChunkloaderMod.LOGGER.warn("Cannot enable chunkloader at ({}, {}) in {}: world not loaded",
                            entry.chunkX(), entry.chunkZ(), entry.dimension());
                    continue;
                }
                if (!activeTargets.containsKey(key)) {
                    ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
                    if (updatedEntry != null) {
                        try {
                            activateChunkloader(updatedEntry, world);
                            count++;
                        } catch (Exception e) {
                            ChunkloaderMod.LOGGER.error("Failed to enable chunkloader at chunk ({}, {})",
                                    entry.chunkX(), entry.chunkZ(), e);
                        }
                    }
                }
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return count;
    }

    public int disableAllChunkloaders() {
        int count = 0;
        List<ChunkKey> keys = new ArrayList<>(activeTargets.keySet());

        for (ChunkKey key : keys) {
            ChunkloaderTarget entry = activeTargets.get(key);
            if (entry != null && entry.enabled()) {
                config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), false);
                deactivateChunkloader(key);
                ChunkloaderNetworking.closeOpenChunkMapsFor(
                    server,
                    entry.chunkX(),
                    entry.chunkZ(),
                    entry.dimension()
                );
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
                removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
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
                removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
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
                activeFakePlayers.size());
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

        int coreRadius = entry.chunkRadius();
        int entityTickRadius = entry.allowMobSpawning()
                ? getEffectiveFakeplayerSpawnChunkRadius(entry)
                : coreRadius;
        int blockTickRadius = entry.allowMobSpawning()
                ? fakeplayerBlockTickRadius(coreRadius)
                : chunkplayerBlockTickRadius(coreRadius);
        int loadingRadius = entry.allowMobSpawning()
                ? fakeplayerLoadingRadius(coreRadius)
                : chunkplayerLoadingRadius(coreRadius);

        for (int row = 0; row < mapHeight; row++) {
            for (int column = 0; column < mapWidth; column++) {
                int chunkX = topLeftChunkX + column;
                int chunkZ = topLeftChunkZ + row;
                int offsetX = chunkX - entry.chunkX();
                int offsetZ = chunkZ - entry.chunkZ();
                boolean withinRange = Math.abs(offsetX) <= coreRadius && Math.abs(offsetZ) <= coreRadius;
                boolean loaded = withinRange && entry.enabled();

                String simulatingFakeplayerName = null;
                boolean simulated = false;
                boolean occupied = false;

                if (entry.allowMobSpawning()) {
                    simulated = withinRange && entry.enabled();
                }

                String occupyingLabel = getOccupyingLoaderLabel(entries, entry, chunkX, chunkZ);
                if (occupyingLabel != null && !simulated) {
                    occupied = true;
                    simulatingFakeplayerName = occupyingLabel;
                }

                cells.add(new ChunkMapCell(offsetX, offsetZ, loaded, withinRange, occupied, simulated,
                        simulatingFakeplayerName));
            }
        }

        String displayName = entry.name() != null ? entry.name()
                : String.format("Chunk (%d, %d)", entry.chunkX(), entry.chunkZ());

        ChunkKey key = chunkKey(entry);
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
                String otherName = otherEntry.name() != null ? otherEntry.name()
                        : String.format("%s at (%d, %d)",
                                otherEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer",
                                otherEntry.chunkX(), otherEntry.chunkZ());
                otherChunkloaders.add(new de.chunkloader.network.ChunkloaderPosition(
                        otherEntry.chunkX(),
                        otherEntry.chunkZ(),
                        otherEntry.blockX(),
                        otherEntry.blockZ(),
                        otherName,
                        otherEntry.allowMobSpawning()));
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
                entityTickRadius,
                blockTickRadius,
                loadingRadius,
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
                chunkMapGeneration,
                entry.spawnYaw(),
                entry.name(),
                nameVisible,
                visualizeActive,
                visualize3DActive,
                canIncreaseRadius,
                otherChunkloaders,
                entry.ownerName(),
                isEasterEgg(chunkKey(entry)) || entry.easterEggSkinIndex() != null,
                entry.allowMobSpawning() && entry.mobTarget());
    }

    private void applySpawnFacing(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry) {
        if (fakePlayer == null || entry == null) {
            return;
        }
        float yaw = normalizeSpawnYaw(entry.spawnYaw());
        fakePlayer.setYRot(yaw);
        fakePlayer.setXRot(0.0F);
        fakePlayer.setYHeadRot(yaw);
        fakePlayer.setYBodyRot(yaw);
    }

    private static float normalizeSpawnYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }

        if (normalized >= 315.0f || normalized < 45.0f) {
            return 0.0f;
        }
        if (normalized >= 45.0f && normalized < 135.0f) {
            return 90.0f;
        }
        if (normalized >= 135.0f && normalized < 225.0f) {
            return 180.0f;
        }
        return -90.0f;
    }

    public boolean toggleChunkloaderAt(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null) {
            return false;
        }
        return toggleChunkloaderByName(entry.name());
    }

    public boolean toggleChunkloaderMobSpawning(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null) {
            return false;
        }
        return setChunkloaderAllowMobSpawning(entry.name(), !entry.allowMobSpawning());
    }

    public boolean adjustChunkloaderRadius(int chunkX, int chunkZ, String dimension, int delta) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
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
                ChunkloaderMod.LOGGER.warn("Cannot increase radius: Would overlap with chunkloader '{}'",
                        overlappingName);
                return false;
            }
        }

        return setChunkloaderRadius(entry.name(), newRadius);
    }

    public boolean wouldRadiusIncreaseOverlap(int chunkX, int chunkZ, int newRadius, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null) {
            return false;
        }
        String overlappingName = getOverlappingChunkloaderName(
                chunkX, chunkZ, newRadius, dimension, entry);
        return overlappingName != null;
    }

    public boolean toggleChunkloaderNameVisible(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null) {
            return false;
        }
        boolean newVisible = !entry.nameVisible();
        config.updateEntryNameVisible(chunkX, chunkZ, dimension, newVisible);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        updateMarkerForChunkloader(key);
        return true;
    }


    public boolean toggleChunkloaderMobTarget(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null || !entry.allowMobSpawning()) {
            return false;
        }
        boolean newValue = !entry.mobTarget();
        config.updateEntryMobTarget(chunkX, chunkZ, dimension, newValue);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ChunkloaderTarget updated = config.getEntry(chunkX, chunkZ, dimension);
        if (updated != null && activeTargets.containsKey(key)) {
            activeTargets.put(key, updated);
        }
        updateMarkerForChunkloader(key);
        return true;
    }
    public boolean toggleChunkloaderVisualize(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null) {
            return false;
        }
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        toggleVisualization(key);
        return true;
    }

    public boolean toggleChunkloaderVisualize3D(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null) {
            return false;
        }
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        toggleVisualization3D(key);
        return true;
    }

    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() > MAX_PROFILE_NAME_LENGTH) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9]+$");
    }

    private boolean isRealPlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
                if (player instanceof ChunkloaderFakePlayer) {
                    continue;
                }
                if (name.equalsIgnoreCase(player.getName().getString())) {
                    return true;
                }
            }
        }
        try {
            var playerManager = server.getPlayerList();
            var playerList = playerManager.getPlayers();
            for (ServerPlayer player : playerList) {
                if (player instanceof ChunkloaderFakePlayer) {
                    continue;
                }
                if (name.equalsIgnoreCase(player.getName().getString())) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private String sanitizeProfileName(String name, String prefix, int chunkX, int chunkZ) {
        String baseName = (name == null || name.isBlank())
                ? (prefix + chunkX + "_" + chunkZ)
                : name.trim();

        StringBuilder sanitized = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            sanitized.append(ok ? c : '_');
        }

        if (sanitized.isEmpty()) {
            sanitized.append(prefix);
        }

        if (sanitized.length() > MAX_PROFILE_NAME_LENGTH) {
            sanitized.setLength(MAX_PROFILE_NAME_LENGTH);
        }

        return sanitized.toString();
    }

    public boolean renameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        newName = newName.trim();

        if (!isValidName(newName)) {
            return false;
        }

        if (isRealPlayerName(newName)) {
            return false;
        }

        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null) {
            return false;
        }

        if (newName.equals(entry.name())) {
            return false;
        }

        String oldName = entry.name();
        boolean success = config.updateEntryName(chunkX, chunkZ, dimension, newName);
        if (!success) {
            return false;
        }

        if (oldName != null && !oldName.isBlank()) {
            migrateCustomSkinName(oldName, newName);
        }

        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ, dimension);
        if (updatedEntry != null && activeTargets.containsKey(key)) {
            respawnMarkerForChunkloader(key, updatedEntry);
        }
        return true;
    }

    public void checkAndRenameConflictingChunkloaders(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        for (ChunkloaderTarget entry : entries) {
            if (entry.name() != null && playerName.equalsIgnoreCase(entry.name())) {
                String suffix = entry.allowMobSpawning() ? "_Fakeplayer" : "_Chunkplayer";
                String finalName = entry.name() + suffix;

                ChunkKey key = chunkKey(entry);

                if (activeTargets.containsKey(key)) {
                    deactivateChunkloader(key);
                }

                String oldName = entry.name();

                boolean success = config.updateEntryNameForced(entry.chunkX(), entry.chunkZ(), entry.dimension(), finalName);
                if (!success) {
                    ChunkloaderMod.LOGGER.warn("Failed to rename chunkloader '{}' to '{}'", entry.name(), finalName);
                    continue;
                }

                if (oldName != null && !oldName.isBlank()) {
                    migrateCustomSkinName(oldName, finalName);
                }

                ChunkloaderMod.LOGGER.info("Renamed chunkloader '{}' to '{}' because player '{}' joined",
                        oldName, finalName, playerName);

                if (server != null) {
                    String type = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                    Component message = Component.empty()
                            .append(Component.literal(type + " '").withStyle(net.minecraft.ChatFormatting.YELLOW))
                            .append(Component.literal(entry.name()).withStyle(net.minecraft.ChatFormatting.GOLD))
                            .append(Component.literal("' was automatically renamed to '")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW))
                            .append(Component.literal(finalName).withStyle(net.minecraft.ChatFormatting.GOLD))
                            .append(Component.literal("' because player '").withStyle(net.minecraft.ChatFormatting.YELLOW))
                            .append(Component.literal(playerName).withStyle(net.minecraft.ChatFormatting.GOLD))
                            .append(Component.literal("' joined the game.").withStyle(net.minecraft.ChatFormatting.YELLOW));
                    for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                        onlinePlayer.sendSystemMessage(message);
                    }
                }

                ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
                if (updatedEntry != null) {
                    ServerLevel world = getWorldByDimension(updatedEntry.dimension());
                    if (world != null) {
                        activateChunkloader(updatedEntry, world);
                    }
                }
            }
        }
    }

    public boolean resetChunkloaderToDefaults(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null) {
            return false;
        }

        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);

        config.updateEntryNameVisible(chunkX, chunkZ, dimension, true);
        config.updateEntryMobTarget(chunkX, chunkZ, dimension, false);
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

    private String getOccupyingLoaderLabel(List<ChunkloaderTarget> entries, ChunkloaderTarget current, int chunkX,
            int chunkZ) {
        ChunkloaderTarget opposite = null;
        ChunkloaderTarget sameType = null;
        boolean currentIsFakeplayer = current.allowMobSpawning();

        for (ChunkloaderTarget other : entries) {
            if (other == current || other == null || !other.enabled()) {
                continue;
            }
            if (!other.dimension().equals(current.dimension())) {
                continue;
            }

            int otherRadius = other.chunkRadius();
            int dx = Math.abs(other.chunkX() - chunkX);
            int dz = Math.abs(other.chunkZ() - chunkZ);
            if (dx > otherRadius || dz > otherRadius) {
                continue;
            }

            if (other.allowMobSpawning() != currentIsFakeplayer) {
                if (opposite == null) {
                    opposite = other;
                }
            } else if (sameType == null) {
                sameType = other;
            }

            if (opposite != null && sameType != null) {
                break;
            }
        }

        ChunkloaderTarget chosen = opposite != null ? opposite : sameType;
        return chosen != null ? formatLoaderLabel(chosen) : null;
    }

    private String formatLoaderLabel(ChunkloaderTarget entry) {
        if (entry.name() != null && !entry.name().isBlank()) {
            return entry.name();
        }
        String type = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        return String.format("%s at (%d, %d)", type, entry.chunkX(), entry.chunkZ());
    }

    public record SimulationStatus(
            boolean inSimulatedChunk,
            String fakeplayerName,
            int chunkX,
            int chunkZ,
            int simulationDistance,
            int distance) {
    }

    public SimulationStatus getSimulationStatus(ServerPlayer player) {
        if (player == null) {
            return new SimulationStatus(false, null, 0, 0, 0, 0);
        }

        var world = (ServerLevel) player.level();
        if (world == null) {
            return new SimulationStatus(false, null, 0, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();

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
                closestFakeplayerName = entry.name() != null ? entry.name()
                        : String.format("Fakeplayer at (%d, %d)", entry.chunkX(), entry.chunkZ());
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
                closestDistance != Integer.MAX_VALUE ? closestDistance : -1);
    }

    public record ChunkplayerStatus(
            boolean inLoadedChunk,
            String chunkplayerName,
            int chunkX,
            int chunkZ,
            int radius,
            int distance) {
    }

    public ChunkplayerStatus getChunkplayerStatus(ServerPlayer player) {
        if (player == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }

        var world = (ServerLevel) player.level();
        if (world == null) {
            return new ChunkplayerStatus(false, null, 0, 0, 0, 0);
        }
        String dimension = getDimensionFromWorld(world);
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();

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
                closestChunkplayerName = entry.name() != null ? entry.name()
                        : String.format("Chunkplayer at (%d, %d)", entry.chunkX(), entry.chunkZ());
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
                closestDistance != Integer.MAX_VALUE ? closestDistance : -1);
    }

    public String getOverlappingChunkloaderName(int chunkX, int chunkZ, int radius, String dimension,
            ChunkloaderTarget excludeEntry) {
        List<ChunkloaderTarget> entries = config.getChunkEntries();

        for (ChunkloaderTarget other : entries) {
            if (other == null || other == excludeEntry) {
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
                return other.name() != null ? other.name()
                        : String.format("Chunk (%d, %d)", other.chunkX(), other.chunkZ());
            }
        }

        return null;
    }

    private boolean isPositionCoveredByOtherChunkloader(int chunkX, int chunkZ, int radius, String dimension,
            ChunkloaderTarget excludeEntry) {
        List<ChunkloaderTarget> entries = config.getChunkEntries();

        for (ChunkloaderTarget other : entries) {
            if (other == null || other == excludeEntry) {
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

    public record ChunkloaderStats(int total, int enabled, int disabled, int loadedChunks, int activeFakePlayers) {
    }

    public record ChunkloaderPerformanceStats(
            int totalLoadedChunks,
            long usedMemory,
            long maxMemory,
            double memoryUsagePercent,
            int activeFakePlayers) {
    }

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
                        false,
                        entry.easterEggSkinIndex() != null ? entry.easterEggSkinIndex() : -1));
            }
        }

        return Collections.unmodifiableList(result);
    }

    public void deleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null && !entry.enabled()) {
            removeChunkloader(chunkX, chunkZ, dimension);
        }
    }

    public void restoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null && !entry.enabled()) {
            config.updateEntryEnabled(chunkX, chunkZ, dimension, true);
            ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
            ServerLevel world = getWorldByDimension(entry.dimension());
            if (world != null && !activeTargets.containsKey(key)) {
                ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ, dimension);
                if (updatedEntry != null) {
                    try {
                        activateChunkloader(updatedEntry, world, false, true);
                        ChunkloaderNetworking.broadcastCloseChunkMap(server);
                    } catch (Exception e) {
                        ChunkloaderMod.LOGGER.error("Failed to restore chunkloader at chunk ({}, {})", chunkX, chunkZ,
                                e);
                    }
                }
            }
            ChunkloaderNetworking.invalidateChunkCache();
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        }
    }

    public boolean updateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ,
            int newBlockX, int newBlockY, int newBlockZ) {
        String errorMessage = updateDisabledChunkloaderCoordsWithMessage(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ);
        return errorMessage == null;
    }

    public String updateDisabledChunkloaderCoordsWithMessage(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ,
            int newBlockX, int newBlockY, int newBlockZ) {
        ChunkloaderTarget entry = config.getEntry(oldChunkX, oldChunkZ, oldDimension);
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
            ChunkloaderMod.LOGGER.warn(
                    "Cannot update disabled chunkloader coordinates: Position ({}, {}) is already covered by another enabled chunkloader",
                    newChunkX, newChunkZ);
            return "Cannot update coordinates: Position is already covered by another enabled player.";
        }

        ChunkloaderTarget existingAtNewPos = config.getEntry(newChunkX, newChunkZ, entry.dimension());
        if (existingAtNewPos != null && existingAtNewPos != entry
                && existingAtNewPos.dimension().equals(entry.dimension())) {
            ChunkloaderMod.LOGGER.warn(
                    "Cannot update disabled chunkloader coordinates: Position ({}, {}) is already occupied",
                    newChunkX, newChunkZ);
            return "Cannot update coordinates: Position is already occupied by another player.";
        }

        boolean success = config.addOrUpdateEntry(
                newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ,
                entry.name(),
                entry.dimension(),
                entry,
                false);

        if (!success) {
            ChunkloaderMod.LOGGER.warn(
                    "Failed to update disabled chunkloader coordinates from ({}, {}) to ({}, {}): addOrUpdateEntry failed",
                    oldChunkX, oldChunkZ, newChunkX, newChunkZ);
            return "Failed to update coordinates: The name may already exist or the position is invalid.";
        }

        if (oldChunkX != newChunkX || oldChunkZ != newChunkZ) {
            config.removeEntry(oldChunkX, oldChunkZ, entry.dimension());
        }

        ChunkloaderMod.LOGGER.info("Updated disabled chunkloader coordinates from ({}, {}) to ({}, {})",
                oldChunkX, oldChunkZ, newChunkX, newChunkZ);
        return null;
    }

}
