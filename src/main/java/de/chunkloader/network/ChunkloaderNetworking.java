package de.chunkloader.network;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.payload.*;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("all")
public final class ChunkloaderNetworking {

    private ChunkloaderNetworking() {
    }

    private static volatile Consumer<String> clearCustomSkinClientHook;

    private static volatile java.util.function.Consumer<SyncCustomSkinPayload> syncCustomSkinClientHook;

    private static final Map<UUID, SimulationStatusResponsePayload> lastSimulationStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkplayerStatusResponsePayload> lastChunkplayerStatus = new ConcurrentHashMap<>();

    private static final Map<String, SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> openChunkMaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> actionCooldownUntilMs = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 250L;

    private static final Map<net.minecraft.server.world.ServerWorld, String> dimensionCache = new ConcurrentHashMap<>();

    private static String getDimensionString(net.minecraft.server.world.ServerWorld world) {
        if (world == null) {
            return "unknown";
        }
        return dimensionCache.computeIfAbsent(world, w -> w.getRegistryKey().getValue().toString());
    }

    private static String createChunkKey(int chunkX, int chunkZ, String dimension) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private static void trackOpenChunkMap(ServerPlayerEntity player, ChunkMapData data) {
        if (player == null || data == null) {
            return;
        }
        if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            return;
        }
        openChunkMaps.put(player.getUuid(), createChunkKey(data.centerChunkX(), data.centerChunkZ(), data.dimensionKey()));
    }

    private static void clearOpenChunkMap(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        openChunkMaps.remove(player.getUuid());
    }

    @SuppressWarnings("all")
    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenChunkMapPayload.ID, OpenChunkMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseChunkMapPayload.ID, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FakePlayerVisibilityPayload.ID, FakePlayerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EasterEggSkinPayload.ID, EasterEggSkinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EasterEggEmotePayload.ID, EasterEggEmotePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SimulationStatusResponsePayload.ID, SimulationStatusResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChunkplayerStatusResponsePayload.ID, ChunkplayerStatusResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DisabledChunkloadersListPayload.ID, DisabledChunkloadersListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkloaderActionPayload.ID, ChunkloaderActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SimulationStatusRequestPayload.ID, SimulationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkplayerStatusRequestPayload.ID, ChunkplayerStatusRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CloseChunkMapPayload.ID, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DisabledChunkloadersListRequestPayload.ID, DisabledChunkloadersListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteDisabledChunkloaderPayload.ID, DeleteDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RestoreDisabledChunkloaderPayload.ID, RestoreDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDisabledChunkloaderCoordsPayload.ID, UpdateDisabledChunkloaderCoordsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateDisabledChunkloaderCoordsResponsePayload.ID, UpdateDisabledChunkloaderCoordsResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RenameChunkloaderResponsePayload.ID, RenameChunkloaderResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RenameChunkloaderPayload.ID, RenameChunkloaderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InvalidateCachePayload.ID, InvalidateCachePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClearCustomSkinPayload.ID, ClearCustomSkinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncCustomSkinPayload.ID, SyncCustomSkinPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ApplyCustomSkinPayload.ID, ApplyCustomSkinPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearCustomSkinPayload.ID, ClearCustomSkinPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ChunkloaderActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleClientAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(SimulationStatusRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleSimulationStatusRequest(player, payload.forceResponse()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ChunkplayerStatusRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleChunkplayerStatusRequest(player, payload.forceResponse()));
        });

        ServerPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleDisabledChunkloadersListRequest(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteDisabledChunkloaderPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleDeleteDisabledChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RestoreDisabledChunkloaderPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleRestoreDisabledChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDisabledChunkloaderCoordsPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleUpdateDisabledChunkloaderCoords(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RenameChunkloaderPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleRenameChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CloseChunkMapPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> clearOpenChunkMap(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(ApplyCustomSkinPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleApplyCustomSkin(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(ClearCustomSkinPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> handleClearCustomSkin(player, payload));
        });
    }

    public static void sendOpenChunkMap(ServerPlayerEntity player, ChunkMapData data) {
        ServerPlayNetworking.send(player, new OpenChunkMapPayload(data));
        trackOpenChunkMap(player, data);
    }

    public static void sendCloseChunkMap(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new CloseChunkMapPayload());
        clearOpenChunkMap(player);
    }

    public static void closeOpenChunkMapsFor(
        net.minecraft.server.MinecraftServer server,
        int chunkX,
        int chunkZ,
        String dimension
    ) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        String dimensionKey = dimension != null && !dimension.isBlank() ? dimension : "minecraft:overworld";
        String targetKey = createChunkKey(chunkX, chunkZ, dimensionKey);
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null || player.networkHandler == null) {
                continue;
            }
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = openChunkMaps.get(player.getUuid());
            if (targetKey.equals(openKey)) {
                ServerPlayNetworking.send(player, payload);
                clearOpenChunkMap(player);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void sendCloseChunkMapToServer() {
        ClientPlayNetworking.send(new CloseChunkMapPayload());
    }

    public static void broadcastCloseChunkMap(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
                clearOpenChunkMap(player);
            }
        }
    }

    public static void broadcastFakePlayerVisibility(net.minecraft.server.MinecraftServer server, String fakePlayerName, boolean visible) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        FakePlayerVisibilityPayload payload = new FakePlayerVisibilityPayload(fakePlayerName, visible);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void setClearCustomSkinClientHook(Consumer<String> hook) {
        clearCustomSkinClientHook = hook;
    }

    public static void setSyncCustomSkinClientHook(java.util.function.Consumer<SyncCustomSkinPayload> hook) {
        syncCustomSkinClientHook = hook;
    }

    public static void broadcastClearCustomSkin(net.minecraft.server.MinecraftServer server, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }

        Consumer<String> clientHook = clearCustomSkinClientHook;
        if (clientHook != null) {
            clientHook.accept(playerName);
        }
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        ClearCustomSkinPayload payload = new ClearCustomSkinPayload(playerName);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sendSyncCustomSkin(
            ServerPlayerEntity player,
            String playerName,
            int layerMask,
            String model,
            byte[] pngBytes) {
        if (player == null || player.networkHandler == null || playerName == null || playerName.isBlank()) {
            return;
        }
        if (pngBytes == null || pngBytes.length == 0) {
            return;
        }
        ServerPlayNetworking.send(player, new SyncCustomSkinPayload(playerName, layerMask, model, pngBytes));
    }

    public static void broadcastSyncCustomSkin(
            net.minecraft.server.MinecraftServer server,
            String playerName,
            int layerMask,
            String model,
            byte[] pngBytes) {
        if (playerName == null || playerName.isBlank() || pngBytes == null || pngBytes.length == 0) {
            return;
        }
        SyncCustomSkinPayload payload = new SyncCustomSkinPayload(playerName, layerMask, model, pngBytes);
        java.util.function.Consumer<SyncCustomSkinPayload> clientHook = syncCustomSkinClientHook;
        if (clientHook != null) {
            clientHook.accept(payload);
        }
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
        ClientPlayNetworking.send(new ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes));
    }

    @Environment(EnvType.CLIENT)
    public static void sendClearCustomSkin(String playerName) {
        ClientPlayNetworking.send(new ClearCustomSkinPayload(playerName));
    }

    private static void handleApplyCustomSkin(ServerPlayerEntity player, ApplyCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to change skins.")
                .formatted(Formatting.RED), false);
            return;
        }
        if (payload == null || payload.playerName() == null || payload.playerName().isBlank()) {
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        var entry = config.getEntryByName(payload.playerName());
        if (entry == null) {
            player.sendMessage(Text.literal("Unknown player.").formatted(Formatting.RED), false);
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendMessage(Text.literal("You don't own this player.").formatted(Formatting.RED), false);
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        boolean ok = manager.applyCustomSkin(
            entry.name() != null ? entry.name() : payload.playerName(),
            payload.pngBytes(),
            payload.layerMask(),
            payload.model()
        );
        if (!ok) {
            player.sendMessage(Text.literal("Failed to apply custom skin.").formatted(Formatting.RED), false);
        }
    }

    private static void handleClearCustomSkin(ServerPlayerEntity player, ClearCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to change skins.")
                .formatted(Formatting.RED), false);
            return;
        }
        if (payload == null || payload.playerName() == null || payload.playerName().isBlank()) {
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        var entry = config.getEntryByName(payload.playerName());
        if (entry == null) {
            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager != null) {
                manager.clearCustomSkin(payload.playerName());
            } else {
                broadcastClearCustomSkin(player.getEntityWorld().getServer(), payload.playerName());
            }
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendMessage(Text.literal("You don't own this player.").formatted(Formatting.RED), false);
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            manager.clearCustomSkin(entry.name() != null ? entry.name() : payload.playerName());
        }
    }

    public static void sendFakePlayerVisibility(ServerPlayerEntity player, String fakePlayerName, boolean visible) {
        if (player == null || player.networkHandler == null) {
            return;
        }
        ServerPlayNetworking.send(player, new FakePlayerVisibilityPayload(fakePlayerName, visible));
    }

    public static void sendEasterEggSkin(ServerPlayerEntity player, UUID playerUuid, int skinIndex) {
        if (player == null || player.networkHandler == null || playerUuid == null) {
            return;
        }
        ServerPlayNetworking.send(player, new EasterEggSkinPayload(playerUuid, skinIndex));
    }

    public static void broadcastEasterEggSkin(net.minecraft.server.MinecraftServer server, UUID playerUuid, int skinIndex) {
        if (server == null || server.getPlayerManager() == null || playerUuid == null) {
            return;
        }
        EasterEggSkinPayload payload = new EasterEggSkinPayload(playerUuid, skinIndex);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sendEasterEggEmote(ServerPlayerEntity player, UUID playerUuid, long startGameTime) {
        if (player == null || player.networkHandler == null || playerUuid == null) {
            return;
        }
        ServerPlayNetworking.send(player, new EasterEggEmotePayload(playerUuid, startGameTime));
    }

    public static void broadcastEasterEggEmote(net.minecraft.server.MinecraftServer server, UUID playerUuid, long startGameTime) {
        if (server == null || server.getPlayerManager() == null || playerUuid == null) {
            return;
        }
        EasterEggEmotePayload payload = new EasterEggEmotePayload(playerUuid, startGameTime);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static boolean isOnActionCooldown(ServerPlayerEntity player) {
        if (player == null) {
            return true;
        }
        Long until = actionCooldownUntilMs.get(player.getUuid());
        return until != null && System.currentTimeMillis() < until;
    }

    private static void markActionCooldown(ServerPlayerEntity player) {
        if (player != null) {
            actionCooldownUntilMs.put(player.getUuid(), System.currentTimeMillis() + ACTION_COOLDOWN_MS);
        }
    }

    private static boolean canModifyEntry(ServerPlayerEntity player, de.chunkloader.config.ChunkloaderTarget entry) {
        if (player == null || entry == null) {
            return false;
        }
        if (PermissionManager.isAdmin(player)) {
            return true;
        }
        String ownerName = entry.ownerName();
        if (ownerName == null || ownerName.isBlank()) {
            return true;
        }
        String playerName = player.getGameProfile().name();
        return playerName != null && playerName.equalsIgnoreCase(ownerName);
    }

    private static boolean hasMatchingOpenChunkMap(ServerPlayerEntity player, int chunkX, int chunkZ, String dimension) {
        if (player == null) {
            return false;
        }
        String openKey = openChunkMaps.get(player.getUuid());
        if (openKey == null) {
            return false;
        }
        return openKey.equals(createChunkKey(chunkX, chunkZ, dimension));
    }

    @Environment(EnvType.CLIENT)
    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        ClientPlayNetworking.send(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value));
    }

    private static void handleClientAction(ServerPlayerEntity player, ChunkloaderActionPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to use player actions."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendMessage(Text.literal("You don't own this player.").formatted(Formatting.RED), false);
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            player.sendMessage(Text.literal("No open chunk map session for this player.").formatted(Formatting.RED), false);
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        switch (payload.action()) {
            case TOGGLE_ENABLED -> {
                manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ(), dimension);

                var entryAfter = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
                if (entryAfter != null && !entryAfter.enabled()) {
                    String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
                    player.sendMessage(Text.literal("Player disabled (Press " + keyName + " to open disabled list)"), false);

                    return;
                }
            }
            case TOGGLE_MOB_SPAWNING -> {
                boolean success = manager.toggleChunkloaderMobSpawning(payload.chunkX(), payload.chunkZ(), dimension);
                if (!success) {
                    player.sendMessage(Text.literal("Toggle failed: rename the player first to avoid a name conflict.").formatted(Formatting.RED), false);
                    sendCloseChunkMap(player);
                    return;
                }
            }
            case RADIUS_INCREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, Math.max(1, payload.value()));
            case RADIUS_DECREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_VISUALIZE -> manager.toggleChunkloaderVisualize(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_VISUALIZE3D -> manager.toggleChunkloaderVisualize3D(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_HIDE_OTHER_DOTS -> {  }
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ(), dimension);
            case DELETE -> {
                manager.removeChunkloader(payload.chunkX(), payload.chunkZ(), dimension);

                return;
            }
        }

        entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry != null && entry.enabled()) {
            sendOpenChunkMap(player, manager.buildChunkMapData(entry));
            refreshOpenChunkMapMarkers(player.getEntityWorld().getServer(), manager);
        }
    }

    private static void handleSimulationStatusRequest(ServerPlayerEntity player, boolean forceResponse) {
        if (forceResponse) {
            if (isOnActionCooldown(player)) {
                return;
            }
            markActionCooldown(player);
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        String dimension = getDimensionString(world);
        int playerChunkX = player.getChunkPos().x;
        int playerChunkZ = player.getChunkPos().z;
        String chunkKey = createChunkKey(playerChunkX, playerChunkZ, dimension);

        SimulationStatusResponsePayload cachedResponse = chunkSimulationCache.get(chunkKey);

        SimulationStatusResponsePayload response;
        if (cachedResponse != null) {
            response = cachedResponse;
        } else {
            var status = manager.getSimulationStatus(player);
            response = new SimulationStatusResponsePayload(
                status.inSimulatedChunk(),
                status.fakeplayerName(),
                status.chunkX(),
                status.chunkZ(),
                status.simulationDistance(),
                status.distance()
            );

            chunkSimulationCache.put(chunkKey, response);
        }

        UUID playerId = player.getUuid();
        SimulationStatusResponsePayload lastStatus = lastSimulationStatus.get(playerId);

        if (forceResponse || lastStatus == null || !statusEquals(lastStatus, response)) {
            lastSimulationStatus.put(playerId, response);
            ServerPlayNetworking.send(player, response);
        }
    }

    private static boolean statusEquals(SimulationStatusResponsePayload a, SimulationStatusResponsePayload b) {
        if (a.inSimulatedChunk() != b.inSimulatedChunk()) {
            return false;
        }
        if (!java.util.Objects.equals(a.fakeplayerName(), b.fakeplayerName())) {
            return false;
        }
        if (a.chunkX() != b.chunkX() || a.chunkZ() != b.chunkZ()) {
            return false;
        }
        if (a.simulationDistance() != b.simulationDistance()) {
            return false;
        }
        if (a.distance() != b.distance()) {
            return false;
        }
        return true;
    }

    private static void handleChunkplayerStatusRequest(ServerPlayerEntity player, boolean forceResponse) {
        if (forceResponse) {
            if (isOnActionCooldown(player)) {
                return;
            }
            markActionCooldown(player);
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        String dimension = getDimensionString(world);
        int playerChunkX = player.getChunkPos().x;
        int playerChunkZ = player.getChunkPos().z;
        String chunkKey = createChunkKey(playerChunkX, playerChunkZ, dimension);

        ChunkplayerStatusResponsePayload cachedResponse = chunkChunkplayerCache.get(chunkKey);

        ChunkplayerStatusResponsePayload response;
        if (cachedResponse != null) {
            response = cachedResponse;
        } else {
            var status = manager.getChunkplayerStatus(player);
            response = new ChunkplayerStatusResponsePayload(
                status.inLoadedChunk(),
                status.chunkplayerName(),
                status.chunkX(),
                status.chunkZ(),
                status.radius(),
                status.distance()
            );

            chunkChunkplayerCache.put(chunkKey, response);
        }

        UUID playerId = player.getUuid();
        ChunkplayerStatusResponsePayload lastStatus = lastChunkplayerStatus.get(playerId);

        if (forceResponse || lastStatus == null || !chunkplayerStatusEquals(lastStatus, response)) {
            lastChunkplayerStatus.put(playerId, response);
            ServerPlayNetworking.send(player, response);
        }
    }

    private static boolean chunkplayerStatusEquals(ChunkplayerStatusResponsePayload a, ChunkplayerStatusResponsePayload b) {
        if (a.inLoadedChunk() != b.inLoadedChunk()) {
            return false;
        }
        if (!java.util.Objects.equals(a.chunkplayerName(), b.chunkplayerName())) {
            return false;
        }
        if (a.chunkX() != b.chunkX() || a.chunkZ() != b.chunkZ()) {
            return false;
        }
        if (a.radius() != b.radius()) {
            return false;
        }
        if (a.distance() != b.distance()) {
            return false;
        }
        return true;
    }

    public static void clearPlayerCache(ServerPlayerEntity player) {
        if (player != null) {
            UUID playerId = player.getUuid();
            lastSimulationStatus.remove(playerId);
            lastChunkplayerStatus.remove(playerId);
            openChunkMaps.remove(playerId);
            actionCooldownUntilMs.remove(playerId);
        }
    }

    public static void broadcastOpenChunkMapUpdate(net.minecraft.server.MinecraftServer server, de.chunkloader.manager.ChunkloaderManager manager, de.chunkloader.config.ChunkloaderTarget entry) {
        if (server == null || manager == null || entry == null) {
            return;
        }
        String key = createChunkKey(entry.chunkX(), entry.chunkZ(), entry.dimension());
        ChunkMapData data;
        try {
            data = manager.buildChunkMapData(entry);
        } catch (Exception e) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = openChunkMaps.get(player.getUuid());
            if (key.equals(openKey)) {
                ServerPlayNetworking.send(player, new OpenChunkMapPayload(data));
                trackOpenChunkMap(player, data);
            }
        }
    }

    public static void refreshOpenChunkMapMarkers(net.minecraft.server.MinecraftServer server, de.chunkloader.manager.ChunkloaderManager manager) {
        if (server == null || manager == null || server.getPlayerManager() == null) {
            return;
        }

        if (server.isOnThread()) {
            refreshOpenChunkMapMarkersNow(server, manager);
        } else {
            server.execute(() -> refreshOpenChunkMapMarkersNow(server, manager));
        }
    }

    private static void refreshOpenChunkMapMarkersNow(net.minecraft.server.MinecraftServer server, de.chunkloader.manager.ChunkloaderManager manager) {
        if (server == null || manager == null || server.getPlayerManager() == null || openChunkMaps.isEmpty()) {
            return;
        }
        java.util.List<java.util.Map.Entry<UUID, String>> open = new java.util.ArrayList<>(openChunkMaps.entrySet());
        for (java.util.Map.Entry<UUID, String> tracked : open) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(tracked.getKey());
            if (player == null || player.networkHandler == null) {
                openChunkMaps.remove(tracked.getKey());
                continue;
            }
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = tracked.getValue();
            if (openKey == null || openKey.isBlank()) {
                continue;
            }
            de.chunkloader.config.ChunkloaderTarget entry = resolveOpenChunkMapEntry(openKey);
            if (entry == null || !entry.enabled()) {
                continue;
            }
            try {
                sendOpenChunkMap(player, manager.buildChunkMapData(entry));
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.warn("Failed to refresh open chunk map markers for {}: {}", player.getName().getString(), e.toString());
            }
        }
    }

    private static de.chunkloader.config.ChunkloaderTarget resolveOpenChunkMapEntry(String openKey) {
        int sep = openKey.lastIndexOf(':');
        if (sep <= 0 || sep >= openKey.length() - 1) {
            return null;
        }
        String dimension = openKey.substring(0, sep);
        String coords = openKey.substring(sep + 1);
        int comma = coords.indexOf(',');
        if (comma <= 0 || comma >= coords.length() - 1) {
            return null;
        }
        try {
            int chunkX = Integer.parseInt(coords.substring(0, comma));
            int chunkZ = Integer.parseInt(coords.substring(comma + 1));
            var config = ChunkloaderMod.getConfig();
            return config != null ? config.getEntry(chunkX, chunkZ, dimension) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void invalidateChunkCache() {
        chunkSimulationCache.clear();
        chunkChunkplayerCache.clear();
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            net.minecraft.server.MinecraftServer server = manager.getServer();
            if (server != null) {
                broadcastInvalidateCache(server);
            }
        }
    }

    public static void broadcastInvalidateCache(net.minecraft.server.MinecraftServer server) {
        if (server != null && server.getPlayerManager() != null) {
            InvalidateCachePayload payload = new InvalidateCachePayload();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                    continue;
                }
                if (player != null && player.networkHandler != null) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void requestSimulationStatus() {
        ClientPlayNetworking.send(new SimulationStatusRequestPayload(false));
    }

    @Environment(EnvType.CLIENT)
    public static void requestSimulationStatusForScreen() {
        ClientPlayNetworking.send(new SimulationStatusRequestPayload(true));
    }

    @Environment(EnvType.CLIENT)
    public static void requestChunkplayerStatus() {
        ClientPlayNetworking.send(new ChunkplayerStatusRequestPayload(false));
    }

    @Environment(EnvType.CLIENT)
    public static void requestChunkplayerStatusForScreen() {
        ClientPlayNetworking.send(new ChunkplayerStatusRequestPayload(true));
    }

    @Environment(EnvType.CLIENT)
    public static void requestDisabledChunkloadersList() {
        ClientPlayNetworking.send(new DisabledChunkloadersListRequestPayload());
    }

    @Environment(EnvType.CLIENT)
    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ClientPlayNetworking.send(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    @Environment(EnvType.CLIENT)
    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ClientPlayNetworking.send(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    @Environment(EnvType.CLIENT)
    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ClientPlayNetworking.send(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ));
    }

    @Environment(EnvType.CLIENT)
    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        ClientPlayNetworking.send(new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName));
    }

    private static void handleRenameChunkloader(ServerPlayerEntity player, RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to rename players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "Player not found."));
            return;
        }
        if (!canModifyEntry(player, entry)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "You don't own this player."));
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "No open chunk map session for this player."));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        String newName = payload.newName();
        if (newName == null) {
            String errorMessage = "Name must be 1-16 characters and can only contain letters and numbers.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
            return;
        }

        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 16 || !newName.matches("^[a-zA-Z0-9]+$")) {
            String errorMessage = "Name must be 1-16 characters and can only contain letters and numbers.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
            return;
        }

        boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), dimension, newName);
        if (success) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(true, null));
            var updated = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
            if (updated != null) {
                broadcastOpenChunkMapUpdate(player.getEntityWorld().getServer(), manager, updated);
                refreshOpenChunkMapMarkers(player.getEntityWorld().getServer(), manager);
            }
        } else {
            String errorMessage = "This name is already in use or invalid.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
        }
    }

    private static void handleDisabledChunkloadersListRequest(ServerPlayerEntity player) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to view disabled players."), false);
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var disabledList = manager.getDisabledChunkloadersList();
        ServerPlayNetworking.send(player, new DisabledChunkloadersListPayload(disabledList));
    }

    private static void handleDeleteDisabledChunkloader(ServerPlayerEntity player, DeleteDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to delete disabled players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null || entry.enabled()) {
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendMessage(Text.literal("You don't own this player.").formatted(Formatting.RED), false);
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        manager.deleteDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
    }

    private static void handleRestoreDisabledChunkloader(ServerPlayerEntity player, RestoreDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to restore disabled players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null || entry.enabled()) {
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendMessage(Text.literal("You don't own this player.").formatted(Formatting.RED), false);
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
    }

    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayerEntity player, UpdateDisabledChunkloaderCoordsPayload payload) {
        try {
            if (!PermissionManager.canUse(player)) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't have permission to update disabled player coordinates."));
                return;
            }
            if (isOnActionCooldown(player)) {
                return;
            }
            markActionCooldown(player);

            var config = ChunkloaderMod.getConfig();
            if (config == null) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Config not available."));
                return;
            }
            String oldDimension = payload.oldDimension() != null && !payload.oldDimension().isBlank()
                    ? payload.oldDimension() : "minecraft:overworld";
            var entry = config.getEntry(payload.oldChunkX(), payload.oldChunkZ(), oldDimension);
            if (entry == null) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player not found."));
                return;
            }
            if (!canModifyEntry(player, entry)) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't own this player."));
                return;
            }

            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager == null) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player manager not available."));
                return;
            }

            String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
                payload.oldChunkX(), payload.oldChunkZ(), oldDimension,
                payload.newChunkX(), payload.newChunkZ(),
                payload.newBlockX(), payload.newBlockY(), payload.newBlockZ()
            );

            if (errorMessage == null) {
                handleDisabledChunkloadersListRequest(player);
                UpdateDisabledChunkloaderCoordsResponsePayload response = new UpdateDisabledChunkloaderCoordsResponsePayload(true, "Coordinates updated successfully.");
                ServerPlayNetworking.send(player, response);
                ChunkloaderMod.LOGGER.info("Sent success response for update disabled chunkloader coordinates to player {}", player.getName().getString());
            } else {
                UpdateDisabledChunkloaderCoordsResponsePayload response = new UpdateDisabledChunkloaderCoordsResponsePayload(false, errorMessage);
                ServerPlayNetworking.send(player, response);
                ChunkloaderMod.LOGGER.info("Sent error response for update disabled chunkloader coordinates to player {}: {}", player.getName().getString(), errorMessage);
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error updating disabled chunkloader coordinates", e);
            ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
