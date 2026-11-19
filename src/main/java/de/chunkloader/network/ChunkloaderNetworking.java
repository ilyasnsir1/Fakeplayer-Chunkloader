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

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("all")
public final class ChunkloaderNetworking {

    private ChunkloaderNetworking() {
    }

    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> skinChangeResultClientHook = payload -> {};

    public static void setSkinChangeResultClientHook(java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> hook) {
        skinChangeResultClientHook = java.util.Objects.requireNonNull(hook);
    }

    public static void handleSkinChangeResultClient(de.chunkloader.network.payload.SkinChangeResultPayload payload) {
        skinChangeResultClientHook.accept(payload);
    }

    private static volatile Consumer<String> clearCustomSkinClientHook;

    private static volatile java.util.function.Consumer<SyncCustomSkinPayload> syncCustomSkinClientHook;

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
        PayloadTypeRegistry.clientboundPlay().register(OpenHelpPayload.TYPE, OpenHelpPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenChunkMapPayload.WITH_SPAWN_DISTANCE, OpenChunkMapPayload.SPAWN_DISTANCE_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenChunkMapPayload.WITH_ACCESS, OpenChunkMapPayload.ACCESS_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChunkMapLoaderDetailsPayload.TYPE, ChunkMapLoaderDetailsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EasterEggSkinPayload.TYPE, EasterEggSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EasterEggEmotePayload.TYPE, EasterEggEmotePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DisabledChunkloadersListPayload.WITH_SHARES, DisabledChunkloadersListPayload.SHARES_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ActiveChunkloadersListPayload.TYPE, ActiveChunkloadersListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ActiveChunkloadersListPayload.WITH_SHARES, ActiveChunkloadersListPayload.SHARES_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChunkloaderActionPayload.TYPE, ChunkloaderActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SimulationStatusRequestPayload.TYPE, SimulationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChunkplayerStatusRequestPayload.TYPE, ChunkplayerStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DisabledChunkloadersListRequestPayload.TYPE, DisabledChunkloadersListRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActiveChunkloadersListRequestPayload.TYPE, ActiveChunkloadersListRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenChunkMapRequestPayload.TYPE, OpenChunkMapRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeleteDisabledChunkloaderPayload.TYPE, DeleteDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RestoreDisabledChunkloaderPayload.TYPE, RestoreDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateDisabledChunkloaderCoordsPayload.TYPE, UpdateDisabledChunkloaderCoordsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, UpdateDisabledChunkloaderCoordsResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenameChunkloaderPayload.TYPE, RenameChunkloaderPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InvalidateCachePayload.TYPE, InvalidateCachePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCustomSkinPayload.TYPE, SyncCustomSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.chunkloader.network.payload.SkinChangeResultPayload.TYPE, de.chunkloader.network.payload.SkinChangeResultPayload.CODEC);
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

        ServerPlayNetworking.registerGlobalReceiver(ActiveChunkloadersListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleActiveChunkloadersListRequest(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenChunkMapRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> handleOpenChunkMapRequest(player, payload));
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

    public static boolean tryOpenHelp(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, OpenHelpPayload.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(player, new OpenHelpPayload());
        return true;
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        var config = ChunkloaderMod.getConfig();
        var entry = config != null
                ? config.getEntry(data.fakeplayerChunkX(), data.fakeplayerChunkZ(), data.dimensionKey())
                : null;
        boolean canModify = PermissionManager.canUse(player) && PermissionManager.canModifyEntry(player, entry);
        boolean includeAccess = ServerPlayNetworking.canSend(player, OpenChunkMapPayload.WITH_ACCESS);
        boolean shared = includeAccess && !canModify && PermissionManager.canOperateEntry(player, entry);
        boolean includeSpawnDistance = includeAccess || ServerPlayNetworking.canSend(player, OpenChunkMapPayload.WITH_SPAWN_DISTANCE);
        ServerPlayNetworking.send(player, new OpenChunkMapPayload(data, canModify, includeSpawnDistance, includeAccess, shared));
        if (entry != null && ServerPlayNetworking.canSend(player, ChunkMapLoaderDetailsPayload.TYPE)) {
            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager != null) {
                ServerPlayNetworking.send(player, manager.buildMapLoaderDetails(data, entry, player, canModify || shared));
            }
        }
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
        sendApplyCustomSkin(playerName, layerMask, model, pngBytes, 0);
    }

