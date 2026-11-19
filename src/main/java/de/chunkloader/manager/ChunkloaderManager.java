package de.chunkloader.manager;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.CustomFakePlayerSkinStore;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.ChunkMapLoaderDetailsPayload;
import de.chunkloader.network.payload.ChunkMapLoaderDetailsPayload.DisabledArea;
import de.chunkloader.permissions.PermissionManager;
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
import net.minecraft.world.scores.TeamColor;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.Ticket;
import de.chunkloader.util.IndependentTicking;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<String> knownRealPlayerNamesLower = ConcurrentHashMap.newKeySet();
    private final Set<VisualizationKey> visualizationActive = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<VisualizationKey, Visualization3DConfig> visualization3DActive = new ConcurrentHashMap<>();
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

    private static final TicketType CHUNKLOADER_LOADING_TICKET = new TicketType(
            TicketType.PLAYER_LOADING.timeout(),
            TicketType.PLAYER_LOADING.flags() | IndependentTicking.OWN_TICKET_FLAG);
    private static final TicketType CHUNKLOADER_SIMULATION_TICKET = new TicketType(
            TicketType.PLAYER_SIMULATION.timeout(),
            TicketType.PLAYER_SIMULATION.flags() | IndependentTicking.OWN_TICKET_FLAG);

    private int tickCounter = 0;
    private String storedWorldName = null;

    private static final int DEFAULT_EASTER_EGG_DENOMINATOR = 500;
    private volatile int easterEggDenominator = DEFAULT_EASTER_EGG_DENOMINATOR;
    private static final String EASTER_EGG_MESSAGE = "An old friend has returned to watch over the world, their presence keeping the chunks alive.";
    private final ConcurrentMap<ChunkKey, Integer> easterEggSkinByKey = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, Long> easterEggEmoteStartByUuid = new ConcurrentHashMap<>();
    private static final double JOIN_EMOTE_MAX_DISTANCE = 24.0;
    private static final long EMOTE_MAX_DURATION_TICKS = 101L;

    private static void addChunkloaderTickets(ServerLevel world, ChunkPos chunkPos, int radius) {
        int level = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - radius;
        ServerChunkCache chunks = world.getChunkSource();
        chunks.addTicket(new Ticket(CHUNKLOADER_LOADING_TICKET, level), chunkPos);
        chunks.addTicket(new Ticket(CHUNKLOADER_SIMULATION_TICKET, level), chunkPos);
    }

    private static void removeChunkloaderTickets(ServerLevel world, ChunkPos chunkPos, int radius) {
        int level = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - radius;
        ServerChunkManagerAccessor chunks = (ServerChunkManagerAccessor) world.getChunkSource();
        var tickets = chunks.chunkloader$getTicketStorage();
        tickets.removeTicket(new Ticket(CHUNKLOADER_LOADING_TICKET, level), chunkPos);
        tickets.removeTicket(new Ticket(CHUNKLOADER_SIMULATION_TICKET, level), chunkPos);
    }

    private static final boolean DEBUG_LOADED_CHUNKS = Boolean.getBoolean("chunkloader.debugLoadedChunks");
    private static final int DEBUG_LOADED_CHUNKS_INTERVAL_TICKS = Integer
            .getInteger("chunkloader.debugLoadedChunksIntervalTicks", 100);
    private int debugLoadedChunksCounter = 0;

    final ConcurrentMap<ServerLevel, String> dimensionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerLevel> dimensionToWorldCache = new ConcurrentHashMap<>();

    private record VisualizationKey(UUID viewer, ChunkKey loader) {
    }

    private record Visualization3DConfig(int minY, int maxY) {
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
            com.mojang.logging.LogUtils.getLogger().warn("Failed to get world for dimension: {}", dimension, e);
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
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager != null) {
            return manager.dimensionCache.computeIfAbsent(world, w -> w.dimension().identifier().toString());
        }
        return world.dimension().identifier().toString();
    }

    private Path determineConfigPath(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        try {
            return de.chunkloader.config.ChunkloaderPaths.getChunkloaderDir(server).resolve("chunkloader_config.json")
                    .toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameConfigPath(Path expected, Path actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        try {
            return expected.toAbsolutePath().normalize().equals(actual.toAbsolutePath().normalize());
        } catch (Exception e) {
            return expected.equals(actual);
        }
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

    public boolean toggleVisualization(ServerPlayer viewer, ChunkloaderTarget entry) {
        VisualizationKey key = new VisualizationKey(viewer.getUUID(), chunkKey(entry));
        if (visualizationActive.remove(key)) {
            return false;
        }
        visualizationActive.add(key);
        return true;
    }

    public boolean toggleVisualization3D(ServerPlayer viewer, ChunkloaderTarget entry) {
        return toggleVisualization3D(viewer, entry, ChunkloaderConstants.MIN_BLOCK_Y, ChunkloaderConstants.MAX_BLOCK_Y);
    }

    public boolean toggleVisualization3D(ServerPlayer viewer, ChunkloaderTarget entry, int minY, int maxY) {
        VisualizationKey key = new VisualizationKey(viewer.getUUID(), chunkKey(entry));
        if (visualization3DActive.remove(key) != null) {
            return false;
        }
        visualization3DActive.put(key, new Visualization3DConfig(minY, maxY));
        return true;
    }

    private void clearLoaderVisualizations(ChunkKey loader) {
        visualizationActive.removeIf(key -> key.loader().equals(loader));
        visualization3DActive.keySet().removeIf(key -> key.loader().equals(loader));
    }

    public void clearViewerVisualizations(UUID viewer) {
        visualizationActive.removeIf(key -> key.viewer().equals(viewer));
        visualization3DActive.keySet().removeIf(key -> key.viewer().equals(viewer));
    }

    private void renderChunkBorders(ServerLevel world, ServerPlayer viewer, ChunkloaderTarget entry) {
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
                world.sendParticles(viewer, ParticleTypes.ELECTRIC_SPARK, false, false, worldX, y, z,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int worldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
            for (int x = minChunkX * ChunkloaderConstants.CHUNK_SIZE; x <= (maxChunkX + 1)
                    * ChunkloaderConstants.CHUNK_SIZE; x += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                world.sendParticles(viewer, ParticleTypes.ELECTRIC_SPARK, false, false, x, y, worldZ,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
    }

    private net.minecraft.core.particles.ParticleOptions getVisualization3DParticle(ServerLevel world) {
        return world != null && world.isBrightOutside() ? ParticleTypes.SCRAPE : ParticleTypes.FLAME;
    }

    private void renderChunkBorders3D(ServerLevel world, ServerPlayer viewer, ChunkloaderTarget entry,
            Visualization3DConfig config) {
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

                for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldX, y, chunkWorldZ,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
                for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldXEnd, y, chunkWorldZ,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
                for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldX, y, chunkWorldZEnd,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
                for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldXEnd, y, chunkWorldZEnd,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }

                for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, x, maxY, chunkWorldZ,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    world.sendParticles(viewer, particleType, false, false, x, maxY, chunkWorldZEnd,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
                for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldX, maxY, z,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    world.sendParticles(viewer, particleType, false, false, chunkWorldXEnd, maxY, z,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }

                for (int x = chunkWorldX; x <= chunkWorldXEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, x, minY, chunkWorldZ,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    world.sendParticles(viewer, particleType, false, false, x, minY, chunkWorldZEnd,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
                for (int z = chunkWorldZ; z <= chunkWorldZEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
                    world.sendParticles(viewer, particleType, false, false, chunkWorldX, minY, z,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                    world.sendParticles(viewer, particleType, false, false, chunkWorldXEnd, minY, z,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                            ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
                }
            }
        }
    }

    private void renderVisualization(VisualizationKey key, Visualization3DConfig bounds) {
        ServerPlayer viewer = server.getPlayerList().getPlayer(key.viewer());
        ChunkKey loader = key.loader();
        ChunkloaderTarget target = config.getEntry(loader.x(), loader.z(), loader.dimension());
        if (target == null || !target.enabled() || !PermissionManager.canUse(viewer)) {
            visualizationActive.remove(key);
            visualization3DActive.remove(key);
            return;
        }

        ServerLevel world = getWorldByDimension(loader.dimension());
        if (world == null || viewer.level() != world) {
            return;
        }
        if (bounds == null) {
            renderChunkBorders(world, viewer, target);
        } else {
            renderChunkBorders3D(world, viewer, target, bounds);
        }
    }

    public void tick() {
        processPendingChunkloaderActivations();
        processPendingPlayerJoinSyncs();

        if (tickCounter % 10 == 0) {
            visualizationActive.forEach(key -> renderVisualization(key, null));
            visualization3DActive.forEach(this::renderVisualization);
        }

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            ensureChunksLoaded();
        }

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

    private void debugDumpLoadedChunks() {
        if (activeTargets.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().info("[chunkloader-debug] activeTargets is empty");
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
                com.mojang.logging.LogUtils.getLogger().info(
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
            com.mojang.logging.LogUtils.getLogger().info(
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
                    boolean allowedTick = isFakeplayerEntityTickChunk(cx, cz, worldDim)
                            || isChunkplayerEntityTickChunk(cx, cz, worldDim);

                    if (!loaded && !allowedTick) {
                        continue;
                    }

                    String info;
                    try {
                        info = chunkManager.getChunkDebugData(new ChunkPos(cx, cz));
                    } catch (Exception e) {
                        info = "<debugInfoError:" + e.getClass().getSimpleName() + ">";
                    }

                    com.mojang.logging.LogUtils.getLogger().info(
                            "[chunkloader-debug] chunk=({}, {}) loaded={} allowedTick={} info={}",
                            cx, cz, loaded, allowedTick, info);
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
                com.mojang.logging.LogUtils.getLogger().error(
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
                        com.mojang.logging.LogUtils.getLogger().error("Failed to activate chunkloader during sync check", e);
                    }
                } else {
                    ChunkloaderTarget activeEntry = activeTargets.get(key);
                    if (activeEntry != null && activeEntry.chunkRadius() != entry.chunkRadius()) {
                        deactivateChunkloader(key);
                        try {
                            activateChunkloader(entry, world);
                        } catch (Exception e) {
                            com.mojang.logging.LogUtils.getLogger().error("Failed to reactivate chunkloader after radius change", e);
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
        com.mojang.logging.LogUtils.getLogger().info("Loading persistent chunkloaders...");

        String currentWorldName = null;
        try {
            if (server != null && server.getWorldData() != null) {
                currentWorldName = server.getWorldData().getLevelName();
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not determine current world name", e);
        }
        com.mojang.logging.LogUtils.getLogger().info("Current world name: '{}'", currentWorldName != null ? currentWorldName : "unknown");

        cleanup();

        Path expectedConfigPath = determineConfigPath(server);
        Path currentConfigPath = config.getConfigPath();

        com.mojang.logging.LogUtils.getLogger().info("Expected config path: {}, Current config path: {}",
                expectedConfigPath != null ? expectedConfigPath.toString() : "null",
                currentConfigPath != null ? currentConfigPath.toString() : "null");

        if (expectedConfigPath != null && !sameConfigPath(expectedConfigPath, currentConfigPath)) {
            com.mojang.logging.LogUtils.getLogger().info("Config path changed - reloading config from {}", expectedConfigPath);
        } else if (currentWorldName != null) {
            String storedWorldName = getStoredWorldName();
            if (storedWorldName == null) {
                com.mojang.logging.LogUtils.getLogger().info("First load for world '{}' - reloading config", currentWorldName);
            } else if (!currentWorldName.equals(storedWorldName)) {
                com.mojang.logging.LogUtils.getLogger().info("Level name changed from '{}' to '{}' - reloading config",
                        storedWorldName, currentWorldName);
            } else {
                com.mojang.logging.LogUtils.getLogger().info("Reloading config for world '{}' to ensure consistency", currentWorldName);
            }
        } else {
            com.mojang.logging.LogUtils.getLogger().info("Cannot determine world name - reloading config to be safe");
        }

        config.flushPendingSave();
        ChunkloaderConfig newConfig = ChunkloaderConfig.load(server);

        this.config = newConfig;
        ChunkloaderForgeMod.setConfig(newConfig);
        this.hideAllFromTabList = !newConfig.isTabListVisibleAll();
        com.mojang.logging.LogUtils.getLogger().info("Config reloaded for world '{}' - {} entries loaded",
                currentWorldName != null ? currentWorldName : "unknown", newConfig.getChunkEntries().size());

        storeWorldName(currentWorldName);

        currentConfigPath = newConfig.getConfigPath();

        if (expectedConfigPath != null && !sameConfigPath(expectedConfigPath, currentConfigPath)) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "Config path mismatch after reload! Expected: {}, Actual: {}. Skipping load to prevent cross-world contamination.",
                    expectedConfigPath, currentConfigPath);
            return;
        }

        Set<String> loadedDimensions = new HashSet<>();
        for (ServerLevel world : server.getAllLevels()) {
            loadedDimensions.add(getDimensionFromWorld(world));
        }
        com.mojang.logging.LogUtils.getLogger().info("Currently loaded dimensions: {}", loadedDimensions);

        Map<String, Integer> dimensionCounts = new HashMap<>();

        com.mojang.logging.LogUtils.getLogger().info("Loading {} chunkloader entries from config", config.getChunkEntries().size());
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
                com.mojang.logging.LogUtils.getLogger().warn("Level for dimension {} not available, skipping chunkloader at ({}, {})",
                        entry.dimension(), entry.chunkX(), entry.chunkZ());
                continue;
            }

            scheduleChunkloaderInitialization(entry, PENDING_ACTIVATION_INITIAL_DELAY_TICKS);
            dimensionCounts.put(entry.dimension(), dimensionCounts.getOrDefault(entry.dimension(), 0) + 1);
        }

        int totalScheduled = dimensionCounts.values().stream().mapToInt(Integer::intValue).sum();
        com.mojang.logging.LogUtils.getLogger().info(
                "Scheduled {} chunkloaders for delayed initialization in world '{}' across {} dimensions ({} pending entries)",
                totalScheduled, currentWorldName != null ? currentWorldName : "unknown", dimensionCounts.size(),
                pendingChunkloaderActivations.size());
    }

    public void savePersistentChunkloaders() {
        com.mojang.logging.LogUtils.getLogger().info("Saving chunkloader data...");
    }

    public void cleanup() {
        com.mojang.logging.LogUtils.getLogger().info("Cleaning up chunkloaders...");

        int despawnedCount = 0;

        if (server != null && server.getPlayerList() != null) {
            try {
                List<ServerPlayer> allPlayers = new ArrayList<>(server.getPlayerList().getPlayers());
                for (ServerPlayer player : allPlayers) {
                    if (player instanceof ChunkloaderFakePlayer fakePlayer) {
                        try {
                            fakePlayer.despawn();
                            despawnedCount++;
                            com.mojang.logging.LogUtils.getLogger().info("Despawned fakeplayer from PlayerList: {}",
                                    player.getName().getString());
                        } catch (Exception e) {
                            com.mojang.logging.LogUtils.getLogger().error("Error despawning fakeplayer from PlayerList: {}",
                                    e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Error accessing PlayerList: {}", e.getMessage());
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
                                com.mojang.logging.LogUtils.getLogger().info("Despawned fakeplayer from world {}: {}",
                                        world.dimension().identifier(), entity.getName().getString());
                            } else if (entity instanceof ServerPlayer player) {
                                if (server.getPlayerList() != null) {
                                    server.getPlayerList().remove(player);
                                }
                                if (player.connection != null) {
                                    player.connection.disconnect(Component.literal("cleanup"));
                                }
                                despawnedCount++;
                                com.mojang.logging.LogUtils.getLogger().info("Despawned potential fakeplayer from world {} by name: {}",
                                        world.dimension().identifier(), entity.getName().getString());
                            }
                        } catch (Exception e) {
                            com.mojang.logging.LogUtils.getLogger().error("Error despawning fakeplayer from world: {}", e.getMessage(),
                                    e);
                        }
                    }
                } catch (Exception e) {
                    com.mojang.logging.LogUtils.getLogger().warn("Error searching for fakeplayers in world {}: {}",
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
                        int radius = entry.chunkRadius();
                        removeChunkloaderTickets(world, chunkPos, radius);
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
                com.mojang.logging.LogUtils.getLogger().error("Error during cleanup of chunkloader at chunk ({}, {})", key.x(), key.z(),
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
                    com.mojang.logging.LogUtils.getLogger().error("Error removing marker entity: {}", e.getMessage(), e);
                }
            }
        }

        clearActiveTargets();
        activeFakePlayers.clear();
        markerEntities.clear();
        markerToChunkKey.clear();
        visualizationActive.clear();
        visualization3DActive.clear();
        pendingChunkloaderActivations.clear();

        com.mojang.logging.LogUtils.getLogger().info(
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
        return addChunkloader(chunkX, chunkZ, blockPos, name, world, ownerName, spawnYaw, null);
    }

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world,
            String ownerName, float spawnYaw, UUID ownerUuid) {
        if (name == null) {
            boolean isFakePlayer = true;
            name = config.generateNextName(isFakePlayer);
            com.mojang.logging.LogUtils.getLogger().info("Generated name for chunkloader at ({}, {}): {}", chunkX, chunkZ, name);
        }

        if (config.hasEntryByName(name)) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot add chunkloader: Name '{}' already exists", name);
            return false;
        }

        String dimension = getDimensionFromWorld(world);

        ChunkloaderTarget existingEntry = config.getEntry(chunkX, chunkZ, dimension);
        if (existingEntry != null && existingEntry.dimension().equals(dimension)) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot add chunkloader at ({}, {}) in {}: entry already exists (enabled={})",
                    chunkX, chunkZ, dimension, existingEntry.enabled());
            return false;
        }

        int defaultRadius = 0;
        if (getOverlappingChunkloaderName(chunkX, chunkZ, defaultRadius, dimension, null) != null) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Cannot add chunkloader at ({}, {}): Position is already covered by another active chunkloader",
                    chunkX, chunkZ);
            return false;
        }
        float normalizedSpawnYaw = normalizeSpawnYaw(spawnYaw);
        boolean success = config.addOrUpdateEntry(chunkX, chunkZ, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                name, dimension, null, null, ownerName, normalizedSpawnYaw, ownerUuid);
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
                com.mojang.logging.LogUtils.getLogger().error("Failed to activate chunkloader at chunk ({}, {})", chunkX, chunkZ, e);
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
        ChunkloaderConfig.RemoveResult removeResult = config.removeEntry(x, z, dimension);
        boolean removed = removeResult.removed();

        if (removed) {
            chunkMapGeneration++;
            ChunkKey key = new ChunkKey(dimension, x, z);
            cancelPendingChunkloader(key);
            deactivateChunkloader(key);
            clearLoaderVisualizations(key);
            if (removedName != null && !removedName.isBlank()) {
                customSkinStore.remove(removedName);
                ChunkloaderNetworking.broadcastClearCustomSkin(server, removedName);
            }
            for (ChunkloaderConfig.NameRename rename : removeResult.renames()) {
                migrateCustomSkinName(rename.oldName(), rename.newName());
            }
            ChunkloaderNetworking.closeOpenChunkMapsFor(server, x, z, dimension);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
            com.mojang.logging.LogUtils.getLogger().info("Removed chunkloader at chunk {}, {}", x, z);
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
                    int oldRadius = oldEntry.chunkRadius();
                    removeChunkloaderTickets(oldWorld, oldChunkPos, oldRadius);
                }
            }
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            addChunkloaderTickets(world, chunkPos, radius);
            putActiveTarget(key, entry);

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
                removeChunkloaderTickets(oldWorld, oldChunkPos,
                        oldEntry.chunkRadius());
            }
        }

        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
        int radius = entry.chunkRadius();

        try {
            addChunkloaderTickets(world, chunkPos, radius);
            putActiveTarget(key, entry);

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
                    removeChunkloaderTickets(world, chunkPos, radius);
                    removeActiveTarget(key);
                    activeFakePlayers.remove(key, fakePlayer);
                    markerEntities.remove(key, fakePlayerUuid);
                    markerToChunkKey.remove(fakePlayerUuid, key);
                    com.mojang.logging.LogUtils.getLogger().error(
                            "Failed to activate chunkloader at chunk ({}, {}): fake player spawn did not register",
                            key.x(), key.z());
                    return;
                }
                applySpawnFacing(fakePlayer, entry);
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
                removeChunkloaderTickets(world, chunkPos, radius);
                removeActiveTarget(key);
                activeFakePlayers.remove(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.remove(key, fakePlayerUuid);
                markerToChunkKey.remove(fakePlayerUuid, key);
                if (e.getMessage() != null && e.getMessage().contains("packettweaker")) {
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "Failed to activate chunkloader at chunk ({}, {}) due to mod incompatibility (polymer-core/packet_tweaker). "
                                    +
                                    "The chunkloader will not be activated. Error: {}",
                            key.x(), key.z(), e.getMessage());
                } else {
                    com.mojang.logging.LogUtils.getLogger().error("Failed to spawn fake player: {}", e.getMessage(), e);
                }
                return;
            }

            BlockPos blockPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
            if (playEffects) {
                playSpawnEffects(world, blockPos, key, activeTargets.getOrDefault(key, entry), true);
            }

            String mode = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
            com.mojang.logging.LogUtils.getLogger().info("Activated {} at chunk {}, {} (block {}, {}, {})",
                    mode, entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to activate chunkloader at chunk ({}, {})", entry.chunkX(),
                    entry.chunkZ(), e);
            removeActiveTarget(key);
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
        ChunkloaderTarget entry = removeActiveTarget(key);
        if (entry == null) {
            entry = config.getEntry(key.x(), key.z(), key.dimension());
        }
        if (entry == null) {
            return;
        }

        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null) {
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            removeChunkloaderTickets(world, chunkPos, radius);
        }
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

        clearLoaderVisualizations(key);

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
        ChunkloaderTarget entry = config.getEntryByName(playerName);
        if (entry != null && entry.easterEggSkinIndex() != null) {
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
            com.mojang.logging.LogUtils.getLogger().warn("Failed to apply custom skin for '{}': {}", playerName, e.getMessage());
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
            putActiveTarget(key, updatedEntry);
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

    private final java.util.concurrent.atomic.AtomicLong spawnStateRevision = new java.util.concurrent.atomic.AtomicLong();

    public long getSpawnStateRevision() {
        return spawnStateRevision.get();
    }

    public void invalidateSpawnPlayerLists() {
        spawnStateRevision.incrementAndGet();
    }

    private void putActiveTarget(ChunkKey key, ChunkloaderTarget target) {
        activeTargets.put(key, target);
        invalidateSpawnPlayerLists();
    }

    private ChunkloaderTarget removeActiveTarget(ChunkKey key) {
        ChunkloaderTarget removed = activeTargets.remove(key);
        if (removed != null) invalidateSpawnPlayerLists();
        return removed;
    }

    private void clearActiveTargets() {
        activeTargets.clear();
        invalidateSpawnPlayerLists();
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

        ChunkloaderTarget entry = activeTargets.get(key);
        if (entry == null) {
            entry = config.getEntry(key.x(), key.z(), key.dimension());
        }

        if (entry == null) {
            return false;
        }

        return entry.allowMobSpawning();
    }

    public ChunkloaderTarget getActiveFakeplayerSpawnEntry(ChunkloaderFakePlayer player) {
        ChunkKey key = markerToChunkKey.get(player.getUUID());
        ChunkloaderTarget entry = key == null ? null : activeTargets.get(key);
        return entry != null && entry.enabled() && entry.allowMobSpawning() ? entry : null;
    }

    public boolean isFakeplayerSpawnChunk(ChunkloaderFakePlayer player, ChunkPos pos) {
        ChunkloaderTarget entry = getActiveFakeplayerSpawnEntry(player);
        return entry != null
                && Math.abs(entry.chunkX() - pos.x()) <= entry.chunkRadius()
                && Math.abs(entry.chunkZ() - pos.z()) <= entry.chunkRadius();
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

    public boolean isChunkplayerMobAiChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || target.allowMobSpawning() || !target.mobAi() || !target.dimension().equals(dimension)) {
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

    public boolean isFakeplayerEntityTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || !target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int r = target.chunkRadius();
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= r && dz <= r) {
                return true;
            }
        }
        return false;
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
                putActiveTarget(key, updatedEntry);
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
            return entry.name();
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
        TeamColor tc = TeamColor.valueOf(teamColor.name());

        String teamName = "chunkloader_" + teamColor.name().toLowerCase();

        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            try {
                team = scoreboard.addPlayerTeam(teamName);
                if (team == null) {
                    return;
                }
                team.setColor(Optional.of(tc));
                team.setDisplayName(Component.literal(
                        (entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer") + " " + teamColor.name()));
            } catch (Exception e) {
                team = scoreboard.getPlayerTeam(teamName);
                if (team == null) {
                    return;
                }
            }
        } else {
            team.setColor(Optional.of(tc));
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
            if (team.getColor().orElse(null) == tc) {
                return;
            }
            team.setColor(Optional.of(tc));
            sendTeamUpdatePackets(team, playerName);
            return;
        }

        if (currentTeam != null && currentTeam != team) {
            currentTeam.getPlayers().remove(playerName);
        }

        team.setColor(Optional.of(tc));

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
                    String oldSkinName = entry.name();
                    String suffix = entry.allowMobSpawning() ? "_Fakeplayer" : "_Chunkplayer";
                    String newName = oldSkinName + suffix;
                    boolean success = config.updateEntryNameForced(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);
                    if (success) {
                        migrateCustomSkinName(oldSkinName, newName);

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

                        spawnMarkerForChunkloader(key, world, pos);
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
                    com.mojang.logging.LogUtils.getLogger().error(
                            "Failed to spawn fake player marker at chunk ({}, {}): spawn did not register",
                            key.x(), key.z());
                    return;
                }
                applySpawnFacing(fakePlayer, finalEntry);
                applyEasterEggAfterSpawn(key, fakePlayer, false, false);
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
                com.mojang.logging.LogUtils.getLogger().error("Failed to spawn fake player marker: {}", e.getMessage(), e);
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
                com.mojang.logging.LogUtils.getLogger().warn("Error removing marker entity: {}", e.getMessage());
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
                        com.mojang.logging.LogUtils.getLogger().info("Marker destroyed at ({}, {}, {}), deactivating chunkloader",
                                expectedX, expectedY, expectedZ);

                        ChunkloaderFakePlayer fakePlayerToDespawn = activeFakePlayers.remove(key);
                        if (fakePlayerToDespawn != null) {
                            try {
                                fakePlayerToDespawn.despawn();
                                com.mojang.logging.LogUtils.getLogger().info("Despawned fakeplayer marker at ({}, {})",
                                        key.x(), key.z());
                            } catch (Exception e) {
                                com.mojang.logging.LogUtils.getLogger().error("Error despawning fakeplayer marker: {}", e.getMessage(),
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
                        com.mojang.logging.LogUtils.getLogger().info(
                                "Chunkloader at chunk ({}, {}) deactivated and added to disabled list",
                                key.x(), key.z());
                    } else {
                        com.mojang.logging.LogUtils.getLogger().info("Other marker found at ({}, {}, {}), keeping chunkloader active",
                                expectedX, expectedY, expectedZ);
                    }
                }
            }
        }
    }

    public boolean isChunkloaderMarker(UUID markerUuid) {
        return markerToChunkKey.containsKey(markerUuid);
    }

    public void rememberRealPlayerName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        knownRealPlayerNamesLower.add(name.trim().toLowerCase(java.util.Locale.ROOT));
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
            if (entry == null) {
                continue;
            }
            if (entry.blockX() != pos.getX() || entry.blockY() != pos.getY() || entry.blockZ() != pos.getZ()) {
                continue;
            }
            if (dim != null && entry.dimension() != null && !dim.equals(entry.dimension())) {
                continue;
            }

            ChunkKey key = chunkKey(entry);
            markerToChunkKey.put(markerUuid, key);
            markerEntities.put(key, markerUuid);
            return true;
        }

        return false;
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
            com.mojang.logging.LogUtils.getLogger().warn("Marker UUID {} not found in markerToChunkKey, searching by position",
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
                com.mojang.logging.LogUtils.getLogger().error("Could not find chunkloader for marker UUID {}", markerUuid);
                return;
            }
        }
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
        if (entry == null) {
            entry = activeTargets.get(key);
        }
        if (entry == null) {
            com.mojang.logging.LogUtils.getLogger().error("Could not find entry for chunk ({}, {})", key.x(), key.z());
            return;
        }

        if (!entry.enabled()) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot open ChunkMap for disabled chunkloader at chunk ({}, {})", key.x(),
                    key.z());
            return;
        }

        try {
            ChunkMapData data = buildChunkMapData(entry, player);
            ChunkloaderNetworking.sendOpenChunkMap(player, data);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to open chunk map for chunk ({}, {})", entry.chunkX(), entry.chunkZ(),
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

    public boolean confirmMarkerDelete(net.minecraft.server.level.ServerPlayer player, java.util.UUID markerUuid, boolean hit) {
        if (player == null || markerUuid == null) {
            return false;
        }
        ChunkloaderTarget entry = getEntryByMarkerUuid(markerUuid);
        if (entry == null || !de.chunkloader.permissions.PermissionManager.canModifyEntry(player, entry)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You don't own this player."));
            return false;
        }
        return true;
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

        if (!config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), newEnabled)) {
            return false;
        }

        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
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
                    removeChunkloaderTickets(oldWorld, oldChunkPos, oldRadius);
                }
            }

            addChunkloaderTickets(world, chunkPos, radius);
            putActiveTarget(key, updatedEntry);

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
                        removeChunkloaderTickets(world, chunkPos, radius);
                        removeActiveTarget(key);
                        activeFakePlayers.remove(key, fakePlayer);
                        markerEntities.remove(key, fakePlayerUuid);
                        markerToChunkKey.remove(fakePlayerUuid, key);
                        com.mojang.logging.LogUtils.getLogger().error(
                                "Failed to spawn fake player during toggle at chunk ({}, {}): spawn did not register",
                                key.x(), key.z());
                        return newEnabled;
                    }
                    applySpawnFacing(fakePlayer, updatedEntry);
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
                    com.mojang.logging.LogUtils.getLogger().error("Failed to spawn fake player during toggle: {}", e.getMessage(), e);
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
            putActiveTarget(key, updated);
        }
        updateMarkerForChunkloader(key);
        return true;
    }

    public boolean setChunkloaderMobAi(String name, boolean mobAi) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null || entry.allowMobSpawning()) {
            return false;
        }
        config.updateEntryMobAi(entry.chunkX(), entry.chunkZ(), entry.dimension(), mobAi);
        ChunkKey key = chunkKey(entry);
        ChunkloaderTarget updated = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (updated != null && activeTargets.containsKey(key)) {
            putActiveTarget(key, updated);
        }
        return true;
    }

    public boolean toggleChunkloaderMobAi(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null || entry.allowMobSpawning()) {
            return false;
        }
        return setChunkloaderMobAi(entry.name(), !entry.mobAi());
    }

    public boolean setChunkloaderAllowMobSpawning(String name, boolean allowMobSpawning) {
        return setChunkloaderAllowMobSpawning(name, allowMobSpawning, false);
    }

    private boolean setChunkloaderAllowMobSpawning(String name, boolean allowMobSpawning, boolean resetting) {
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return false;
        }

        ChunkKey key = chunkKey(entry);
        boolean modeChanged = entry.allowMobSpawning() != allowMobSpawning;

        String pendingName = null;
        if (modeChanged && !resetting && entry.name() != null) {
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

        config.updateEntryAllowMobSpawning(entry.chunkX(), entry.chunkZ(), entry.dimension(), allowMobSpawning, !resetting);
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
        if (nameChanged) {
            migrateCustomSkinName(entry.name(), updatedEntry.name());
        }
        if (nameChanged && existingFakePlayer != null && existingFakePlayer.isAlive()) {
            respawnMarkerForChunkloader(key, updatedEntry);
            existingFakePlayer = activeFakePlayers.get(key);
        }
        if (modeChanged && existingFakePlayer != null && existingFakePlayer.isAlive() && existingFakePlayer.level() != null) {
            existingFakePlayer.level().getChunkSource().move(existingFakePlayer);
        }

        if (updatedEntry.enabled() && oldActiveEntry != null) {
            ServerLevel world = getWorldByDimension(updatedEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(updatedEntry.chunkX(), updatedEntry.chunkZ());
                int oldRadius = oldActiveEntry.chunkRadius();
                int newRadius = updatedEntry.chunkRadius();
                removeChunkloaderTickets(world, chunkPos, oldRadius);
                addChunkloaderTickets(world, chunkPos, newRadius);
            }
        }

        if (activeTargets.containsKey(key) && updatedEntry.enabled()) {
            putActiveTarget(key, updatedEntry);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                if (modeChanged && !resetting) {
                    applyEasterEggAfterSpawn(key, existingFakePlayer, false, false);
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
                            new BlockPos(updatedEntry.blockX(), updatedEntry.blockY(), updatedEntry.blockZ()));
                }
            }
        } else {
            if (activeTargets.containsKey(key)) {
                putActiveTarget(key, updatedEntry);
            }
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                if (modeChanged && !resetting) {
                    applyEasterEggAfterSpawn(key, existingFakePlayer, false, false);
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

        config.flushPendingSave();
        ChunkloaderConfig newConfig = ChunkloaderConfig.load(server);
        config.copySettingsFrom(newConfig);
        config.replaceAllEntries(newConfig.getChunkEntries());

        loadPersistentChunkloaders();
    }

    public ChunkloaderStats getStats() {
        List<ChunkloaderTarget> entries = config.getChunkEntries();
        Set<ChunkKey> coreChunks = new HashSet<>();
        Set<ChunkKey> chunksWithRings = new HashSet<>();
        int enabled = 0;

        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                continue;
            }
            enabled++;
            int coreRadius = entry.chunkRadius();
            int loadedRadius = coreRadius + 2;
            for (int dx = -loadedRadius; dx <= loadedRadius; dx++) {
                for (int dz = -loadedRadius; dz <= loadedRadius; dz++) {
                    ChunkKey chunk = new ChunkKey(entry.dimension(), entry.chunkX() + dx, entry.chunkZ() + dz);
                    chunksWithRings.add(chunk);
                    if (Math.abs(dx) <= coreRadius && Math.abs(dz) <= coreRadius) {
                        coreChunks.add(chunk);
                    }
                }
            }
        }

        return new ChunkloaderStats(entries.size(), enabled, entries.size() - enabled,
                coreChunks.size(), chunksWithRings.size(), activeFakePlayers.size());
    }

    public boolean setChunkloaderRadius(String name, int radius) {
        return setChunkloaderRadiusWithMessage(name, radius) == null;
    }

    public String setChunkloaderRadiusWithMessage(String name, int radius) {
        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            return "SD must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ".";
        }

        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return "Player '" + name + "' not found.";
        }
        String overlappingName = getRadiusIncreaseConflict(entry, radius);
        if (overlappingName != null) {
            return "Core overlaps with player '" + overlappingName + "'.";
        }
        if (entry.chunkRadius() == radius) {
            return null;
        }

        config.updateEntryChunkRadius(entry.chunkX(), entry.chunkZ(), entry.dimension(), radius);

        ChunkKey key = chunkKey(entry);
        ChunkloaderTarget activeEntry = activeTargets.get(key);
        ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
        if (activeEntry != null && updatedEntry != null) {
            ServerLevel world = getWorldByDimension(activeEntry.dimension());
            if (world != null && activeEntry.chunkRadius() != radius) {
                ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
                removeChunkloaderTickets(world, chunkPos, activeEntry.chunkRadius());
                addChunkloaderTickets(world, chunkPos, radius);
            }
            putActiveTarget(key, updatedEntry);
        }
        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return null;
    }

    public int enableAllChunkloaders() {
        int count = 0;
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());

        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                if (!config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), true)) {
                    continue;
                }
                ChunkKey key = chunkKey(entry);
                ServerLevel world = getWorldByDimension(entry.dimension());
                if (world == null) {
                    com.mojang.logging.LogUtils.getLogger().warn("Cannot enable chunkloader at ({}, {}) in {}: world not loaded",
                            entry.chunkX(), entry.chunkZ(), entry.dimension());
                    count++;
                    continue;
                }
                if (!activeTargets.containsKey(key)) {
                    ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
                    if (updatedEntry != null) {
                        try {
                            activateChunkloader(updatedEntry, world);
                            count++;
                        } catch (Exception e) {
                            deactivateChunkloader(key);
                            config.updateEntryEnabled(entry.chunkX(), entry.chunkZ(), entry.dimension(), false);
                            com.mojang.logging.LogUtils.getLogger().error("Failed to enable chunkloader at chunk ({}, {})",
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
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());

        for (ChunkloaderTarget entry : entries) {
            if (entry.enabled()) {
                ChunkKey key = chunkKey(entry);
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

    public ChunkMapData buildChunkMapData(ChunkloaderTarget entry, ServerPlayer viewer) {
        int mapDisplayRadius = entry.chunkRadius();
        int mapWidth = computeMapSize(mapDisplayRadius);
        int mapHeight = mapWidth;
        int half = (mapWidth - 1) / 2;
        int topLeftChunkX = entry.chunkX() - half;
        int topLeftChunkZ = entry.chunkZ() - half;

        List<ChunkMapCell> cells = new ArrayList<>(mapWidth * mapHeight);
        List<ChunkloaderTarget> entries = config.getChunkEntries();

        int coreRadius = entry.chunkRadius();
        int entityTickRadius = coreRadius;
        int blockTickRadius = coreRadius + 1;
        int loadingRadius = coreRadius + 2;

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
        VisualizationKey visualizationKey = new VisualizationKey(viewer.getUUID(), key);
        boolean canVisualize = PermissionManager.canUse(viewer);
        boolean visualizeActive = canVisualize && visualizationActive.contains(visualizationKey);
        boolean visualize3DActive = canVisualize && visualization3DActive.containsKey(visualizationKey);
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
                entry.allowMobSpawning() && entry.mobTarget(),
                !entry.allowMobSpawning() && entry.mobAi());
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

        return setChunkloaderRadius(entry.name(), newRadius);
    }

    public boolean wouldRadiusIncreaseOverlap(int chunkX, int chunkZ, int newRadius, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        return entry != null && getRadiusIncreaseConflict(entry, newRadius) != null;
    }

    private String getRadiusIncreaseConflict(ChunkloaderTarget entry, int newRadius) {
        if (newRadius <= entry.chunkRadius()) {
            return null;
        }
        return getOverlappingChunkloaderName(entry.chunkX(), entry.chunkZ(), newRadius, entry.dimension(), entry);
    }

    public ChunkMapLoaderDetailsPayload buildMapLoaderDetails(
            ChunkMapData data, ChunkloaderTarget entry, ServerPlayer viewer, boolean canModify) {
        int activeBlockers = 0;
        int disabledBlockers = 0;
        List<DisabledArea> disabledAreas = new ArrayList<>();
        boolean countBlockers = canModify && !data.canIncreaseRadius()
                && entry.chunkRadius() < ChunkloaderConstants.MAX_RADIUS;
        for (ChunkloaderTarget other : config.getChunkEntries()) {
            if (other.equals(entry) || !other.dimension().equals(entry.dimension())) {
                continue;
            }
            if (countBlockers && coresOverlap(entry.chunkX(), entry.chunkZ(), entry.chunkRadius() + 1, other)) {
                if (other.enabled()) {
                    activeBlockers++;
                } else {
                    disabledBlockers++;
                }
            }
            int radius = other.chunkRadius();
            if (!other.enabled() && PermissionManager.canModifyEntry(viewer, other)
                    && other.chunkX() + radius >= data.topLeftChunkX()
                    && other.chunkX() - radius < data.topLeftChunkX() + data.mapWidth()
                    && other.chunkZ() + radius >= data.topLeftChunkZ()
                    && other.chunkZ() - radius < data.topLeftChunkZ() + data.mapHeight()) {
                disabledAreas.add(new DisabledArea(other.chunkX(), other.chunkZ(), radius, formatLoaderLabel(other)));
            }
        }
        return new ChunkMapLoaderDetailsPayload(data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(), data.dimensionKey(), activeBlockers, disabledBlockers, disabledAreas);
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
            putActiveTarget(key, updated);
        }
        updateMarkerForChunkloader(key);
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
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
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
                    com.mojang.logging.LogUtils.getLogger().warn("Failed to rename chunkloader '{}' to '{}'", entry.name(), finalName);
                    continue;
                }

                if (oldName != null && !oldName.isBlank()) {
                    migrateCustomSkinName(oldName, finalName);
                }

                com.mojang.logging.LogUtils.getLogger().info("Renamed chunkloader '{}' to '{}' because player '{}' joined",
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

        String defaultName = config.generateNextName(true, entry, this::isRealPlayerName);
        if (entry.chunkRadius() != 0 && !setChunkloaderRadius(entry.name(), 0)) {
            return false;
        }
        if (!entry.allowMobSpawning() && !setChunkloaderAllowMobSpawning(entry.name(), true, true)) {
            return false;
        }
        if (!entry.nameVisible()) {
            config.updateEntryNameVisible(chunkX, chunkZ, dimension, true);
        }
        if (entry.mobTarget()) {
            config.updateEntryMobTarget(chunkX, chunkZ, dimension, false);
        }
        if (entry.mobAi() != ChunkloaderConstants.DEFAULT_MOB_AI) {
            config.updateEntryMobAi(chunkX, chunkZ, dimension, ChunkloaderConstants.DEFAULT_MOB_AI);
        }

        boolean nameChanged = !defaultName.equals(entry.name());
        if (nameChanged && !config.updateEntryName(chunkX, chunkZ, dimension, defaultName)) {
            return false;
        }
        if (entry.easterEggSkinIndex() != null) {
            config.updateEntryEasterEggSkinIndex(chunkX, chunkZ, dimension, null);
        }

        ChunkKey key = chunkKey(entry);
        ChunkloaderTarget updated = config.getEntry(chunkX, chunkZ, dimension);
        if (updated == null) {
            return false;
        }
        seedEasterEggFromEntry(key, updated);
        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
        if (fakePlayer != null && entry.easterEggSkinIndex() != null) {
            ChunkloaderNetworking.broadcastEasterEggSkin(server, fakePlayer.getUUID(), -1);
        }
        clearCustomSkin(entry.name());

        if (activeTargets.containsKey(key)) {
            putActiveTarget(key, updated);
            if (nameChanged) {
                respawnMarkerForChunkloader(key, updated);
            } else {
                updateMarkerForChunkloader(key);
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
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

    private boolean coresOverlap(int chunkX, int chunkZ, int radius, ChunkloaderTarget other) {
        int combinedRadius = radius + other.chunkRadius();
        return Math.abs((long) other.chunkX() - chunkX) <= combinedRadius
                && Math.abs((long) other.chunkZ() - chunkZ) <= combinedRadius;
    }

    public String getOverlappingChunkloaderName(int chunkX, int chunkZ, int radius, String dimension,
            ChunkloaderTarget excludeEntry) {
        for (ChunkloaderTarget other : config.getChunkEntries()) {
            if (other == null || !other.dimension().equals(dimension) || other.equals(excludeEntry)) {
                continue;
            }
            if (coresOverlap(chunkX, chunkZ, radius, other)) {
                return other.name() != null ? other.name()
                        : String.format("Chunk (%d, %d)", other.chunkX(), other.chunkZ());
            }
        }
        return null;
    }

    private int computeMapSize(int chunkRadius) {
        int desired = Math.max(9, chunkRadius * 2 + 3);
        if ((desired & 1) == 0) {
            desired++;
        }
        return Math.min(33, desired);
    }

    public record ChunkloaderStats(int total, int enabled, int disabled, int coreChunks, int chunksWithRings,
            int activeFakePlayers) {
    }

    public record ChunkloaderPerformanceStats(
            int totalLoadedChunks,
            long usedMemory,
            long maxMemory,
            double memoryUsagePercent,
            int activeFakePlayers) {
    }

    public List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> getDisabledChunkloadersList() {
        return getDisabledChunkloadersList(null);
    }

    public List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> getDisabledChunkloadersList(ServerPlayer viewer) {
        List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> result = new ArrayList<>();
        List<ChunkloaderTarget> entries = config.getChunkEntries();

        for (ChunkloaderTarget entry : entries) {
            if (viewer != null && !PermissionManager.canOperateEntry(viewer, entry)) {
                continue;
            }
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
                        entry.easterEggSkinIndex() != null ? entry.easterEggSkinIndex() : -1,
                        sharedBy(viewer, entry, true)));
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> getActiveChunkloadersList(ServerPlayer viewer) {
        List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> result = new ArrayList<>();
        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (viewer != null && !PermissionManager.canOperateEntry(viewer, entry)) {
                continue;
            }
            if (entry.enabled()) {
                result.add(new de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry(
                        entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ(),
                        entry.name(), entry.allowMobSpawning(), entry.dimension(), false,
                        entry.easterEggSkinIndex() != null ? entry.easterEggSkinIndex() : -1,
                        sharedBy(viewer, entry, true)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String sharedBy(ServerPlayer viewer, ChunkloaderTarget entry, boolean includeShares) {
        if (!includeShares || viewer == null || PermissionManager.canModifyEntry(viewer, entry)) {
            return null;
        }
        return entry.ownerName() != null ? entry.ownerName() : "Server";
    }

    public String getActivationLimitMessage(ChunkloaderTarget entry) {
        return entry == null ? null : config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
    }

    public String getOwnerAssignmentLimitMessage(String name, ServerPlayer owner) {
        return owner == null ? null : config.getOwnerAssignmentLimitMessage(name, owner.getUUID(), owner.getGameProfile().name());
    }

    public boolean assignChunkloaderOwner(String name, ServerPlayer owner) {
        if (owner == null || owner instanceof ChunkloaderFakePlayer) {
            return false;
        }
        if (!config.setEntryOwner(name, owner.getUUID(), owner.getGameProfile().name())) {
            return false;
        }
        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return true;
    }

    public void deleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null && !entry.enabled()) {
            removeChunkloader(chunkX, chunkZ, dimension);
        }
    }

    public boolean restoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.enabled()) return false;
        if (!config.updateEntryEnabled(chunkX, chunkZ, dimension, true)) return false;
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null && !activeTargets.containsKey(key)) {
            ChunkloaderTarget updatedEntry = config.getEntry(chunkX, chunkZ, dimension);
            if (updatedEntry != null) {
                try {
                    activateChunkloader(updatedEntry, world, false, true);
                } catch (Exception e) {
                    deactivateChunkloader(key);
                    config.updateEntryEnabled(chunkX, chunkZ, dimension, false);
                    com.mojang.logging.LogUtils.getLogger().error("Failed to restore chunkloader at chunk ({}, {})", chunkX, chunkZ, e);
                    return false;
                }
            }
        }
        ChunkloaderNetworking.invalidateChunkCache();
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
        return true;
    }

    public boolean updateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ,
            int newBlockX, int newBlockY, int newBlockZ) {
        String errorMessage = updateDisabledChunkloaderCoordsWithMessage(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ);
        return errorMessage == null;
    }

    public String updateDisabledChunkloaderCoordsWithMessage(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ,
            int newBlockX, int newBlockY, int newBlockZ) {
        return updateDisabledChunkloaderCoordsWithMessage(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ, oldDimension);
    }

    public String updateDisabledChunkloaderCoordsWithMessage(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ,
            int newBlockX, int newBlockY, int newBlockZ, String newDimension) {
        ChunkloaderTarget entry = config.getEntry(oldChunkX, oldChunkZ, oldDimension);
        if (entry == null) {
            return "Entry not found at the specified position.";
        }

        String targetDimension = newDimension == null || newDimension.isBlank() ? entry.dimension() : newDimension;
        var targetWorld = getWorldByDimension(targetDimension);
        if (targetWorld == null) {
            return "That dimension is not loaded.";
        }
        if (!targetWorld.isInWorldBounds(new BlockPos(newBlockX, newBlockY, newBlockZ))
                || newBlockY < ChunkloaderConstants.MIN_BLOCK_Y || newBlockY > ChunkloaderConstants.MAX_BLOCK_Y) {
            return "Position is outside that dimension.";
        }

        if ((newBlockX >> 4) != newChunkX || (newBlockZ >> 4) != newChunkZ) {
            return "Block coordinates must be inside the target chunk.";
        }

        boolean sameCore = targetDimension.equals(entry.dimension()) && oldChunkX == newChunkX && oldChunkZ == newChunkZ;
        if (sameCore && entry.blockX() == newBlockX && entry.blockY() == newBlockY && entry.blockZ() == newBlockZ) {
            return "Cannot update coordinates: Coordinates are identical to the current position.";
        }
        if (!sameCore) {
            String overlappingName = getOverlappingChunkloaderName(
                    newChunkX, newChunkZ, entry.chunkRadius(), targetDimension, entry);
            if (overlappingName != null) {
                return "Core overlaps with player '" + overlappingName + "'.";
            }
        }

        boolean wasEnabled = entry.enabled();
        if (wasEnabled) {
            deactivateChunkloader(new ChunkKey(entry.dimension(), oldChunkX, oldChunkZ));
        }

        boolean success = config.moveEntry(oldChunkX, oldChunkZ, entry.dimension(),
                newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ, targetDimension);

        if (!success) {
            if (wasEnabled) {
                reactivateBeforeMove(entry);
            }
            return "Failed to update coordinates: The position is invalid.";
        }

        if (wasEnabled) {
            ChunkloaderTarget moved = config.getEntry(newChunkX, newChunkZ, targetDimension);
            boolean reactivated = false;
            if (moved != null) {
                try {
                    activateChunkloader(moved, targetWorld, false, true);
                    reactivated = activeFakePlayers.containsKey(chunkKey(moved));
                } catch (Exception e) {
                    com.mojang.logging.LogUtils.getLogger().error("Failed to reactivate moved chunkloader", e);
                }
            }
            if (!reactivated) {
                deactivateChunkloader(new ChunkKey(targetDimension, newChunkX, newChunkZ));
                if (config.moveEntry(newChunkX, newChunkZ, targetDimension, oldChunkX, oldChunkZ,
                        entry.blockX(), entry.blockY(), entry.blockZ(), entry.dimension())) {
                    reactivateBeforeMove(entry);
                }
                return "Failed to enable the player at the new position.";
            }
        }

        return null;
    }

    private void reactivateBeforeMove(ChunkloaderTarget entry) {
        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world == null) {
            return;
        }
        try {
            activateChunkloader(entry, world, false, false);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to restore chunkloader after a rejected move", e);
        }
    }

}
