package de.chunkloader.network;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.payload.*;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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

    private static final Map<net.minecraft.server.level.ServerLevel, String> dimensionCache = new ConcurrentHashMap<>();

    private static String getDimensionString(net.minecraft.server.level.ServerLevel world) {
        if (world == null) {
            return "unknown";
        }
        return dimensionCache.computeIfAbsent(world, w -> w.dimension().identifier().toString());
    }

    private static String createChunkKey(int chunkX, int chunkZ, String dimension) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private static void trackOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player == null || data == null) {
            return;
        }
        if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            return;
        }
        openChunkMaps.put(player.getUUID(), createChunkKey(data.centerChunkX(), data.centerChunkZ(), data.dimensionKey()));
    }

    private static void clearOpenChunkMap(ServerPlayer player) {
        if (player == null) {
            return;
        }
        openChunkMaps.remove(player.getUUID());
    }

    @SuppressWarnings("all")
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EasterEggSkinPayload.TYPE, EasterEggSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EasterEggEmotePayload.TYPE, EasterEggEmotePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChunkloaderActionPayload.TYPE, ChunkloaderActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SimulationStatusRequestPayload.TYPE, SimulationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChunkplayerStatusRequestPayload.TYPE, ChunkplayerStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DisabledChunkloadersListRequestPayload.TYPE, DisabledChunkloadersListRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeleteDisabledChunkloaderPayload.TYPE, DeleteDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RestoreDisabledChunkloaderPayload.TYPE, RestoreDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateDisabledChunkloaderCoordsPayload.TYPE, UpdateDisabledChunkloaderCoordsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, UpdateDisabledChunkloaderCoordsResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenameChunkloaderPayload.TYPE, RenameChunkloaderPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InvalidateCachePayload.TYPE, InvalidateCachePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCustomSkinPayload.TYPE, SyncCustomSkinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ApplyCustomSkinPayload.TYPE, ApplyCustomSkinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ChunkloaderActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleClientAction(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(SimulationStatusRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleSimulationStatusRequest(player, payload.forceResponse()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ChunkplayerStatusRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleChunkplayerStatusRequest(player, payload.forceResponse()));
        });

        ServerPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleDisabledChunkloadersListRequest(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteDisabledChunkloaderPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleDeleteDisabledChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RestoreDisabledChunkloaderPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleRestoreDisabledChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDisabledChunkloaderCoordsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleUpdateDisabledChunkloaderCoords(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(RenameChunkloaderPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleRenameChunkloader(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CloseChunkMapPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> clearOpenChunkMap(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(ApplyCustomSkinPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleApplyCustomSkin(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(ClearCustomSkinPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleClearCustomSkin(player, payload));
        });
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        ServerPlayNetworking.send(player, new OpenChunkMapPayload(data));
        trackOpenChunkMap(player, data);
    }

    public static void sendCloseChunkMap(ServerPlayer player) {
        ServerPlayNetworking.send(player, new CloseChunkMapPayload());
        clearOpenChunkMap(player);
    }

    public static void closeOpenChunkMapsFor(
        net.minecraft.server.MinecraftServer server,
        int chunkX,
        int chunkZ,
        String dimension
    ) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        String dimensionKey = dimension != null && !dimension.isBlank() ? dimension : "minecraft:overworld";
        String targetKey = createChunkKey(chunkX, chunkZ, dimensionKey);
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.connection == null) {
                continue;
            }
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = openChunkMaps.get(player.getUUID());
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
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
                ServerPlayNetworking.send(player, payload);
                clearOpenChunkMap(player);
            }
        }
    }

    public static void broadcastFakePlayerVisibility(net.minecraft.server.MinecraftServer server, String fakePlayerName, boolean visible) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        FakePlayerVisibilityPayload payload = new FakePlayerVisibilityPayload(fakePlayerName, visible);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
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
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        ClearCustomSkinPayload payload = new ClearCustomSkinPayload(playerName);
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sendSyncCustomSkin(
            ServerPlayer player,
            String playerName,
            int layerMask,
            String model,
            byte[] pngBytes) {
        if (player == null || player.connection == null || playerName == null || playerName.isBlank()) {
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
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
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

    private static void handleApplyCustomSkin(ServerPlayer player, ApplyCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to change skins.")
                .withStyle(ChatFormatting.RED));
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
            player.sendSystemMessage(Component.literal("Unknown player.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
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
            player.sendSystemMessage(Component.literal("Failed to apply custom skin.").withStyle(ChatFormatting.RED));
        }
    }

    private static void handleClearCustomSkin(ServerPlayer player, ClearCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to change skins.")
                .withStyle(ChatFormatting.RED));
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
                broadcastClearCustomSkin(player.level().getServer(), payload.playerName());
            }
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager != null) {
            manager.clearCustomSkin(entry.name() != null ? entry.name() : payload.playerName());
        }
    }

    public static void sendFakePlayerVisibility(ServerPlayer player, String fakePlayerName, boolean visible) {
        if (player == null || player.connection == null) {
            return;
        }
        ServerPlayNetworking.send(player, new FakePlayerVisibilityPayload(fakePlayerName, visible));
    }

    public static void sendEasterEggSkin(ServerPlayer player, UUID playerUuid, int skinIndex) {
        if (player == null || player.connection == null || playerUuid == null) {
            return;
        }
        ServerPlayNetworking.send(player, new EasterEggSkinPayload(playerUuid, skinIndex));
    }

    public static void broadcastEasterEggSkin(net.minecraft.server.MinecraftServer server, UUID playerUuid, int skinIndex) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        EasterEggSkinPayload payload = new EasterEggSkinPayload(playerUuid, skinIndex);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sendEasterEggEmote(ServerPlayer player, UUID playerUuid, long startGameTime) {
        if (player == null || player.connection == null || playerUuid == null) {
            return;
        }
        ServerPlayNetworking.send(player, new EasterEggEmotePayload(playerUuid, startGameTime));
    }

    public static void broadcastEasterEggEmote(net.minecraft.server.MinecraftServer server, UUID playerUuid, long startGameTime) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        EasterEggEmotePayload payload = new EasterEggEmotePayload(playerUuid, startGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.connection != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static boolean isOnActionCooldown(ServerPlayer player) {
        if (player == null) {
            return true;
        }
        Long until = actionCooldownUntilMs.get(player.getUUID());
        return until != null && System.currentTimeMillis() < until;
    }

    private static void markActionCooldown(ServerPlayer player) {
        if (player != null) {
            actionCooldownUntilMs.put(player.getUUID(), System.currentTimeMillis() + ACTION_COOLDOWN_MS);
        }
    }

    private static boolean canModifyEntry(ServerPlayer player, de.chunkloader.config.ChunkloaderTarget entry) {
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

    private static boolean hasMatchingOpenChunkMap(ServerPlayer player, int chunkX, int chunkZ, String dimension) {
        if (player == null) {
            return false;
        }
        String openKey = openChunkMaps.get(player.getUUID());
        if (openKey == null) {
            return false;
        }
        return openKey.equals(createChunkKey(chunkX, chunkZ, dimension));
    }

    @Environment(EnvType.CLIENT)
    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        ClientPlayNetworking.send(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value));
    }

    private static void handleClientAction(ServerPlayer player, ChunkloaderActionPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to use player actions."));
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            player.sendSystemMessage(Component.literal("No open chunk map session for this player.").withStyle(ChatFormatting.RED));
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
                    player.sendSystemMessage(Component.literal("Player disabled (Press " + keyName + " to open disabled list)"), false);

                    return;
                }
            }
            case TOGGLE_MOB_SPAWNING -> {
                boolean success = manager.toggleChunkloaderMobSpawning(payload.chunkX(), payload.chunkZ(), dimension);
                if (!success) {
                    player.sendSystemMessage(Component.literal("Toggle failed: rename the player first to avoid a name conflict.").withStyle(ChatFormatting.RED));
                    sendCloseChunkMap(player);
                    return;
                }
            }
            case RADIUS_INCREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, Math.max(1, payload.value()));
            case RADIUS_DECREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_MOB_TARGET -> manager.toggleChunkloaderMobTarget(payload.chunkX(), payload.chunkZ(), dimension);
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
            refreshOpenChunkMapMarkers(player.level().getServer(), manager);
        }
    }

    private static void handleSimulationStatusRequest(ServerPlayer player, boolean forceResponse) {
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

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = getDimensionString(world);
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
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

        UUID playerId = player.getUUID();
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

    private static void handleChunkplayerStatusRequest(ServerPlayer player, boolean forceResponse) {
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

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = getDimensionString(world);
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
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

        UUID playerId = player.getUUID();
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

    public static void clearPlayerCache(ServerPlayer player) {
        if (player != null) {
            UUID playerId = player.getUUID();
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = openChunkMaps.get(player.getUUID());
            if (key.equals(openKey)) {
                ServerPlayNetworking.send(player, new OpenChunkMapPayload(data));
                trackOpenChunkMap(player, data);
            }
        }
    }

    public static void refreshOpenChunkMapMarkers(net.minecraft.server.MinecraftServer server, de.chunkloader.manager.ChunkloaderManager manager) {
        if (server == null || manager == null || server.getPlayerList() == null) {
            return;
        }

        if (server.isSameThread()) {
            refreshOpenChunkMapMarkersNow(server, manager);
        } else {
            server.execute(() -> refreshOpenChunkMapMarkersNow(server, manager));
        }
    }

    private static void refreshOpenChunkMapMarkersNow(net.minecraft.server.MinecraftServer server, de.chunkloader.manager.ChunkloaderManager manager) {
        if (server == null || manager == null || server.getPlayerList() == null || openChunkMaps.isEmpty()) {
            return;
        }
        java.util.List<java.util.Map.Entry<UUID, String>> open = new java.util.ArrayList<>(openChunkMaps.entrySet());
        for (java.util.Map.Entry<UUID, String> tracked : open) {
            ServerPlayer player = server.getPlayerList().getPlayer(tracked.getKey());
            if (player == null || player.connection == null) {
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
        if (server != null && server.getPlayerList() != null) {
            InvalidateCachePayload payload = new InvalidateCachePayload();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null && player.connection != null) {
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

    private static void handleRenameChunkloader(ServerPlayer player, RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to rename players."));
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
                broadcastOpenChunkMapUpdate(player.level().getServer(), manager, updated);
                refreshOpenChunkMapMarkers(player.level().getServer(), manager);
            }
        } else {
            String errorMessage = "This name is already in use or invalid.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
        }
    }

    private static void handleDisabledChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view disabled players."));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var disabledList = manager.getDisabledChunkloadersList();
        ServerPlayNetworking.send(player, new DisabledChunkloadersListPayload(disabledList));
    }

    private static void handleDeleteDisabledChunkloader(ServerPlayer player, DeleteDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to delete disabled players."));
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        manager.deleteDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
    }

    private static void handleRestoreDisabledChunkloader(ServerPlayer player, RestoreDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to restore disabled players."));
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
    }

    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayer player, UpdateDisabledChunkloaderCoordsPayload payload) {
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

