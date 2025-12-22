package de.chunkloader.network;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.ChunkplayerStatusRequestPayload;
import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
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
import de.chunkloader.permissions.PermissionManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChunkloaderNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private static final Map<UUID, SimulationStatusResponsePayload> lastSimulationStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkplayerStatusResponsePayload> lastChunkplayerStatus = new ConcurrentHashMap<>();

    private static final Map<String, SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();

    private ChunkloaderNetworking() {}

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.STREAM_CODEC);
        registrar.playToClient(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.STREAM_CODEC);
        registrar.playToClient(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.STREAM_CODEC);
        registrar.playToClient(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.STREAM_CODEC);
        registrar.playToClient(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.STREAM_CODEC);
        registrar.playToClient(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.STREAM_CODEC);
        registrar.playToClient(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC);
        registrar.playToClient(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.STREAM_CODEC);

        registrar.playToServer(ChunkloaderActionPayload.TYPE, ChunkloaderActionPayload.STREAM_CODEC, ChunkloaderNetworking::handleChunkloaderAction);
        registrar.playToServer(SimulationStatusRequestPayload.TYPE, SimulationStatusRequestPayload.STREAM_CODEC, ChunkloaderNetworking::handleSimulationStatusRequest);
        registrar.playToServer(ChunkplayerStatusRequestPayload.TYPE, ChunkplayerStatusRequestPayload.STREAM_CODEC, ChunkloaderNetworking::handleChunkplayerStatusRequest);
        registrar.playToServer(DisabledChunkloadersListRequestPayload.TYPE, DisabledChunkloadersListRequestPayload.STREAM_CODEC, ChunkloaderNetworking::handleDisabledChunkloadersListRequest);
        registrar.playToServer(DeleteDisabledChunkloaderPayload.TYPE, DeleteDisabledChunkloaderPayload.STREAM_CODEC, ChunkloaderNetworking::handleDeleteDisabledChunkloader);
        registrar.playToServer(RestoreDisabledChunkloaderPayload.TYPE, RestoreDisabledChunkloaderPayload.STREAM_CODEC, ChunkloaderNetworking::handleRestoreDisabledChunkloader);
        registrar.playToServer(UpdateDisabledChunkloaderCoordsPayload.TYPE, UpdateDisabledChunkloaderCoordsPayload.STREAM_CODEC, ChunkloaderNetworking::handleUpdateDisabledChunkloaderCoords);
        registrar.playToServer(RenameChunkloaderPayload.TYPE, RenameChunkloaderPayload.STREAM_CODEC, ChunkloaderNetworking::handleRenameChunkloader);
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new OpenChunkMapPayload(data));
        }
    }

    public static void sendCloseChunkMap(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new CloseChunkMapPayload());
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

    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, int value) {
        ClientPacketDistributor.sendToServer(new ChunkloaderActionPayload(chunkX, chunkZ, action, value));
    }

    public static void requestSimulationStatus() {
        ClientPacketDistributor.sendToServer(new SimulationStatusRequestPayload(false));
    }

    public static void requestChunkplayerStatus() {
        ClientPacketDistributor.sendToServer(new ChunkplayerStatusRequestPayload(false));
    }

    public static void requestDisabledChunkloadersList() {
        ClientPacketDistributor.sendToServer(new DisabledChunkloadersListRequestPayload());
    }

    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ) {
        ClientPacketDistributor.sendToServer(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ));
    }

    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ) {
        ClientPacketDistributor.sendToServer(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ));
    }

    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ClientPacketDistributor.sendToServer(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ));
    }
    
    public static void sendRenameChunkloader(int chunkX, int chunkZ, String newName) {
        ClientPacketDistributor.sendToServer(new RenameChunkloaderPayload(chunkX, chunkZ, newName));
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

    private static void handleDisabledChunkloadersListRequest(DisabledChunkloadersListRequestPayload payload, IPayloadContext ctx) {
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

    private static void handleRestoreDisabledChunkloader(RestoreDisabledChunkloaderPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleRestoreDisabledChunkloader(player, payload));
    }
    
    private static void handleUpdateDisabledChunkloaderCoords(UpdateDisabledChunkloaderCoordsPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleUpdateDisabledChunkloaderCoords(player, payload));
    }
    
    private static void handleRenameChunkloader(RenameChunkloaderPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> {
            if (!PermissionManager.canUse(player)) {
                player.sendSystemMessage(Component.literal("You don't have permission to rename chunkloaders."), false);
                return;
            }
            
            ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
            if (manager == null) {
                return;
            }

            String newNameRaw = payload.newName();
            String newName = newNameRaw != null ? newNameRaw.trim() : null;
            if (newName == null || newName.isEmpty() || !newName.matches("^[a-zA-Z0-9]+$")) {
                String errorMessage = "Name can only contain letters and numbers";
                PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, errorMessage));
                return;
            }
            
            boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), newName);
            if (success) {
                PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(true, null));
            } else {
                String errorMessage = "This name is already in use or invalid.";
                PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, errorMessage));
            }
        });
    }

    private static void handleChunkloaderAction(ServerPlayer player, ChunkloaderActionPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to use chunkloader actions."), false);
            return;
        }
        
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            return;
        }

        switch (payload.action()) {
            case TOGGLE_ENABLED -> {
                manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ());
                var entryAfter = config.getEntry(payload.chunkX(), payload.chunkZ());
                if (entryAfter != null && !entryAfter.enabled()) {
                    sendCloseChunkMap(player);
                    return;
                }
            }
            case TOGGLE_MOB_SPAWNING -> manager.toggleChunkloaderMobSpawning(payload.chunkX(), payload.chunkZ());
            case RADIUS_INCREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), Math.max(1, payload.value()));
            case RADIUS_DECREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ());
            case TOGGLE_VISUALIZE -> manager.toggleChunkloaderVisualize(payload.chunkX(), payload.chunkZ());
            case TOGGLE_VISUALIZE3D -> manager.toggleChunkloaderVisualize3D(payload.chunkX(), payload.chunkZ());
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ());
            case TOGGLE_HIDE_OTHER_DOTS -> manager.toggleChunkloaderHideOtherDots(payload.chunkX(), payload.chunkZ());
            case DELETE -> {
                manager.removeChunkloader(payload.chunkX(), payload.chunkZ());
                return;
            }
        }

        var entry = config.getEntry(payload.chunkX(), payload.chunkZ());
        if (entry != null) {
            sendOpenChunkMap(player, manager.buildChunkMapData(entry));
        }
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
        return a.distance() == b.distance();
    }
    
    public static void clearPlayerCache(Player player) {
        if (player != null) {
            UUID playerId = player.getUUID();
            lastSimulationStatus.remove(playerId);
            lastChunkplayerStatus.remove(playerId);
        }
    }
    
    public static void invalidateChunkCache() {
        chunkSimulationCache.clear();
        chunkChunkplayerCache.clear();
    }
    
    private static void handleDisabledChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view disabled chunkloaders."), false);
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
                    entry.name(), entry.allowMobSpawning(), entry.dimension(), entry.isFakeplayer()));
        }
        PacketDistributor.sendToPlayer(player, new DisabledChunkloadersListPayload(payloadEntries));
    }
    
    private static void handleDeleteDisabledChunkloader(ServerPlayer player, DeleteDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to delete chunkloaders."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        manager.deleteDisabledChunkloader(payload.chunkX(), payload.chunkZ());
        handleDisabledChunkloadersListRequest(player);
    }
    
    private static void handleRestoreDisabledChunkloader(ServerPlayer player, RestoreDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to restore chunkloaders."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ());
        handleDisabledChunkloadersListRequest(player);
    }
    
    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayer player, UpdateDisabledChunkloaderCoordsPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to update chunkloader coordinates."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            player.sendSystemMessage(Component.literal("Chunkloader Manager is not initialized."), false);
            return;
        }
        String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
            payload.oldChunkX(), payload.oldChunkZ(),
            payload.newChunkX(), payload.newChunkZ(),
                payload.newBlockX(), payload.newBlockY(), payload.newBlockZ());
        boolean success = errorMessage == null;
        PacketDistributor.sendToPlayer(player, new UpdateDisabledChunkloaderCoordsResponsePayload(success, errorMessage));
        if (success) {
            handleDisabledChunkloadersListRequest(player);
        }
    }
    
}
