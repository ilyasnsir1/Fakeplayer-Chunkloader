package de.chunkloader.manager;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.ChunkloaderConstants;
import org.slf4j.Logger;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.config.CustomFakePlayerSkinStore;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.ChatFormatting;

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
import java.util.concurrent.ThreadLocalRandom;

public class ChunkloaderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private final ConcurrentMap<UUID, Integer> pendingPlayerJoinSyncs = new ConcurrentHashMap<>();
    private final Set<UUID> syncingFakePlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastToggleTime = new ConcurrentHashMap<>();
    private static final long TOGGLE_COOLDOWN_MS = 200;
    private static final int PENDING_ACTIVATION_INITIAL_DELAY_TICKS = 0;
    private static final int PENDING_ACTIVATION_RETRY_TICKS = 20;

    private static final int FAKEPLAYER_EXTRA_BLOCK_TICK_RINGS = Integer
            .getInteger("chunkloader.fakeplayerExtraBlockTickRings", 1);
    private static final int FAKEPLAYER_EXTRA_LOADING_RINGS = Integer
            .getInteger("chunkloader.fakeplayerExtraLoadingRings", 2);
    private static final int FAKEPLAYER_MOB_ENTITY_TICK_RADIUS = Integer
            .getInteger("chunkloader.fakeplayerMobEntityTickRadius", 5);
    private static final int FAKEPLAYER_MOB_SPAWN_CHUNK_RADIUS = Integer
            .getInteger("chunkloader.fakeplayerMobSpawnChunkRadius", 8);
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED_MOB_SPAWN_RADIUS_WARNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final int CHUNKPLAYER_EXTRA_BLOCK_TICK_RINGS = Integer
            .getInteger("chunkloader.chunkplayerExtraBlockTickRings", 0);
    private static final int CHUNKPLAYER_EXTRA_LOADING_RINGS = Integer
            .getInteger("chunkloader.chunkplayerExtraLoadingRings", 2);

    private static final long DISABLE_TICK_CONTROL_GRACE_MS = Long.getLong("chunkloader.disableTickControlGraceMs", 0L);
    private final ConcurrentMap<String, Long> tickControlUntilByDimension = new ConcurrentHashMap<>();

    private volatile boolean tabListVisibleAll = true;
    private final Set<String> knownRealPlayerNamesLower = ConcurrentHashMap.newKeySet();
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

    final ConcurrentMap<ServerLevel, String> dimensionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerLevel> dimensionToWorldCache = new ConcurrentHashMap<>();

    public record Visualization3DConfig(int minY, int maxY) {
        public Visualization3DConfig() {
            this(ChunkloaderConstants.MIN_BLOCK_Y, ChunkloaderConstants.MAX_BLOCK_Y);
        }
    }

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

    private static int fakeplayerEntityTickRadius(ChunkloaderTarget entry) {
        if (entry == null) {
            return 0;
        }
        int selected = Math.max(0, entry.chunkRadius());
        if (!entry.allowMobSpawning()) {
            return selected;
        }
        int mob = FAKEPLAYER_MOB_ENTITY_TICK_RADIUS;
        if (mob < 0) {
            return selected;
        }
        int spawn = FAKEPLAYER_MOB_SPAWN_CHUNK_RADIUS;
        if (spawn < 0) {
            spawn = selected;
        }
        return Math.max(selected, Math.max(mob, spawn));
    }

    public static int getEffectiveFakeplayerEntityTickRadius(ChunkloaderTarget entry) {
        return fakeplayerEntityTickRadius(entry);
    }

    public static int getEffectiveFakeplayerSpawnChunkRadius(ChunkloaderTarget entry) {
        if (entry == null || !entry.allowMobSpawning()) {
            return 0;
        }
        int selected = Math.max(0, entry.chunkRadius());
        int spawn = FAKEPLAYER_MOB_SPAWN_CHUNK_RADIUS;
        if (spawn < 0) {
            return selected;
        }
        int effective = Math.max(selected, spawn);
        if (effective > selected && LOGGED_MOB_SPAWN_RADIUS_WARNING.compareAndSet(false, true)) {
            LOGGER.warn(
                    "Fakeplayer with allowMobSpawning uses effective spawn/ticket radius {} (UI radius {}, system min {}). "
                            + "Extra loading rings still apply via chunkloader.fakeplayerExtraLoadingRings (default 2).",
                    effective, selected, spawn);
        }
        return effective;
    }

    public boolean isTabListHidden(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!(player instanceof ChunkloaderFakePlayer)) {
            return false;
        }
        return !tabListVisibleAll;
    }

    public boolean isTabListVisibleAll() {
        return tabListVisibleAll;
    }

    public int setTabListVisibleAll(boolean visible) {
        this.tabListVisibleAll = visible;

        if (config != null) {
            try {
                config.setTabListVisibleAll(visible);
                config.save();
            } catch (Exception ignored) {
            }
        }

        int changed = 0;
        for (ChunkloaderFakePlayer fakePlayer : activeFakePlayers.values()) {
            if (fakePlayer == null || !fakePlayer.isAlive()) {
                continue;
            }
            broadcastTabListVisibilityUpdate(fakePlayer, visible);
            changed++;
        }
        return changed;
    }

    private void broadcastTabListVisibilityUpdate(ServerPlayer target, boolean visible) {
        if (server == null || target == null) {
            return;
        }
        try {
            if (!visible) {
                net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket removePacket = new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                        java.util.List.of(target.getUUID()));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p != null && p.connection != null) {
                        p.connection.send(removePacket);
                    }
                }
                return;
            }

            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket addPacket = new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                    java.util.EnumSet.of(
                            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                    java.util.List.of(target));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != null && p.connection != null) {
                    p.connection.send(addPacket);
                }
            }
        } catch (Throwable t) {
            try {
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket packet = new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                        java.util.EnumSet.of(
                                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                        java.util.List.of(target));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p != null && p.connection != null) {
                        p.connection.send(packet);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void extendTickControlGrace(String dimension) {
        if (dimension == null) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(0L, DISABLE_TICK_CONTROL_GRACE_MS);
        tickControlUntilByDimension.put(dimension, until);
    }

    public boolean shouldControlTicksInDimension(String dimension) {
        if (hasAnyActiveLoaderInDimension(dimension)) {
            return true;
        }
        Long until = tickControlUntilByDimension.get(dimension);
        if (until == null) {
            return false;
        }
        if (until > System.currentTimeMillis()) {
            return true;
        }
        tickControlUntilByDimension.remove(dimension, until);
        return false;
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
        int effectiveEntityRadius = entry.allowMobSpawning() ? fakeplayerEntityTickRadius(entry) : coreRadius;
        int loadingRadius = entry.allowMobSpawning() ? fakeplayerLoadingRadius(coreRadius)
                : chunkplayerLoadingRadius(coreRadius);
        int ticketRadius = Math.max(loadingRadius, effectiveEntityRadius);

        world.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunkPos, ticketRadius);
    }

    private static void removeAllChunkloaderTickets(ServerLevel world, ChunkPos chunkPos, ChunkloaderTarget entry,
            int radius) {
        if (world == null || chunkPos == null || entry == null) {
            return;
        }

        int coreRadius = Math.max(0, radius);
        int effectiveEntityRadius = entry.allowMobSpawning() ? fakeplayerEntityTickRadius(entry) : coreRadius;
        int loadingRadius = entry.allowMobSpawning() ? fakeplayerLoadingRadius(coreRadius)
                : chunkplayerLoadingRadius(coreRadius);
        int ticketRadius = Math.max(loadingRadius, effectiveEntityRadius);

        world.getChunkSource().removeTicketWithRadius(TicketType.PORTAL, chunkPos, ticketRadius);
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
            this.tabListVisibleAll = config.isTabListVisibleAll();
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

        }
        return null;
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
        if (server == null) {
            return null;
        }
        try {
            return de.chunkloader.config.ChunkloaderPaths.getChunkloaderDir(server).resolve("chunkloader_config.json");
        } catch (Exception e) {
            return null;
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
        if (world == null)
            return;

        ChunkKey key = chunkKey(entry);
        if (!visualizationActive.contains(key))
            return;

        ChunkBorderRenderer.renderChunkBorders2D(world, entry, entry.blockY());
    }

    private void renderChunkBorders3D(ServerLevel world, ChunkloaderTarget entry) {
        if (world == null)
            return;

        ChunkKey key = chunkKey(entry);
        Visualization3DConfig config = visualization3DActive.get(key);
        if (config == null)
            return;

        ChunkBorderRenderer.renderChunkBorders3D(world, entry, config.minY(), config.maxY(), tickCounter);
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
        java.util.Iterator<Map.Entry<UUID, Integer>> it = pendingPlayerJoinSyncs.entrySet().iterator();
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

    public void tick() {
        processPendingChunkloaderActivations();
        processPendingPlayerJoinSyncs();

        for (ChunkloaderTarget entry : config.getChunkEntries()) {
            if (!entry.enabled())
                continue;
            ChunkKey key = chunkKey(entry);
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

        if (tickCounter % 10 == 0) {
            for (ChunkKey key : visualizationActive) {
                ChunkloaderTarget entry = activeTargets.get(key);
                if (entry == null) {
                    entry = config.getEntry(key.x(), key.z(), key.dimension());
                }
                if (entry != null) {
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
            configuredKeys.add(chunkKey(entry));
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
        this.tabListVisibleAll = newConfig.isTabListVisibleAll();

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

        boolean scanWorlds = Boolean.getBoolean("chunkloader.cleanupScanWorlds");
        if (server != null && scanWorlds) {
            for (ServerLevel world : server.getAllLevels()) {
                try {
                    net.minecraft.world.phys.AABB worldBox = new net.minecraft.world.phys.AABB(
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
                    List<Entity> fakePlayersToRemove = new ArrayList<>();
                    for (Entity entity : world.getEntities().getAll()) {
                        if (!(entity instanceof ServerPlayer))
                            continue;
                        if (!worldBox.contains(entity.getX(), entity.getY(), entity.getZ()))
                            continue;
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
                                    player.connection.disconnect(Component.literal("cleanup"));
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

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world,
            String ownerName) {
        return addChunkloader(chunkX, chunkZ, blockPos, name, world, ownerName, 0.0f);
    }

    public boolean addChunkloader(int chunkX, int chunkZ, BlockPos blockPos, String name, ServerLevel world,
            String ownerName, float spawnYaw) {
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

        ChunkloaderTarget existingEntry = config.getEntry(chunkX, chunkZ, dimension);
        if (existingEntry != null && existingEntry.dimension().equals(dimension)) {

            return false;
        }

        int defaultRadius = 0;
        if (isPositionCoveredByOtherChunkloader(chunkX, chunkZ, defaultRadius, dimension, null)) {

            return false;
        }
        float normalizedSpawnYaw = normalizeSpawnYaw(spawnYaw);
        boolean success = config.addOrUpdateEntry(chunkX, chunkZ, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                name, dimension, null, null, ownerName, normalizedSpawnYaw);
        if (!success) {
            return false;
        }

        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null) {
            try {
                activateChunkloader(entry, world, true);
                ChunkloaderNetworking.invalidateChunkCache();
                ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, this);
                return true;
            } catch (Exception e) {

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

            String plainName = displayName;
            de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName, nameVisible);

            updateFakePlayerTeam(existingFakePlayer, entry);

            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldRadius);
                }
            }
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            addChunkloaderTickets(world, chunkPos, entry, radius);
            activeTargets.put(key, entry);

            updateMarkerForChunkloader(key);

            applyEasterEggAfterSpawn(existingFakePlayer, key, allowRandomEasterEggAssign, allowRandomEasterEggAssign);

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
                removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldEntry.chunkRadius());
            }
        }

        ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
        int radius = entry.chunkRadius();

        try {
            addChunkloaderTickets(world, chunkPos, entry, radius);
            activeTargets.put(key, entry);

            ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                    server,
                    world,
                    createProfile(entry));
            fakePlayer.setPos(entry.blockX() + 0.5, entry.blockY(), entry.blockZ() + 0.5);
            fakePlayer.setYRot(normalizeSpawnYaw(entry.spawnYaw()));
            fakePlayer.setXRot(0.0F);

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
                    LOGGER.error(
                            "Failed to activate chunkloader at chunk ({}, {}): fake player spawn did not register",
                            key.x(), key.z());
                    return;
                }
                ChunkloaderNetworking.broadcastEasterEggEmote(server, fakePlayer.getUUID(),
                        fakePlayer.level().getGameTime());
                noteEasterEggEmoteStart(fakePlayer.getUUID(), fakePlayer.level().getGameTime());
                broadcastTabListVisibilityUpdate(fakePlayer, tabListVisibleAll);

                server.execute(() -> {
                    server.execute(() -> {
                        if (finalFakePlayer.isAlive() && finalFakePlayer.level() == finalWorld) {
                            ChunkloaderTarget updatedEntry = activeTargets.get(key);
                            if (updatedEntry == null) {
                                updatedEntry = entry;
                            }
                            net.minecraft.ChatFormatting updatedColor = determineFakePlayerColor(updatedEntry, key);
                            finalFakePlayer.setCustomName(Component.literal(displayName).withStyle(updatedColor));
                            finalFakePlayer.setPlayerListName(
                                    buildTabListName(displayName, updatedColor, updatedEntry.dimension()));
                            finalFakePlayer.setCustomNameVisible(updatedEntry.nameVisible());
                            finalFakePlayer.setVisibleAsMarker(true);

                            forceEntitySync(finalFakePlayer);

                            updateFakePlayerTeam(finalFakePlayer, updatedEntry);
                        }
                    });
                });

                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
                fakePlayer.setCustomNameVisible(entry.nameVisible());
                fakePlayer.setVisibleAsMarker(true);
                fakePlayer.setInvisible(false);

                forceEntitySync(fakePlayer);
            } catch (Exception e) {
                removeAllChunkloaderTickets(world, chunkPos, entry, radius);
                activeTargets.remove(key);
                activeFakePlayers.remove(key, fakePlayer);
                UUID fakePlayerUuid = fakePlayer.getUUID();
                markerEntities.remove(key, fakePlayerUuid);
                markerToChunkKey.remove(fakePlayerUuid, key);
                throw new RuntimeException("Failed to spawn fake player", e);
            }

            applyEasterEggAfterSpawn(fakePlayer, key, allowRandomEasterEggAssign, allowRandomEasterEggAssign);

            ChunkloaderTarget updatedEntry = activeTargets.get(key);
            if (updatedEntry != null) {
                updateFakePlayerTeam(fakePlayer, updatedEntry);
            }

            BlockPos blockPos = new BlockPos(entry.blockX(), entry.blockY(), entry.blockZ());
            if (playEffects) {
                playSpawnEffects(world, blockPos, key, activeTargets.getOrDefault(key, entry), true);
            }
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

        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.remove(key);
        UUID fakePlayerUuid = fakePlayer != null ? fakePlayer.getUUID() : null;

        Integer removedEasterEgg = easterEggSkinByKey.remove(key);
        if (fakePlayerUuid != null) {
            easterEggEmoteStartByUuid.remove(fakePlayerUuid);
        }

        extendTickControlGrace(entry.dimension());

        ServerLevel world = getWorldByDimension(entry.dimension());
        if (world != null) {
            ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());
            int radius = entry.chunkRadius();
            removeAllChunkloaderTickets(world, chunkPos, entry, radius);
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
        return isChunkplayerBlockTickChunk(chunkX, chunkZ, dimension);
    }

    public boolean isFakeplayerRandomTickChunk(int chunkX, int chunkZ, String dimension) {
        return isFakeplayerBlockTickChunk(chunkX, chunkZ, dimension);
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

    public boolean isFakeplayerEntityTickChunk(int chunkX, int chunkZ, String dimension) {
        for (Map.Entry<ChunkKey, ChunkloaderTarget> entry : activeTargets.entrySet()) {
            ChunkloaderTarget target = entry.getValue();
            if (!target.enabled() || !target.allowMobSpawning() || !target.dimension().equals(dimension)) {
                continue;
            }
            ChunkKey key = entry.getKey();
            int r = getEffectiveFakeplayerSpawnChunkRadius(target);
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
            int r = fakeplayerBlockTickRadius(target.chunkRadius());
            int dx = Math.abs(key.x() - chunkX);
            int dz = Math.abs(key.z() - chunkZ);
            if (dx <= r && dz <= r) {
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

    private void applyEasterEggAfterSpawn(ChunkloaderFakePlayer fakePlayer, ChunkKey key, boolean allowRandomAssign,
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
            ChunkloaderFakePlayer fp = e.getValue();
            if (key == null || fp == null || !fp.isAlive()) {
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
            LOGGER.warn("Failed to apply custom skin for '{}': {}", playerName, e.getMessage());
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
        fakePlayer.setPlayerListName(buildTabListName(displayName, color, entry.dimension()));
        de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, displayName, nameVisible);
        updateFakePlayerTeam(fakePlayer, entry);
        applySpawnFacing(fakePlayer, entry);
    }

    private void updateFakePlayerTeam(ChunkloaderFakePlayer fakePlayer, ChunkloaderTarget entry) {
        if (server == null || server.getScoreboard() == null) {
            return;
        }

        try {
            net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
            net.minecraft.ChatFormatting teamColor = determineFakePlayerColor(entry,
                    chunkKey(entry));

            String teamName = "chunkloader_" + teamColor.getName().toLowerCase(java.util.Locale.ROOT);

            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                if (team != null) {
                    team.setColor(teamColor);
                    team.setDisplayName(Component.literal(
                            (entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer") + " " + teamColor.getName())
                            .withStyle(teamColor));
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

    private static final int MAX_PROFILE_NAME_LENGTH = 16;

    private static String sanitizeProfileName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "Player";
        }

        StringBuilder out = new StringBuilder(rawName.length());
        for (int i = 0; i < rawName.length() && out.length() < MAX_PROFILE_NAME_LENGTH; i++) {
            char c = rawName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }

        if (out.isEmpty()) {
            return "Player";
        }
        return out.toString();
    }

    private GameProfile createProfile(ChunkloaderTarget entry) {
        String prefix = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        String name = entry.name() != null ? entry.name() : (prefix + entry.chunkX() + "_" + entry.chunkZ());
        String data = "chunkloader:" + entry.dimension() + ":" + entry.chunkX() + ":" + entry.chunkZ();
        UUID uuid = UUID.nameUUIDFromBytes(data.getBytes(StandardCharsets.UTF_8));
        String profileName = sanitizeProfileName(name);
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

        if (!entry.enabled()) {
            return;
        }

        seedEasterEggFromEntry(key, entry);

        final ChunkloaderTarget finalEntry = entry;
        final ServerLevel finalWorld = world;

        ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);

        if (fakePlayer != null) {
            applyFakePlayerMetadata(fakePlayer, finalEntry, key);
            applyEasterEggAfterSpawn(fakePlayer, key, allowRandomAssign, false);

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
            fakePlayer.setYRot(normalizeSpawnYaw(entry.spawnYaw()));
            fakePlayer.setXRot(0.0F);

            applyFakePlayerMetadata(fakePlayer, finalEntry, key);

            try {
                activeFakePlayers.put(key, fakePlayer);
                boolean spawned = fakePlayer.spawn();
                if (!spawned || !fakePlayer.isRegistered()) {
                    activeFakePlayers.remove(key, fakePlayer);
                    LOGGER.error(
                            "Failed to spawn fake player marker at chunk ({}, {}): spawn did not register",
                            key.x(), key.z());
                    return;
                }
                broadcastTabListVisibilityUpdate(fakePlayer, tabListVisibleAll);
                applyEasterEggAfterSpawn(fakePlayer, key, allowRandomAssign, false);
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
                activeFakePlayers.remove(key, fakePlayer);
                LOGGER.error("Failed to spawn fake player marker: {}", e.getMessage(), e);
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
            ServerLevel serverWorld = fakePlayer.level() instanceof ServerLevel ? (ServerLevel) fakePlayer.level()
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
        seedEasterEggFromEntry(key, entry);

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

                        config.updateEntryEnabled(key.x(), key.z(), key.dimension(), false);
                        cancelPendingChunkloader(key);
                        ChunkloaderNetworking.closeOpenChunkMapsFor(server, key.x(), key.z(), key.dimension());
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
            if (entry == null)
                continue;
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

                return;
            }
        }
        ChunkloaderTarget entry = config.getEntry(key.x(), key.z(), key.dimension());
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
            int radius = updatedEntry.chunkRadius();

            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.get(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    int oldRadius = oldEntry.chunkRadius();
                    removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldRadius);
                }
            }

            addChunkloaderTickets(world, chunkPos, entry, radius);
            activeTargets.put(key, updatedEntry);

            ChunkloaderFakePlayer existingFakePlayer = activeFakePlayers.get(key);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                updateMarkerForChunkloader(key);
            } else {
                ChunkloaderFakePlayer fakePlayer = new ChunkloaderFakePlayer(
                        server,
                        world,
                        createProfile(updatedEntry));
                fakePlayer.setPos(updatedEntry.blockX() + 0.5, updatedEntry.blockY(), updatedEntry.blockZ() + 0.5);
                fakePlayer.setYRot(normalizeSpawnYaw(updatedEntry.spawnYaw()));
                fakePlayer.setXRot(0.0F);

                String prefix = updatedEntry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
                String displayName = updatedEntry.name() != null ? updatedEntry.name()
                        : (prefix + key.x() + "_" + key.z());
                net.minecraft.ChatFormatting color = determineFakePlayerColor(updatedEntry, key);
                Component nameText = Component.literal(displayName).withStyle(color);
                fakePlayer.setCustomName(nameText);
                fakePlayer.setPlayerListName(buildTabListName(displayName, color, updatedEntry.dimension()));
                fakePlayer.setCustomNameVisible(updatedEntry.nameVisible());
                fakePlayer.setVisibleAsMarker(true);

                String plainName = displayName;
                de.chunkloader.network.ChunkloaderNetworking.broadcastFakePlayerVisibility(server, plainName,
                        updatedEntry.nameVisible());

                updateFakePlayerTeam(fakePlayer, updatedEntry);

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
                        LOGGER.error(
                                "Failed to spawn fake player during toggle at chunk ({}, {}): spawn did not register",
                                key.x(), key.z());
                        return newEnabled;
                    }
                    broadcastTabListVisibilityUpdate(fakePlayer, tabListVisibleAll);
                } catch (Exception e) {
                    activeFakePlayers.remove(key, fakePlayer);
                    UUID fakePlayerUuid = fakePlayer.getUUID();
                    markerEntities.remove(key, fakePlayerUuid);
                    markerToChunkKey.remove(fakePlayerUuid, key);
                    LOGGER.error("Failed to spawn fake player during toggle: {}", e.getMessage(), e);
                }
            }
            playSpawnEffects(world, pos, key, updatedEntry, true);
        } else {
            if (activeTargets.containsKey(key)) {
                ChunkloaderTarget oldEntry = activeTargets.remove(key);
                ServerLevel oldWorld = getWorldByDimension(oldEntry.dimension());
                if (oldWorld != null) {
                    ChunkPos oldChunkPos = new ChunkPos(oldEntry.chunkX(), oldEntry.chunkZ());
                    removeAllChunkloaderTickets(oldWorld, oldChunkPos, oldEntry, oldEntry.chunkRadius());
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
                removeAllChunkloaderTickets(world, chunkPos, oldActiveEntry, oldActiveEntry.chunkRadius());
                addChunkloaderTickets(world, chunkPos, updatedEntry, updatedEntry.chunkRadius());
            }
        }

        if (activeTargets.containsKey(key) && updatedEntry.enabled()) {
            activeTargets.put(key, updatedEntry);
            if (existingFakePlayer != null && existingFakePlayer.isAlive()) {
                if (modeChanged) {
                    applyEasterEggAfterSpawn(existingFakePlayer, key, true, true);
                    ChunkloaderNetworking.broadcastEasterEggEmote(server, existingFakePlayer.getUUID(),
                            existingFakePlayer.level().getGameTime());
                    noteEasterEggEmoteStart(existingFakePlayer.getUUID(), existingFakePlayer.level().getGameTime());
                } else {
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
                    applyEasterEggAfterSpawn(existingFakePlayer, key, true, true);
                    ChunkloaderNetworking.broadcastEasterEggEmote(server, existingFakePlayer.getUUID(),
                            existingFakePlayer.level().getGameTime());
                    noteEasterEggEmoteStart(existingFakePlayer.getUUID(), existingFakePlayer.level().getGameTime());
                } else {
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

        config.updateEntryChunkRadius(entry.chunkX(), entry.chunkZ(), entry.dimension(), radius);

        ChunkKey key = chunkKey(entry);

        if (activeTargets.containsKey(key)) {
            ChunkloaderTarget activeEntry = activeTargets.get(key);
            ServerLevel world = getWorldByDimension(activeEntry.dimension());
            if (world != null) {
                ChunkPos chunkPos = new ChunkPos(entry.chunkX(), entry.chunkZ());

                ChunkloaderTarget updated = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
                if (updated != null) {
                    removeAllChunkloaderTickets(world, chunkPos, activeEntry, activeEntry.chunkRadius());
                    addChunkloaderTickets(world, chunkPos, updated, updated.chunkRadius());
                }

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
                    LOGGER.warn("Cannot enable chunkloader at ({}, {}) in {}: world not loaded",
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
        int mapWidth = ChunkMapRenderer.computeMapSize(mapDisplayRadius);
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
                }

                String occupyingLabel = getOccupyingLoaderLabel(entries, entry, chunkX, chunkZ);
                if (occupyingLabel != null && !simulated) {
                    occupied = true;
                    simulatingFakeplayerName = occupyingLabel;
                }

                cells.add(new ChunkMapCell(offsetX, offsetZ, loaded, withinRange, occupied, simulated,
                        simulatingFakeplayerName));
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                int[] pixels = serverLevel != null
                        ? ChunkMapRenderer.generateChunkTilePixels(serverLevel, chunkPos, entry.blockY())
                        : ChunkMapRenderer.solidTile(ChunkMapRenderer.DEFAULT_TILE_COLOR_ABGR);
                tiles.add(new ChunkMapTile(chunkX, chunkZ, pixels));
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
                entry.spawnYaw(),
                entry.name(),
                nameVisible,
                visualizeActive,
                visualize3DActive,
                canIncreaseRadius,
                otherChunkloaders,
                entry.ownerName(),
                isEasterEgg(chunkKey(entry)) || entry.easterEggSkinIndex() != null);
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

    public boolean renameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        newName = newName.trim();

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

    public void rememberRealPlayerName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        knownRealPlayerNamesLower.add(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isRealPlayerName(String name) {
        if (name == null || name.isBlank() || server == null) {
            return false;
        }
        String n = name.trim();
        if (knownRealPlayerNamesLower.contains(n.toLowerCase(java.util.Locale.ROOT))) {
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof ChunkloaderFakePlayer) {
                continue;
            }
            if (n.equalsIgnoreCase(p.getName().getString())) {
                return true;
            }
        }
        return false;
    }

    public void checkAndRenameConflictingChunkloaders(String playerName) {
        if (playerName == null || playerName.isBlank() || server == null || config == null) {
            return;
        }

        String realName = playerName.trim();
        List<ChunkloaderTarget> entries = new ArrayList<>(config.getChunkEntries());
        for (ChunkloaderTarget entry : entries) {
            if (entry == null || entry.name() == null) {
                continue;
            }
            if (!realName.equalsIgnoreCase(entry.name())) {
                continue;
            }

            String unique = generateNextDefaultName(entry.allowMobSpawning());
            if (unique == null || unique.isBlank()) {
                continue;
            }

            String oldName = entry.name();
            boolean renamed = config.updateEntryName(entry.chunkX(), entry.chunkZ(), entry.dimension(), unique);
            if (!renamed) {
                continue;
            }

            if (oldName != null && !oldName.isBlank()) {
                migrateCustomSkinName(oldName, unique);
            }

            ChunkKey key = chunkKey(entry);
            ChunkloaderTarget updatedEntry = config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension());
            if (updatedEntry != null && activeTargets.containsKey(key)) {
                ChunkloaderFakePlayer fakePlayer = activeFakePlayers.get(key);
                if (fakePlayer != null) {
                    applyFakePlayerMetadata(fakePlayer, updatedEntry, key);
                }
            }
        }
    }

    private String generateNextDefaultName(boolean isFakeplayer) {
        final String prefix = isFakeplayer ? "Fakeplayer" : "Chunkplayer";
        Set<Integer> used = new HashSet<>();
        for (ChunkloaderTarget e : config.getChunkEntries()) {
            if (e == null || e.name() == null) {
                continue;
            }
            String n = e.name();
            if (n.length() < prefix.length()) {
                continue;
            }
            if (!n.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String numStr = n.substring(prefix.length());
            if (!numStr.matches("^\\d+$")) {
                continue;
            }
            try {
                used.add(Integer.parseInt(numStr));
            } catch (NumberFormatException ignored) {
            }
        }
        for (int i = 1; i < 10000; i++) {
            if (used.contains(i)) {
                continue;
            }
            String candidate = prefix + i;
            if (isRealPlayerName(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    public boolean resetChunkloaderToDefaults(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry == null || entry.name() == null) {
            return false;
        }

        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);

        config.updateEntryNameVisible(chunkX, chunkZ, dimension, true);
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

    public record ChunkloaderStats(int total, int enabled, int disabled, int loadedChunks, int activeFakePlayers) {
    }

    public record ChunkloaderPerformanceStats(
            int totalLoadedChunks,
            long usedMemory,
            long maxMemory,
            double memoryUsagePercent,
            int activeFakePlayers) {
    }

    public List<DisabledChunkloaderEntry> getDisabledChunkloadersList() {
        List<DisabledChunkloaderEntry> result = new ArrayList<>();
        List<ChunkloaderTarget> entries = config.getChunkEntries();

        for (ChunkloaderTarget entry : entries) {
            if (!entry.enabled()) {
                Integer easterEggSkinIndex = entry.easterEggSkinIndex();
                int skinIndex = easterEggSkinIndex != null ? easterEggSkinIndex : -1;
                result.add(new DisabledChunkloaderEntry(
                        entry.chunkX(),
                        entry.chunkZ(),
                        entry.blockX(),
                        entry.blockY(),
                        entry.blockZ(),
                        entry.name(),
                        entry.allowMobSpawning(),
                        entry.dimension(),
                        false,
                        skinIndex));
            }
        }

        return Collections.unmodifiableList(result);
    }

    public record DisabledChunkloaderEntry(
            int chunkX, int chunkZ,
            int blockX, int blockY, int blockZ,
            String name, boolean allowMobSpawning, String dimension, boolean isFakeplayer,
            int easterEggSkinIndex) {
    }

    public void deleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderTarget entry = config.getEntry(chunkX, chunkZ, dimension);
        if (entry != null && !entry.enabled()) {
            removeChunkloader(chunkX, chunkZ, dimension);
        }
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
                java.util.Objects.equals(oldDimension, entry.dimension()) &&
                entry.blockX() == newBlockX && entry.blockY() == newBlockY && entry.blockZ() == newBlockZ) {

            return "Cannot update coordinates: Coordinates are identical to the current position.";
        }

        if (isPositionCoveredByOtherChunkloader(newChunkX, newChunkZ, 0, entry.dimension(), entry)) {

            return "Cannot update coordinates: Position is already covered by another enabled player.";
        }

        String targetDimension = entry.dimension();
        ChunkloaderTarget existingAtNewPos = config.getEntry(newChunkX, newChunkZ, targetDimension);
        if (existingAtNewPos != null && existingAtNewPos != entry
                && existingAtNewPos.dimension().equals(entry.dimension())) {

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

            return "Failed to update coordinates: The name may already exist or the position is invalid.";
        }

        if (oldChunkX != newChunkX || oldChunkZ != newChunkZ) {
            config.removeEntry(oldChunkX, oldChunkZ, oldDimension);
        }

        return null;
    }

}
