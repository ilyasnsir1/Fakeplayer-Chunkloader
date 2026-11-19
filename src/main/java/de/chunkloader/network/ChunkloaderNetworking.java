package de.chunkloader.network;

import java.util.ArrayList;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.ChunkplayerStatusRequestPayload;
import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.OpenHelpPayload;
import de.chunkloader.network.payload.CloseChunkMapRequestPayload;
import de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.ChunkMapLoaderDetailsPayload;
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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChunkloaderNetworking {
    private static final String PROTOCOL_VERSION = "4";

    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> skinChangeResultClientHook = payload -> {};

    public static void setSkinChangeResultClientHook(java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> hook) {
        skinChangeResultClientHook = java.util.Objects.requireNonNull(hook);
    }

    public static void handleSkinChangeResultClient(de.chunkloader.network.payload.SkinChangeResultPayload payload) {
        skinChangeResultClientHook.accept(payload);
    }

    private static volatile Consumer<String> clearCustomSkinClientHook;

    private static volatile Consumer<SyncCustomSkinPayload> syncCustomSkinClientHook;

    private static final Map<String, SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> openChunkMaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> actionCooldownUntilMs = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 250L;

    private ChunkloaderNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(de.chunkloader.network.payload.SkinChangeResultPayload.TYPE, de.chunkloader.network.payload.SkinChangeResultPayload.STREAM_CODEC,
                (value, context) -> context.enqueueWork(() -> skinChangeResultClientHook.accept(value)));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            de.chunkloader.client.ChunkloaderClientPayloadHandlers.register(registrar);
        } else {
            registrar.playToClient(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.optional().playToClient(ChunkMapLoaderDetailsPayload.TYPE, ChunkMapLoaderDetailsPayload.CODEC, (payload, context) -> {});
            registrar.optional().playToClient(OpenHelpPayload.TYPE, OpenHelpPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(de.chunkloader.network.payload.EasterEggSkinPayload.TYPE, de.chunkloader.network.payload.EasterEggSkinPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(de.chunkloader.network.payload.EasterEggEmotePayload.TYPE, de.chunkloader.network.payload.EasterEggEmotePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(de.chunkloader.network.payload.ActiveChunkloadersListPayload.TYPE, de.chunkloader.network.payload.ActiveChunkloadersListPayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(InvalidateCachePayload.TYPE, InvalidateCachePayload.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(SyncCustomSkinPayload.TYPE, SyncCustomSkinPayload.STREAM_CODEC, (payload, context) -> {});
        }
        registerClearCustomSkin(registrar);

        registrar.playToServer(ChunkloaderActionPayload.TYPE, ChunkloaderActionPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleChunkloaderAction);
        registrar.playToServer(SimulationStatusRequestPayload.TYPE, SimulationStatusRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleSimulationStatusRequest);
        registrar.playToServer(ChunkplayerStatusRequestPayload.TYPE, ChunkplayerStatusRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleChunkplayerStatusRequest);
        registrar.playToServer(DisabledChunkloadersListRequestPayload.TYPE,
                DisabledChunkloadersListRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::handleDisabledChunkloadersListRequest);
        registrar.playToServer(de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload.TYPE,
                de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::onActiveChunkloadersListRequest);
        registrar.playToServer(de.chunkloader.network.payload.OpenChunkMapRequestPayload.TYPE,
                de.chunkloader.network.payload.OpenChunkMapRequestPayload.STREAM_CODEC,
                ChunkloaderNetworking::onOpenChunkMapRequest);
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

    public static boolean tryOpenHelp(ServerPlayer player) {
        if (!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, OpenHelpPayload.TYPE.id())) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new OpenHelpPayload());
        return true;
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player != null) {
            var config = ChunkloaderForgeMod.getConfig();
            var entry = config != null
                    ? config.getEntry(data.fakeplayerChunkX(), data.fakeplayerChunkZ(), data.dimensionKey())
                    : null;
            boolean canModify = PermissionManager.canUse(player) && PermissionManager.canModifyEntry(player, entry);
            PacketDistributor.sendToPlayer(player, new OpenChunkMapPayload(data, canModify,
                    !canModify && PermissionManager.canOperateEntry(player, entry)));
            if (entry != null && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, ChunkMapLoaderDetailsPayload.TYPE.id())) {
                var manager = ChunkloaderForgeMod.getChunkloaderManager();
                if (manager != null) {
                    var details = manager.buildMapLoaderDetails(data, entry, player,
                        PermissionManager.canOperateEntry(player, entry));
                    PacketDistributor.sendToPlayer(player, details);
                }
            }
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

    private static void handleApplyCustomSkin(ApplyCustomSkinPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        ctx.enqueueWork(() -> handleApplyCustomSkin(player, payload));
    }

    private static void registerClearCustomSkin(PayloadRegistrar registrar) {
        IPayloadHandler<ClearCustomSkinPayload> handler = ChunkloaderNetworking::handleClearCustomSkin;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                PayloadRegistrar.class.getMethod("playBidirectional", CustomPacketPayload.Type.class,
                        StreamCodec.class, IPayloadHandler.class, IPayloadHandler.class)
                        .invoke(registrar, ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.STREAM_CODEC,
                                handler, handler);
                return;
            } catch (NoSuchMethodException olderNeoForge) {
            } catch (ReflectiveOperationException registrationError) {
                throw new IllegalStateException("Could not register the skin removal packet", registrationError);
            }
        }
        registrar.playBidirectional(ClearCustomSkinPayload.TYPE, ClearCustomSkinPayload.STREAM_CODEC, handler);
    }

    private static void handleClearCustomSkin(ClearCustomSkinPayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> handleClearCustomSkin(player, payload));
        } else {
            Consumer<String> clientHook = clearCustomSkinClientHook;
            if (clientHook != null) {
                ctx.enqueueWork(() -> clientHook.accept(payload.playerName()));
            }
        }
    }

        private static void sendSkinChangeResult(ServerPlayer player, String name, int requestId,
            boolean removed, boolean success, String message) {
        if (requestId == 0) {
            if (!success) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            return;
        }
        var response = new de.chunkloader.network.payload.SkinChangeResultPayload(name, requestId, removed, success, message);
        PacketDistributor.sendToPlayer(player, response);
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
        var config = ChunkloaderForgeMod.getConfig();
        var entry = config == null ? null : config.getEntryByName(name);
        if (entry == null) error = "The selected player is unavailable.";
        else if (!PermissionManager.canOperateEntry(player, entry)) error = "You don't have permission to change this player.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), false, false, error);
            return;
        }
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
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
        var config = ChunkloaderForgeMod.getConfig();
        var entry = config == null ? null : config.getEntryByName(name);
        if (entry == null) error = "The selected player is unavailable.";
        else if (!PermissionManager.canOperateEntry(player, entry)) error = "You don't have permission to change this player.";
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null && error == null) error = "The selected player is unavailable.";
        if (error != null) {
            sendSkinChangeResult(player, name, payload.requestId(), true, false, error);
            return;
        }
        manager.clearCustomSkin(entry.name());
        sendSkinChangeResult(player, name, payload.requestId(), true, true, "Custom skin removed.");
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
            return false;
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
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "You don't have permission to rename players."));
            return;
        }
        if (isOnActionCooldown(player)) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "Please wait a moment and try again."));
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."));
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "Player not found."));
            return;
        }
        if (!PermissionManager.canOperateEntry(player, entry)) {
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
            PacketDistributor.sendToPlayer(player, new RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."));
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
        boolean canChange = switch (payload.action()) {
            case DELETE, RESET_TO_DEFAULTS -> canModifyEntry(player, entry);
            case TOGGLE_VISUALIZE, TOGGLE_VISUALIZE3D -> true;
            default -> PermissionManager.canOperateEntry(player, entry);
        };
        if (!canChange) {
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
            case TOGGLE_MOB_TARGET -> manager.toggleChunkloaderMobTarget(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_MOB_AI -> manager.toggleChunkloaderMobAi(payload.chunkX(), payload.chunkZ(), dimension);
            case RADIUS_INCREMENT ->
                manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, Math.max(1, payload.value()));
            case RADIUS_DECREMENT ->
                manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, -Math.max(1, payload.value()));
            case TOGGLE_NAME_VISIBLE -> manager.toggleChunkloaderNameVisible(payload.chunkX(), payload.chunkZ(), dimension);
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
            case RESET_TO_DEFAULTS -> manager.resetChunkloaderToDefaults(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_HIDE_OTHER_DOTS -> {  }
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
        String dimension = world.dimension().location().toString();
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

        PacketDistributor.sendToPlayer(player, response);
    }

    private static void handleChunkplayerStatusRequest(ServerPlayer player, boolean forceResponse) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = world.dimension().location().toString();
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

        PacketDistributor.sendToPlayer(player, response);
    }

    public static void clearPlayerCache(Player player) {
        if (player != null) {
            UUID playerId = player.getUUID();
            openChunkMaps.remove(playerId);
            actionCooldownUntilMs.remove(playerId);
            var manager = ChunkloaderForgeMod.getChunkloaderManager();
            if (manager != null) {
                manager.clearViewerVisualizations(playerId);
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
            com.mojang.logging.LogUtils.getLogger().warn("Failed to refresh open chunk map for {}: {}", player.getName().getString(), e.toString());
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

    private static void onActiveChunkloadersListRequest(de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) return;
        ctx.enqueueWork(() -> handleActiveChunkloadersListRequest(player));
    }

    private static void onOpenChunkMapRequest(de.chunkloader.network.payload.OpenChunkMapRequestPayload payload, IPayloadContext ctx) {
        Player p = ctx.player();
        if (!(p instanceof ServerPlayer player)) return;
        ctx.enqueueWork(() -> handleOpenChunkMapRequest(player, payload));
    }

    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.ActiveChunkloadersListPayload> activeChunkloadersListHandler = payload -> {};

    public static void setActiveChunkloadersListHandler(java.util.function.Consumer<de.chunkloader.network.payload.ActiveChunkloadersListPayload> handler) {
        activeChunkloadersListHandler = java.util.Objects.requireNonNull(handler);
    }

    private static void handleActiveChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view players."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) return;
        PacketDistributor.sendToPlayer(player, new de.chunkloader.network.payload.ActiveChunkloadersListPayload(manager.getActiveChunkloadersList(player)));
    }

    private static void handleOpenChunkMapRequest(ServerPlayer player, de.chunkloader.network.payload.OpenChunkMapRequestPayload payload) {
        if (!PermissionManager.canUse(player)) return;
        var config = ChunkloaderForgeMod.getConfig();
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (config == null || manager == null) return;
        String dimension = payload.dimension() != null && !payload.dimension().isBlank() ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null || !entry.enabled() || !PermissionManager.canOperateEntry(player, entry)) return;
        sendOpenChunkMap(player, manager.buildChunkMapData(entry, player));
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
        List<ChunkloaderManager.DisabledChunkloaderEntry> disabled = manager.getDisabledChunkloadersList(player);
        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> payloadEntries = new java.util.ArrayList<>();
        for (ChunkloaderManager.DisabledChunkloaderEntry entry : disabled) {
            payloadEntries.add(new DisabledChunkloadersListPayload.DisabledChunkloaderEntry(
                    entry.chunkX(), entry.chunkZ(),
                    entry.blockX(), entry.blockY(), entry.blockZ(),
                    entry.name(), entry.allowMobSpawning(), entry.dimension(), entry.isFakeplayer(),
                    entry.easterEggSkinIndex(), entry.sharedBy()));
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
        if (!PermissionManager.canOperateEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED), false);
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
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
        if (!PermissionManager.canOperateEntry(player, entry)) {
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
        String newDimension = payload.newDimension() != null && !payload.newDimension().isBlank()
                    ? payload.newDimension() : oldDimension;
            String errorMessage = manager.updateDisabledChunkloaderCoordsWithMessage(
                payload.oldChunkX(), payload.oldChunkZ(), oldDimension,
                payload.newChunkX(), payload.newChunkZ(),
                payload.newBlockX(), payload.newBlockY(), payload.newBlockZ(), newDimension);
        boolean success = errorMessage == null;
        PacketDistributor.sendToPlayer(player,
                new UpdateDisabledChunkloaderCoordsResponsePayload(success,
                        success ? "Coordinates updated successfully." : errorMessage));
        if (success) {
            refreshOpenChunkMapMarkers(player.level().getServer(), manager);
            var moved = config.getEntry(payload.newChunkX(), payload.newChunkZ(), newDimension);
            if (moved != null && moved.enabled()) handleActiveChunkloadersListRequest(player);
            else handleDisabledChunkloadersListRequest(player);
        }
    }

}
