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
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class ChunkloaderNetworking {

    private ChunkloaderNetworking() {
    }
    
    private static final Map<UUID, SimulationStatusResponsePayload> lastSimulationStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkplayerStatusResponsePayload> lastChunkplayerStatus = new ConcurrentHashMap<>();
    
    private static final Map<String, SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    
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

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenChunkMapPayload.ID, OpenChunkMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseChunkMapPayload.ID, CloseChunkMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FakePlayerVisibilityPayload.ID, FakePlayerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SimulationStatusResponsePayload.ID, SimulationStatusResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChunkplayerStatusResponsePayload.ID, ChunkplayerStatusResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DisabledChunkloadersListPayload.ID, DisabledChunkloadersListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkloaderActionPayload.ID, ChunkloaderActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SimulationStatusRequestPayload.ID, SimulationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkplayerStatusRequestPayload.ID, ChunkplayerStatusRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DisabledChunkloadersListRequestPayload.ID, DisabledChunkloadersListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteDisabledChunkloaderPayload.ID, DeleteDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RestoreDisabledChunkloaderPayload.ID, RestoreDisabledChunkloaderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDisabledChunkloaderCoordsPayload.ID, UpdateDisabledChunkloaderCoordsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateDisabledChunkloaderCoordsResponsePayload.ID, UpdateDisabledChunkloaderCoordsResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RenameChunkloaderResponsePayload.ID, RenameChunkloaderResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RenameChunkloaderPayload.ID, RenameChunkloaderPayload.CODEC);

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
        
        ClientPlayNetworking.registerGlobalReceiver(RenameChunkloaderResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.player != null && client.currentScreen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });
    }

    public static void sendOpenChunkMap(ServerPlayerEntity player, ChunkMapData data) {
        ServerPlayNetworking.send(player, new OpenChunkMapPayload(data));
    }
    
    public static void sendCloseChunkMap(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new CloseChunkMapPayload());
    }
    
    public static void broadcastCloseChunkMap(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
                ServerPlayNetworking.send(player, payload);
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
    
    @Environment(EnvType.CLIENT)
    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, int value) {
        ClientPlayNetworking.send(new ChunkloaderActionPayload(chunkX, chunkZ, action, value));
    }

    private static void handleClientAction(ServerPlayerEntity player, ChunkloaderActionPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to use chunkloader actions."), false);
            return;
        }
        
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        switch (payload.action()) {
            case TOGGLE_ENABLED -> manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ());
            case TOGGLE_MOB_SPAWNING -> manager.toggleChunkloaderMobSpawning(payload.chunkX(), payload.chunkZ());
            case RADIUS_INCREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), Math.max(1, payload.value()));
            case RADIUS_DECREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ());
            case TOGGLE_VISUALIZE -> manager.toggleChunkloaderVisualize(payload.chunkX(), payload.chunkZ());
            case TOGGLE_VISUALIZE3D -> manager.toggleChunkloaderVisualize3D(payload.chunkX(), payload.chunkZ());
            case TOGGLE_HIDE_OTHER_DOTS -> manager.toggleChunkloaderHideOtherDots(payload.chunkX(), payload.chunkZ());
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ());
            case DELETE -> {
                manager.removeChunkloader(payload.chunkX(), payload.chunkZ());
                return;
            }
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            return;
        }
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ());
        if (entry != null && entry.enabled()) {
            sendOpenChunkMap(player, manager.buildChunkMapData(entry));
        }
    }
    
    private static void handleSimulationStatusRequest(ServerPlayerEntity player, boolean forceResponse) {
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
        if (a.distance() != b.distance()) {
            return false;
        }
        return true;
    }
    
    private static void handleChunkplayerStatusRequest(ServerPlayerEntity player, boolean forceResponse) {
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
        }
    }
    
    public static void invalidateChunkCache() {
        chunkSimulationCache.clear();
        chunkChunkplayerCache.clear();
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
    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ) {
        ClientPlayNetworking.send(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ));
    }
    
    @Environment(EnvType.CLIENT)
    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ) {
        ClientPlayNetworking.send(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ));
    }
    
    @Environment(EnvType.CLIENT)
    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ClientPlayNetworking.send(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ));
    }
    
    @Environment(EnvType.CLIENT)
    public static void sendRenameChunkloader(int chunkX, int chunkZ, String newName) {
        ClientPlayNetworking.send(new RenameChunkloaderPayload(chunkX, chunkZ, newName));
    }
    
    private static void handleRenameChunkloader(ServerPlayerEntity player, RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to rename chunkloaders."), false);
            return;
        }
        
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        String newName = payload.newName();
        if (newName == null || newName.trim().isEmpty() || !newName.matches("^[a-zA-Z0-9]+$")) {
            String errorMessage = "Name can only contain letters and numbers.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
            return;
        }
        
        boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), newName);
        if (success) {
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(true, null));
        } else {
            String errorMessage = "This name is already in use or invalid.";
            ServerPlayNetworking.send(player, new RenameChunkloaderResponsePayload(false, errorMessage));
        }
    }
    
    private static void handleDisabledChunkloadersListRequest(ServerPlayerEntity player) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to view disabled chunkloaders."), false);
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
            player.sendMessage(Text.literal("You don't have permission to delete disabled chunkloaders."), false);
            return;
        }
        
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        manager.deleteDisabledChunkloader(payload.chunkX(), payload.chunkZ());
    }
    
    private static void handleRestoreDisabledChunkloader(ServerPlayerEntity player, RestoreDisabledChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendMessage(Text.literal("You don't have permission to restore disabled chunkloaders."), false);
            return;
        }
        
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ());
    }
    
    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayerEntity player, UpdateDisabledChunkloaderCoordsPayload payload) {
        try {
            if (!PermissionManager.canUse(player)) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't have permission to update disabled chunkloader coordinates."));
                return;
            }
            
            var manager = ChunkloaderMod.getChunkloaderManager();
            if (manager == null) {
                ServerPlayNetworking.send(player, new UpdateDisabledChunkloaderCoordsResponsePayload(false, "Chunkloader manager not available."));
                return;
            }
            
            String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
                payload.oldChunkX(), payload.oldChunkZ(),
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