    @Environment(EnvType.CLIENT)
    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes, int requestId) {
        ClientPlayNetworking.send(new ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes, requestId));
    }

    @Environment(EnvType.CLIENT)
    public static void sendClearCustomSkin(String playerName) {
        sendClearCustomSkin(playerName, 0);
    }

    @Environment(EnvType.CLIENT)
    public static void sendClearCustomSkin(String playerName, int requestId) {
        ClientPlayNetworking.send(new ClearCustomSkinPayload(playerName, requestId));
    }

    private static void sendSkinChangeResult(ServerPlayer player, String name, int requestId,
            boolean removed, boolean success, String message) {
        if (requestId == 0) {
            if (!success) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            return;
        }
        var response = new de.chunkloader.network.payload.SkinChangeResultPayload(name, requestId, removed, success, message);
        ServerPlayNetworking.send(player, response);
    }

    private static void handleApplyCustomSkin(ServerPlayer player, ApplyCustomSkinPayload payload) {
        if (payload == null) return;
        String name = payload.playerName() == null ? "" : payload.playerName().trim();
        String error = null;
        if (!PermissionManager.canUse(player)) error = "You don't have permission to change skins.";
        else if (isOnActionCooldown(player)) error = "Please wait a moment before changing the skin again.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), false, false, error);
            return;
        }
        markActionCooldown(player);
        var config = ChunkloaderMod.getConfig();
        var entry = config == null ? null : config.getEntryByName(name);
        if (entry == null) error = "The selected player is unavailable.";
        else if (!PermissionManager.canOperateEntry(player, entry)) error = "You don't have permission to change this player.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), false, false, error);
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        boolean ok = manager != null && manager.applyCustomSkin(entry.name(), payload.pngBytes(), payload.layerMask(), payload.model());
        sendSkinChangeResult(player, name, payload.requestId(), false, ok,
            ok ? "Skin applied for " + entry.name() + "." : "Failed to apply the skin.");
    }

    private static void handleClearCustomSkin(ServerPlayer player, ClearCustomSkinPayload payload) {
        if (payload == null) return;
        String name = payload.playerName() == null ? "" : payload.playerName().trim();
        String error = null;
        if (!PermissionManager.canUse(player)) error = "You don't have permission to change skins.";
        else if (isOnActionCooldown(player)) error = "Please wait a moment before changing the skin again.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), true, false, error);
            return;
        }
        markActionCooldown(player);
        var config = ChunkloaderMod.getConfig();
        var entry = config == null ? null : config.getEntryByName(name);
        if (entry == null) error = "The selected player is unavailable.";
        else if (!PermissionManager.canOperateEntry(player, entry)) error = "You don't have permission to change this player.";
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null && error == null) error = "The selected player is unavailable.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), true, false, error);
            return;
        }
        manager.clearCustomSkin(entry.name());
        sendSkinChangeResult(player, name, payload.requestId(), true, true, "Custom skin removed.");
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
        return PermissionManager.canModifyEntry(player, entry);
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
        boolean canChange = switch (payload.action()) {
            case DELETE, RESET_TO_DEFAULTS -> canModifyEntry(player, entry);
            case TOGGLE_VISUALIZE, TOGGLE_VISUALIZE3D -> true;
            case TOGGLE_ENABLED, TOGGLE_MOB_SPAWNING, RADIUS_INCREMENT, RADIUS_DECREMENT,
                    TOGGLE_NAME_VISIBLE, TOGGLE_MOB_TARGET, TOGGLE_MOB_AI,
                    TOGGLE_HIDE_OTHER_DOTS -> PermissionManager.canOperateEntry(player, entry);
        };
        if (!canChange) {
            player.sendSystemMessage(Component.literal("You don't have permission to change this player.").withStyle(ChatFormatting.RED));
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
                if (!entry.enabled()) {
                    String limitMessage = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
                    if (limitMessage != null) {
                        player.sendSystemMessage(Component.literal(limitMessage).withStyle(ChatFormatting.RED));
                        return;
                    }
                }
                manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ(), dimension);
                var entryAfter = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
                if (entryAfter == null || entryAfter.enabled() == entry.enabled()) {
                    String limitMessage = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
                    player.sendSystemMessage(Component.literal(limitMessage != null
                            ? limitMessage : "Could not change this player's status.").withStyle(ChatFormatting.RED));
                    return;
                }

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
            case TOGGLE_MOB_AI -> manager.toggleChunkloaderMobAi(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_VISUALIZE -> {
                manager.toggleVisualization(player, entry);
                refreshOpenChunkMap(player, manager);
                return;
            }
            case TOGGLE_VISUALIZE3D -> {
                manager.toggleVisualization3D(player, entry);
                refreshOpenChunkMap(player, manager);
                return;
            }
            case TOGGLE_HIDE_OTHER_DOTS -> {  }
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ(), dimension);
            case DELETE -> {
                manager.removeChunkloader(payload.chunkX(), payload.chunkZ(), dimension);

                return;
            }
        }

        entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry != null && entry.enabled()) {
            refreshOpenChunkMapMarkers(player.level().getServer(), manager);
        }
    }

    private static void handleSimulationStatusRequest(ServerPlayer player, boolean forceResponse) {
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

        ServerPlayNetworking.send(player, response);
    }

    private static void handleChunkplayerStatusRequest(ServerPlayer player, boolean forceResponse) {
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

        ServerPlayNetworking.send(player, response);
    }

    public static void clearPlayerCache(ServerPlayer player) {
        if (player != null) {
            UUID playerId = player.getUUID();
            openChunkMaps.remove(playerId);
            actionCooldownUntilMs.remove(playerId);
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
        for (UUID playerId : new ArrayList<>(openChunkMaps.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.connection == null) {
                openChunkMaps.remove(playerId);
            } else {
                refreshOpenChunkMap(player, manager);
            }
        }
    }

    public static void refreshOpenChunkMap(ServerPlayer player, de.chunkloader.manager.ChunkloaderManager manager) {
        if (player.connection == null || player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            return;
        }
        String openKey = openChunkMaps.get(player.getUUID());
        if (openKey == null || openKey.isBlank()) {
            return;
        }
        if (!PermissionManager.canUse(player)) {
            sendCloseChunkMap(player);
            return;
        }
        var entry = resolveOpenChunkMapEntry(openKey);
        if (entry == null || !entry.enabled()) {
            return;
        }
        try {
            sendOpenChunkMap(player, manager.buildChunkMapData(entry, player));
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn("Failed to refresh open chunk map for {}: {}", player.getName().getString(), e.toString());
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
    public static void requestActiveChunkloadersList() {
        ClientPlayNetworking.send(new ActiveChunkloadersListRequestPayload());
    }

    @Environment(EnvType.CLIENT)
    public static void requestOpenChunkMap(int chunkX, int chunkZ, String dimension) {
        ClientPlayNetworking.send(new OpenChunkMapRequestPayload(chunkX, chunkZ, dimension));
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
    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ, String newDimension) {
        ClientPlayNetworking.send(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ, newDimension));
    }

    @Environment(EnvType.CLIENT)
    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        ClientPlayNetworking.send(new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName));
    }

    private static void handleRenameChunkloader(ServerPlayer player, RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "You don't have permission to rename players."));
            return;
        }
        if (isOnActionCooldown(player)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "Please wait a moment and try again."));
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."));
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "Player not found."));
            return;
        }
        if (!PermissionManager.canOperateEntry(player, entry)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "You don't own this player."));
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "No open chunk map session for this player."));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."));
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

        boolean includeShares = ServerPlayNetworking.canSend(player, DisabledChunkloadersListPayload.WITH_SHARES);
        var disabledList = manager.getDisabledChunkloadersList(player, includeShares);
        ServerPlayNetworking.send(player, new DisabledChunkloadersListPayload(disabledList, includeShares));
    }

    private static void handleActiveChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view players."));
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        boolean includeShares = ServerPlayNetworking.canSend(player, ActiveChunkloadersListPayload.WITH_SHARES);
        var entries = manager.getActiveChunkloadersList(player, includeShares);
        ServerPlayNetworking.send(player, new ActiveChunkloadersListPayload(entries, includeShares));
    }

    private static void handleOpenChunkMapRequest(ServerPlayer player, OpenChunkMapRequestPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to use player actions."));
            return;
        }
        var config = ChunkloaderMod.getConfig();
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (config == null || manager == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null || !entry.enabled()) {
            return;
        }
        if (!PermissionManager.canOperateEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }
        sendOpenChunkMap(player, manager.buildChunkMapData(entry, player));
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
        if (!PermissionManager.canOperateEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
            return;
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        String limitMessage = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
        if (limitMessage != null) {
            player.sendSystemMessage(Component.literal(limitMessage).withStyle(ChatFormatting.RED));
            return;
        }
        if (!manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension)) {
            String failure = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
            player.sendSystemMessage(Component.literal(failure != null
                    ? failure : "Could not restore this player.").withStyle(ChatFormatting.RED));
        }
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
            if (!PermissionManager.canOperateEntry(player, entry)) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't own this player."));
                return;
            }

            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager == null) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player manager not available."));
                return;
            }

            String newDimension = payload.newDimension() != null && !payload.newDimension().isBlank()
                    ? payload.newDimension() : oldDimension;
            String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
                payload.oldChunkX(), payload.oldChunkZ(), oldDimension,
                payload.newChunkX(), payload.newChunkZ(),
                payload.newBlockX(), payload.newBlockY(), payload.newBlockZ(),
                newDimension
            );

            if (errorMessage == null) {
                refreshOpenChunkMapMarkers(player.level().getServer(), manager);
                var moved = config.getEntry(payload.newChunkX(), payload.newChunkZ(), newDimension);
                if (moved != null && moved.enabled()) {
                    handleActiveChunkloadersListRequest(player);
                } else {
                    handleDisabledChunkloadersListRequest(player);
                }
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

