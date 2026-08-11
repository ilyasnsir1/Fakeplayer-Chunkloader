package de.chunkloader.network;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.ChunkplayerStatusRequestPayload;
import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.CloseChunkMapRequestPayload;
import de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload;
import de.chunkloader.network.payload.SimulationStatusRequestPayload;
import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import de.chunkloader.network.payload.RenameChunkloaderPayload;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import de.chunkloader.network.payload.InvalidateCachePayload;
import de.chunkloader.network.payload.ClearCustomSkinPayload;
import de.chunkloader.network.payload.ApplyCustomSkinPayload;
import de.chunkloader.network.payload.SyncCustomSkinPayload;
import de.chunkloader.permissions.PermissionManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChunkloaderNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private static volatile Consumer<String> clearCustomSkinClientHook;

    private static volatile Consumer<SyncCustomSkinPayload> syncCustomSkinClientHook;

    private static final Map<UUID, SimulationStatusResponsePayload> lastSimulationStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkplayerStatusResponsePayload> lastChunkplayerStatus = new ConcurrentHashMap<>();

    private static final Map<String, SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> openChunkMaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> actionCooldownUntilMs = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 250L;

    private ChunkloaderNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.STREAM_CODEC);
        registrar.playToClient(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.STREAM_CODEC);
        registrar.playToClient(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.STREAM_CODEC);
        registrar.playToClient(de.chunkloader.network.payload.EasterEggSkinPayload.TYPE,
                de.chunkloader.network.payload.EasterEggSkinPayload.STREAM_CODEC);
        registrar.playToClient(de.chunkloader.network.payload.EasterEggEmotePayload.TYPE,
                de.chunkloader.network.payload.EasterEggEmotePayload.STREAM_CODEC);
        registrar.playToClient(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.STREAM_CODEC);
        registrar.playToClient(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.STREAM_CODEC);
        registrar.playToClient(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.STREAM_CODEC);
        registrar.playToClient(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE,
                UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC);
        registrar.playToClient(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.STREAM_CODEC);
        registrar.playToClient(InvalidateCachePayload.TYPE, InvalidateCachePayload.STREAM_CODEC);
        registrar.playToClient(SyncCustomSkinPayload.TYPE, SyncCustomSkinPayload.STREAM_CODEC);
        registrar.playBidirectional(ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleClearCustomSkin);

        registrar.playToServer(ChunkloaderActionPayload.TYPE, ChunkloaderActionPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleChunkloaderAction);
        registrar.playToServer(SimulationStatusRequestPayload.TYPE, SimulationStatusRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleSimulationStatusRequest);
        registrar.playToServer(ChunkplayerStatusRequestPayload.TYPE, ChunkplayerStatusRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleChunkplayerStatusRequest);
        registrar.playToServer(DisabledChunkloadersListRequestPayload.TYPE,
                DisabledChunkloadersListRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleDisabledChunkloadersListRequest);
        registrar.playToServer(DeleteDisabledChunkloaderPayload.TYPE, DeleteDisabledChunkloaderPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleDeleteDisabledChunkloader);
        registrar.playToServer(RestoreDisabledChunkloaderPayload.TYPE, RestoreDisabledChunkloaderPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleRestoreDisabledChunkloader);
        registrar.playToServer(UpdateDisabledChunkloaderCoordsPayload.TYPE,
                UpdateDisabledChunkloaderCoordsPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleUpdateDisabledChunkloaderCoords);
        registrar.playToServer(RenameChunkloaderPayload.TYPE, RenameChunkloaderPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleRenameChunkloader);
        registrar.playToServer(CloseChunkMapRequestPayload.TYPE, CloseChunkMapRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleCloseChunkMap);
        registrar.playToServer(ApplyCustomSkinPayload.TYPE, ApplyCustomSkinPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleApplyCustomSkin);
    }

    private static String createChunkKey(int chunkX, int chunkZ, String dimension) {
        String dim = (dimension == null || dimension.isBlank()) ? "unknown" : dimension;
        return dim + ":" + chunkX + "," + chunkZ;
    }

    private static void trackOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player == null || data == null) {
            return;
        }
        if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            return;
        }
        openChunkMaps.put(player.getUUID(),
                createChunkKey(data.centerChunkX(), data.centerChunkZ(), data.dimensionKey()));
    }

    private static void clearOpenChunkMap(ServerPlayer player) {
        if (player == null) {
            return;
        }
        openChunkMaps.remove(player.getUUID());
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new OpenChunkMapPayload(data));
            trackOpenChunkMap(player, data);
        }
    }

    public static void sendCloseChunkMap(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new CloseChunkMapPayload());
            clearOpenChunkMap(player);
        }
    }

    public static void closeOpenChunkMapsFor(
        MinecraftServer server,
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
            if (player == null) {
                continue;
            }
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            String openKey = openChunkMaps.get(player.getUUID());
            if (targetKey.equals(openKey)) {
                if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                    PacketDistributor.sendToPlayer(player, payload);
                    clearOpenChunkMap(player);
                }
            }
        }
    }

    public static void broadcastCloseChunkMap(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                PacketDistributor.sendToPlayer(player, new CloseChunkMapPayload());
                clearOpenChunkMap(player);
            }
        }
    }

    public static void broadcastFakePlayerVisibility(MinecraftServer server, String fakePlayerName, boolean visible) {
        if (server == null) {
            return;
        }
        FakePlayerVisibilityPayload payload = new FakePlayerVisibilityPayload(fakePlayerName, visible);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void setClearCustomSkinClientHook(Consumer<String> hook) {
        clearCustomSkinClientHook = hook;
    }

    public static void setSyncCustomSkinClientHook(Consumer<SyncCustomSkinPayload> hook) {
        syncCustomSkinClientHook = hook;
    }

    public static void broadcastClearCustomSkin(MinecraftServer server, String playerName) {
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                PacketDistributor.sendToPlayer(player, payload);
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
        PacketDistributor.sendToPlayer(player, new SyncCustomSkinPayload(playerName, layerMask, model, pngBytes));
    }

    public static void broadcastSyncCustomSkin(
            MinecraftServer server,
            String playerName,
            int layerMask,
            String model,
            byte[] pngBytes) {
        if (playerName == null || playerName.isBlank() || pngBytes == null || pngBytes.length == 0) {
            return;
        }
        SyncCustomSkinPayload payload = new SyncCustomSkinPayload(playerName, layerMask, model, pngBytes);
        Consumer<SyncCustomSkinPayload> clientHook = syncCustomSkinClientHook;
        if (clientHook != null) {
            clientHook.accept(payload);
        }
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void sendFakePlayerVisibility(ServerPlayer player, String fakePlayerName, boolean visible) {
        if (player == null || fakePlayerName == null || fakePlayerName.isBlank()) {
            return;
        }
        if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new FakePlayerVisibilityPayload(fakePlayerName, visible));
    }

    public static void sendEasterEggSkin(ServerPlayer player, UUID playerUuid, int skinIndex) {
        if (player == null || playerUuid == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new de.chunkloader.network.payload.EasterEggSkinPayload(playerUuid, skinIndex));
    }

    public static void broadcastEasterEggSkin(MinecraftServer server, UUID playerUuid, int skinIndex) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        de.chunkloader.network.payload.EasterEggSkinPayload payload = new de.chunkloader.network.payload.EasterEggSkinPayload(
                playerUuid, skinIndex);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && !(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer)) {
                if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    public static void sendEasterEggEmote(ServerPlayer player, UUID playerUuid, long startGameTime) {
        if (player == null || playerUuid == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new de.chunkloader.network.payload.EasterEggEmotePayload(playerUuid, startGameTime));
    }

    public static void broadcastEasterEggEmote(MinecraftServer server, UUID playerUuid, long startGameTime) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        de.chunkloader.network.payload.EasterEggEmotePayload payload = new de.chunkloader.network.payload.EasterEggEmotePayload(
                playerUuid, startGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && !(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer)) {
                if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    private static void handleChunkloaderAction(ChunkloaderActionPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleChunkloaderAction(player, payload));
    }

    private static void handleSimulationStatusRequest(SimulationStatusRequestPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleSimulationStatusRequest(player, payload.forceResponse()));
    }

    private static void handleChunkplayerStatusRequest(ChunkplayerStatusRequestPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleChunkplayerStatusRequest(player, payload.forceResponse()));
    }

    private static void handleDisabledChunkloadersListRequest(DisabledChunkloadersListRequestPayload payload,
            IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleDisabledChunkloadersListRequest(player));
    }

    private static void handleDeleteDisabledChunkloader(DeleteDisabledChunkloaderPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleDeleteDisabledChunkloader(player, payload));
    }

    private static void handleRestoreDisabledChunkloader(RestoreDisabledChunkloaderPayload payload,
            IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleRestoreDisabledChunkloader(player, payload));
    }

    private static void handleUpdateDisabledChunkloaderCoords(UpdateDisabledChunkloaderCoordsPayload payload,
            IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleUpdateDisabledChunkloaderCoords(player, payload));
    }

    private static void handleApplyCustomSkin(ApplyCustomSkinPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleApplyCustomSkin(player, payload));
    }

    private static void handleClearCustomSkin(ClearCustomSkinPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleClearCustomSkin(player, payload));
    }

    private static void handleApplyCustomSkin(ServerPlayer player, ApplyCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to change skins.")
                .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (payload == null || payload.playerName() == null || payload.playerName().isBlank()) {
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            return;
        }
        var entry = config.getEntryByName(payload.playerName());
        if (entry == null) {
            player.sendSystemMessage(Component.literal("Unknown player.").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }

        var manager = ChunkloaderForgeMod.getChunkloaderManager();
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
            player.sendSystemMessage(Component.literal("Failed to apply custom skin.").withStyle(ChatFormatting.RED), false);
        }
    }

    private static void handleClearCustomSkin(ServerPlayer player, ClearCustomSkinPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to change skins.")
                .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (payload == null || payload.playerName() == null || payload.playerName().isBlank()) {
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            return;
        }
        var entry = config.getEntryByName(payload.playerName());
        if (entry == null) {
            var manager = ChunkloaderForgeMod.getChunkloaderManager();
            if (manager != null) {
                manager.clearCustomSkin(payload.playerName());
            } else {
                broadcastClearCustomSkin(player.level().getServer(), payload.playerName());
            }
            return;
        }
        if (!canModifyEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager != null) {
            manager.clearCustomSkin(entry.name() != null ? entry.name() : payload.playerName());
        }
    }

    private static void handleRenameChunkloader(RenameChunkloaderPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleRenameChunkloader(player, payload));
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
        String playerName = player.getName().getString();
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

    private static void handleRenameChunkloader(ServerPlayer player, RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to rename players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "Player not found."));
            return;
        }
        if (!canModifyEntry(player, entry)) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "You don't own this player."));
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            PacketDistributor.sendToPlayer(player,
                    new RenameChunkloaderResponsePayload(false, "No open chunk map session for this player."));
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        String newNameRaw = payload.newName();
        String newName = newNameRaw != null ? newNameRaw.trim() : null;
        if (newName == null || newName.isEmpty() || newName.length() > 16 || !newName.matches("^[a-zA-Z0-9]+$")) {
            String errorMessage = "Name must be 1-16 characters and can only contain letters and numbers.";
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, errorMessage));
            return;
        }

        boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), dimension, newName);
        if (success) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(true, null));
            var updated = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
            if (updated != null) {
                broadcastOpenChunkMapUpdate(player.level().getServer(), manager, updated);
                refreshOpenChunkMapMarkers(player.level().getServer(), manager);
            }
        } else {
            String errorMessage = "This name is already in use or invalid.";
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, errorMessage));
        }
    }

    private static void handleChunkloaderAction(ServerPlayer player, ChunkloaderActionPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to use player actions."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var config = ChunkloaderForgeMod.getConfig();
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            player.sendSystemMessage(
                    Component.literal("No open chunk map session for this player.").withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        switch (payload.action()) {
            case TOGGLE_ENABLED -> {
                manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ(), dimension);
                var entryAfter = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
                if (entryAfter != null && !entryAfter.enabled()) {
                    String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
                    player.sendSystemMessage(
                            Component.literal("Player disabled (Press " + keyName + " to open disabled list)"), false);

                    return;
                }
            }
            case TOGGLE_MOB_SPAWNING -> {
                boolean success = manager.toggleChunkloaderMobSpawning(payload.chunkX(), payload.chunkZ(), dimension);
                if (!success) {
                    player.sendSystemMessage(
                            Component.literal("Toggle failed: rename the player first to avoid a name conflict.")
                                    .withStyle(ChatFormatting.RED),
                            false);
                    sendCloseChunkMap(player);
                    return;
                }
            }
            case RADIUS_INCREMENT ->
                manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, Math.max(1, payload.value()));
            case RADIUS_DECREMENT ->
                manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_VISUALIZE -> manager.toggleChunkloaderVisualize(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_VISUALIZE3D -> manager.toggleChunkloaderVisualize3D(payload.chunkX(), payload.chunkZ(), dimension);
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_HIDE_OTHER_DOTS -> {  }
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

    private static void handleCloseChunkMap(CloseChunkMapRequestPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> clearOpenChunkMap(player));
    }

    private static void handleSimulationStatusRequest(ServerPlayer player, boolean forceResponse) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = world.dimension().identifier().toString();
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        String chunkKey = playerChunkX + "," + playerChunkZ + "," + dimension;

        SimulationStatusResponsePayload cachedResponse = chunkSimulationCache.get(chunkKey);

        SimulationStatusResponsePayload response;
        if (cachedResponse != null && !forceResponse) {
            response = cachedResponse;
        } else {
            var status = manager.getSimulationStatus(player);
            response = new SimulationStatusResponsePayload(
                    status.inSimulatedChunk(),
                    status.fakeplayerName(),
                    status.chunkX(),
                    status.chunkZ(),
                    status.simulationDistance(),
                    status.distance());

            chunkSimulationCache.put(chunkKey, response);
        }

        UUID playerId = player.getUUID();
        SimulationStatusResponsePayload lastStatus = lastSimulationStatus.get(playerId);

        if (forceResponse || lastStatus == null || !statusEquals(lastStatus, response)) {
            lastSimulationStatus.put(playerId, response);
            PacketDistributor.sendToPlayer(player, response);
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
        return a.distance() == b.distance();
    }

    private static void handleChunkplayerStatusRequest(ServerPlayer player, boolean forceResponse) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = world.dimension().identifier().toString();
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        String chunkKey = playerChunkX + "," + playerChunkZ + "," + dimension;

        ChunkplayerStatusResponsePayload cachedResponse = chunkChunkplayerCache.get(chunkKey);

        ChunkplayerStatusResponsePayload response;
        if (cachedResponse != null && !forceResponse) {
            response = cachedResponse;
        } else {
            var status = manager.getChunkplayerStatus(player);
            response = new ChunkplayerStatusResponsePayload(
                    status.inLoadedChunk(),
                    status.chunkplayerName(),
                    status.chunkX(),
                    status.chunkZ(),
                    status.radius(),
                    status.distance());

            chunkChunkplayerCache.put(chunkKey, response);
        }

        UUID playerId = player.getUUID();
        ChunkplayerStatusResponsePayload lastStatus = lastChunkplayerStatus.get(playerId);

        if (forceResponse || lastStatus == null || !chunkplayerStatusEquals(lastStatus, response)) {
            lastChunkplayerStatus.put(playerId, response);
            PacketDistributor.sendToPlayer(player, response);
        }
    }

    private static boolean chunkplayerStatusEquals(ChunkplayerStatusResponsePayload a,
            ChunkplayerStatusResponsePayload b) {
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
        return a.distance() == b.distance();
    }

    public static void clearPlayerCache(Player player) {
        if (player != null) {
            UUID playerId = player.getUUID();
            lastSimulationStatus.remove(playerId);
            lastChunkplayerStatus.remove(playerId);
            openChunkMaps.remove(playerId);
            actionCooldownUntilMs.remove(playerId);
        }
    }

    public static void broadcastOpenChunkMapUpdate(MinecraftServer server, ChunkloaderManager manager,
            de.chunkloader.config.ChunkloaderTarget entry) {
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
                PacketDistributor.sendToPlayer(player, new OpenChunkMapPayload(data));
                trackOpenChunkMap(player, data);
            }
        }
    }

    public static void refreshOpenChunkMapMarkers(MinecraftServer server, ChunkloaderManager manager) {
        if (server == null || manager == null || server.getPlayerList() == null) {
            return;
        }
        if (server.isSameThread()) {
            refreshOpenChunkMapMarkersNow(server, manager);
        } else {
            server.execute(() -> refreshOpenChunkMapMarkersNow(server, manager));
        }
    }

    private static void refreshOpenChunkMapMarkersNow(MinecraftServer server, ChunkloaderManager manager) {
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
                com.mojang.logging.LogUtils.getLogger().warn("Failed to refresh open chunk map markers for {}: {}", player.getName().getString(), e.toString());
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
            var config = ChunkloaderForgeMod.getConfig();
            return config != null ? config.getEntry(chunkX, chunkZ, dimension) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void invalidateChunkCache() {
        chunkSimulationCache.clear();
        chunkChunkplayerCache.clear();
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            broadcastInvalidateCache(server);
        }
    }

    public static void broadcastInvalidateCache(MinecraftServer server) {
        if (server == null) {
            return;
        }
        InvalidateCachePayload payload = new InvalidateCachePayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void handleDisabledChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view disabled players."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        List<ChunkloaderManager.DisabledChunkloaderEntry> disabled = manager.getDisabledChunkloadersList();
        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> payloadEntries = new java.util.ArrayList<>();
        for (ChunkloaderManager.DisabledChunkloaderEntry entry : disabled) {
            payloadEntries.add(new DisabledChunkloadersListPayload.DisabledChunkloaderEntry(
                    entry.chunkX(), entry.chunkZ(),
                    entry.blockX(), entry.blockY(), entry.blockZ(),
                    entry.name(), entry.allowMobSpawning(), entry.dimension(), entry.isFakeplayer(),
                    entry.easterEggSkinIndex()));
        }
        PacketDistributor.sendToPlayer(player, new DisabledChunkloadersListPayload(payloadEntries));
    }

    private static void handleDeleteDisabledChunkloader(ServerPlayer player, DeleteDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to delete disabled players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        manager.deleteDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
        handleDisabledChunkloadersListRequest(player);
    }

    private static void handleRestoreDisabledChunkloader(ServerPlayer player,
            RestoreDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to restore disabled players."), false);
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
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
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension);
        handleDisabledChunkloadersListRequest(player);
    }

    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayer player,
            UpdateDisabledChunkloaderCoordsPayload payload) {
        if (!PermissionManager.canUse(player)) {
            PacketDistributor.sendToPlayer(player,
                    new UpdateDisabledChunkloaderCoordsResponsePayload(false,
                            "You don't have permission to update disabled player coordinates."));
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            PacketDistributor.sendToPlayer(player,
                    new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Config not available."));
            return;
        }
        String oldDimension = payload.oldDimension() != null && !payload.oldDimension().isBlank()
                ? payload.oldDimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.oldChunkX(), payload.oldChunkZ(), oldDimension);
        if (entry == null) {
            PacketDistributor.sendToPlayer(player,
                    new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player not found."));
            return;
        }
        if (!canModifyEntry(player, entry)) {
            PacketDistributor.sendToPlayer(player,
                    new UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't own this player."));
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            PacketDistributor.sendToPlayer(player,
                    new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player manager not available."));
            return;
        }
        String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
                payload.oldChunkX(), payload.oldChunkZ(), oldDimension,
                payload.newChunkX(), payload.newChunkZ(),
                payload.newBlockX(), payload.newBlockY(), payload.newBlockZ());
        boolean success = errorMessage == null;
        PacketDistributor.sendToPlayer(player,
                new UpdateDisabledChunkloaderCoordsResponsePayload(success,
                        success ? "Coordinates updated successfully." : errorMessage));
        if (success) {
            handleDisabledChunkloadersListRequest(player);
        }
    }

}
