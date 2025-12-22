package de.chunkloader.network;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkloaderNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static final Identifier CHANNEL_NAME = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "main");
    
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(CHANNEL_NAME)
        .networkProtocolVersion(Integer.parseInt(PROTOCOL_VERSION))
        .clientAcceptedVersions(Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
        .serverAcceptedVersions(Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
        .simpleChannel();

    private static final Map<UUID, de.chunkloader.network.payload.SimulationStatusResponsePayload> lastSimulationStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, de.chunkloader.network.payload.ChunkplayerStatusResponsePayload> lastChunkplayerStatus = new ConcurrentHashMap<>();
    
    private static final Map<String, de.chunkloader.network.payload.SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, de.chunkloader.network.payload.ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    
    private static int messageId = 0;
    
    @SuppressWarnings("deprecation")
    public static void init() {
        CHANNEL.messageBuilder(OpenChunkMapPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> OpenChunkMapPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> OpenChunkMapPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        var data = payload.data();
                        
                        if (client.screen instanceof de.chunkloader.client.screen.ChunkMapScreen existingScreen) {
                            existingScreen.updateData(data);
                        } else {
                            client.setScreen(new de.chunkloader.client.screen.ChunkMapScreen(data));
                        }
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
            
        CHANNEL.messageBuilder(CloseChunkMapPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> CloseChunkMapPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> CloseChunkMapPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        if (client.screen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                            client.setScreen(null);
                        }
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
            
        CHANNEL.messageBuilder(FakePlayerVisibilityPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> FakePlayerVisibilityPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> FakePlayerVisibilityPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    de.chunkloader.client.FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
                });
                ctx.setPacketHandled(true);
            })
            .add();
        
        CHANNEL.messageBuilder(ChunkloaderActionPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> ChunkloaderActionPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> ChunkloaderActionPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                
                handleChunkloaderAction(player, payload);
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.SimulationStatusRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.SimulationStatusRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.SimulationStatusRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                
                handleSimulationStatusRequest(player, payload.forceResponse());
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.ChunkplayerStatusRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ChunkplayerStatusRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ChunkplayerStatusRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                
                handleChunkplayerStatusRequest(player, payload.forceResponse());
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.SimulationStatusResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.SimulationStatusResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.SimulationStatusResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        de.chunkloader.client.hud.SimulationStatusHUD.updateStatus(payload);
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        de.chunkloader.client.hud.ChunkplayerStatusHUD.updateStatus(payload);
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleDisabledChunkloadersListRequest(player);
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleDeleteDisabledChunkloader(player, payload);
            })
            .add();
        
        CHANNEL.messageBuilder(de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleRestoreDisabledChunkloader(player, payload);
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleUpdateDisabledChunkloaderCoords(player, payload);
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.DisabledChunkloadersListPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.DisabledChunkloadersListPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.DisabledChunkloadersListPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player == null) {
                        return;
                    }
                    
                    net.minecraft.client.gui.screens.Screen currentScreen = client.screen;
                    if (currentScreen instanceof de.chunkloader.client.screen.DisabledChunkloadersScreen existingScreen) {
                        existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                    } else if (currentScreen == null || currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                        net.minecraft.client.gui.screens.Screen parent = currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen ? currentScreen : null;
                        client.setScreen(new de.chunkloader.client.screen.DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null && client.screen instanceof de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen editScreen) {
                        editScreen.handleUpdateResponse(payload);
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.RenameChunkloaderResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.RenameChunkloaderResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.RenameChunkloaderResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null && client.screen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                        renameScreen.handleRenameResponse(payload);
                    }
                });
                ctx.setPacketHandled(true);
            })
            .add();
            
        CHANNEL.messageBuilder(de.chunkloader.network.payload.RenameChunkloaderPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.RenameChunkloaderPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.RenameChunkloaderPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                
                handleRenameChunkloader(player, payload);
            })
            .add();
    }
    
    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player != null) {
            CHANNEL.send(new OpenChunkMapPayload(data), PacketDistributor.PLAYER.with(player));
        }
    }
    
    public static void sendCloseChunkMap(ServerPlayer player) {
        if (player != null) {
            CHANNEL.send(new CloseChunkMapPayload(), PacketDistributor.PLAYER.with(player));
        }
    }
    
    public static void broadcastCloseChunkMap(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        CloseChunkMapPayload payload = new CloseChunkMapPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        }
    }
    
    public static void broadcastFakePlayerVisibility(MinecraftServer server, String fakePlayerName, boolean visible) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        FakePlayerVisibilityPayload payload = new FakePlayerVisibilityPayload(fakePlayerName, visible);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        }
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
            case TOGGLE_HIDE_OTHER_DOTS -> manager.toggleChunkloaderHideOtherDots(payload.chunkX(), payload.chunkZ());
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ());
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
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, int value) {
        CHANNEL.send(new ChunkloaderActionPayload(chunkX, chunkZ, action, value), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void requestSimulationStatus() {
        CHANNEL.send(new de.chunkloader.network.payload.SimulationStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void requestChunkplayerStatus() {
        CHANNEL.send(new de.chunkloader.network.payload.ChunkplayerStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void requestDisabledChunkloadersList() {
        CHANNEL.send(new de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload(), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ) {
        CHANNEL.send(new de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload(chunkX, chunkZ), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ) {
        CHANNEL.send(new de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload(chunkX, chunkZ), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ), PacketDistributor.SERVER.noArg());
    }
    
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void sendRenameChunkloader(int chunkX, int chunkZ, String newName) {
        CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderPayload(chunkX, chunkZ, newName), PacketDistributor.SERVER.noArg());
    }
    
    private static void handleRenameChunkloader(ServerPlayer player, de.chunkloader.network.payload.RenameChunkloaderPayload payload) {
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
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, errorMessage), PacketDistributor.PLAYER.with(player));
            return;
        }
        
        boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), newName);
        if (success) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(true, null), PacketDistributor.PLAYER.with(player));
        } else {
            String errorMessage = "This name is already in use or invalid.";
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, errorMessage), PacketDistributor.PLAYER.with(player));
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
        
        de.chunkloader.network.payload.SimulationStatusResponsePayload cachedResponse = chunkSimulationCache.get(chunkKey);
        
        de.chunkloader.network.payload.SimulationStatusResponsePayload response;
        if (cachedResponse != null && !forceResponse) {
            response = cachedResponse;
        } else {
            var status = manager.getSimulationStatus(player);
            response = new de.chunkloader.network.payload.SimulationStatusResponsePayload(
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
        de.chunkloader.network.payload.SimulationStatusResponsePayload lastStatus = lastSimulationStatus.get(playerId);
        
        if (forceResponse || lastStatus == null || !statusEquals(lastStatus, response)) {
            lastSimulationStatus.put(playerId, response);
            CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
        }
    }
    
    private static boolean statusEquals(de.chunkloader.network.payload.SimulationStatusResponsePayload a, de.chunkloader.network.payload.SimulationStatusResponsePayload b) {
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
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = world.dimension().identifier().toString();
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        String chunkKey = playerChunkX + "," + playerChunkZ + "," + dimension;
        
        de.chunkloader.network.payload.ChunkplayerStatusResponsePayload cachedResponse = chunkChunkplayerCache.get(chunkKey);
        
        de.chunkloader.network.payload.ChunkplayerStatusResponsePayload response;
        if (cachedResponse != null && !forceResponse) {
            response = cachedResponse;
        } else {
            var status = manager.getChunkplayerStatus(player);
            response = new de.chunkloader.network.payload.ChunkplayerStatusResponsePayload(
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
        de.chunkloader.network.payload.ChunkplayerStatusResponsePayload lastStatus = lastChunkplayerStatus.get(playerId);
        
        if (forceResponse || lastStatus == null || !chunkplayerStatusEquals(lastStatus, response)) {
            lastChunkplayerStatus.put(playerId, response);
            CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
        }
    }
    
    private static boolean chunkplayerStatusEquals(de.chunkloader.network.payload.ChunkplayerStatusResponsePayload a, de.chunkloader.network.payload.ChunkplayerStatusResponsePayload b) {
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
        List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> payloadEntries = new java.util.ArrayList<>();
        for (ChunkloaderManager.DisabledChunkloaderEntry entry : disabled) {
            payloadEntries.add(new de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry(
                entry.chunkX(), entry.chunkZ(),
                entry.blockX(), entry.blockY(), entry.blockZ(),
                entry.name(), entry.allowMobSpawning(), entry.dimension(), entry.isFakeplayer()
            ));
        }
        CHANNEL.send(new de.chunkloader.network.payload.DisabledChunkloadersListPayload(payloadEntries), PacketDistributor.PLAYER.with(player));
    }
    
    private static void handleDeleteDisabledChunkloader(ServerPlayer player, de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload payload) {
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
    
    private static void handleRestoreDisabledChunkloader(ServerPlayer player, de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload payload) {
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
    
    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayer player, de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload payload) {
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
            payload.newBlockX(), payload.newBlockY(), payload.newBlockZ()
        );
        boolean success = errorMessage == null;
        CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(success, errorMessage), PacketDistributor.PLAYER.with(player));
        if (success) {
            handleDisabledChunkloadersListRequest(player);
        }
    }
}
