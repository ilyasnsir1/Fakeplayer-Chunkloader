package de.chunkloader.network;
import de.chunkloader.network.payload.ClearCustomSkinPayload;
import de.chunkloader.network.payload.ApplyCustomSkinPayload;

import java.util.ArrayList;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.OpenHelpPayload;
import de.chunkloader.network.payload.CloseChunkMapRequestPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.ChunkMapLoaderDetailsPayload;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
import java.util.function.Consumer;

public class ChunkloaderNetworking {
    private static final String PROTOCOL_VERSION = "5";
    private static final Identifier CHANNEL_NAME = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "main");

    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> skinChangeResultClientHook = payload -> {};

    public static void setSkinChangeResultClientHook(java.util.function.Consumer<de.chunkloader.network.payload.SkinChangeResultPayload> hook) {
        skinChangeResultClientHook = java.util.Objects.requireNonNull(hook);
    }

    public static void handleSkinChangeResultClient(de.chunkloader.network.payload.SkinChangeResultPayload payload) {
        skinChangeResultClientHook.accept(payload);
    }

    private static volatile Consumer<String> clearCustomSkinClientHook;

    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SyncCustomSkinPayload> syncCustomSkinClientHook;

    public static final SimpleChannel CHANNEL = ChannelBuilder.named(CHANNEL_NAME)
        .networkProtocolVersion(Integer.parseInt(PROTOCOL_VERSION))
        .clientAcceptedVersions(Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
        .serverAcceptedVersions(Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
        .simpleChannel();
    private static final SimpleChannel HELP_CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "help"))
        .networkProtocolVersion(1)
        .optional()
        .simpleChannel();

    private static volatile Runnable openHelpHandler = () -> {};

    private static final Map<String, de.chunkloader.network.payload.SimulationStatusResponsePayload> chunkSimulationCache = new ConcurrentHashMap<>();
    private static final Map<String, de.chunkloader.network.payload.ChunkplayerStatusResponsePayload> chunkChunkplayerCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> openChunkMaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> actionCooldownUntilMs = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 250L;

    private static volatile Consumer<ChunkMapLoaderDetailsPayload> loaderDetailsHandler = payload -> {};
    private static volatile java.util.function.Consumer<OpenChunkMapPayload> openChunkMapHandler = payload -> {};
    private static volatile java.util.function.Consumer<CloseChunkMapPayload> closeChunkMapHandler = payload -> {};
    private static volatile java.util.function.Consumer<FakePlayerVisibilityPayload> fakePlayerVisibilityHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.EasterEggSkinPayload> easterEggSkinHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.EasterEggEmotePayload> easterEggEmoteHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SimulationStatusResponsePayload> simulationStatusHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.ChunkplayerStatusResponsePayload> chunkplayerStatusHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.ActiveChunkloadersListPayload> activeChunkloadersListHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.DisabledChunkloadersListPayload> disabledChunkloadersListHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload> updateDisabledChunkloaderCoordsResponseHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.RenameChunkloaderResponsePayload> renameChunkloaderResponseHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.ClearCustomSkinPayload> clearCustomSkinHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.SyncCustomSkinPayload> syncCustomSkinHandler = payload -> {};
    private static volatile java.util.function.Consumer<de.chunkloader.network.payload.InvalidateCachePayload> invalidateCacheHandler = payload -> {};

    private static int messageId = 0;

    @SuppressWarnings("deprecation")
    public static void init() {
        HELP_CHANNEL.messageBuilder(OpenHelpPayload.class, 0, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> OpenHelpPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> OpenHelpPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(openHelpHandler);
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(OpenChunkMapPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> OpenChunkMapPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> OpenChunkMapPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    openChunkMapHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(CloseChunkMapPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> CloseChunkMapPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> CloseChunkMapPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    closeChunkMapHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(CloseChunkMapRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> CloseChunkMapRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> CloseChunkMapRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        clearOpenChunkMap(player);
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
                    fakePlayerVisibilityHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.EasterEggSkinPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.EasterEggSkinPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.EasterEggSkinPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    easterEggSkinHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.EasterEggEmotePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.EasterEggEmotePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.EasterEggEmotePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    easterEggEmoteHandler.accept(payload);
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
                    simulationStatusHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ChunkplayerStatusResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    chunkplayerStatusHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleActiveChunkloadersListRequest(player);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.OpenChunkMapRequestPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.OpenChunkMapRequestPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.OpenChunkMapRequestPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                handleOpenChunkMapRequest(player, payload);
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

        CHANNEL.messageBuilder(de.chunkloader.network.payload.ActiveChunkloadersListPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ActiveChunkloadersListPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ActiveChunkloadersListPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> activeChunkloadersListHandler.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.DisabledChunkloadersListPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.DisabledChunkloadersListPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.DisabledChunkloadersListPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    disabledChunkloadersListHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    updateDisabledChunkloaderCoordsResponseHandler.accept(payload);
                });
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.RenameChunkloaderResponsePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.RenameChunkloaderResponsePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.RenameChunkloaderResponsePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    renameChunkloaderResponseHandler.accept(payload);
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

        CHANNEL.messageBuilder(de.chunkloader.network.payload.ClearCustomSkinPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ClearCustomSkinPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ClearCustomSkinPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> clearCustomSkinHandler.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.SyncCustomSkinPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.SyncCustomSkinPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.SyncCustomSkinPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> syncCustomSkinHandler.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.SkinChangeResultPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.SkinChangeResultPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.SkinChangeResultPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> skinChangeResultClientHook.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.ApplyCustomSkinPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.ApplyCustomSkinPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.ApplyCustomSkinPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    handleApplyCustomSkin(player, payload);
                }
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.RequestClearCustomSkinPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> de.chunkloader.network.payload.RequestClearCustomSkinPayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.RequestClearCustomSkinPayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    handleClearCustomSkin(player, new de.chunkloader.network.payload.ClearCustomSkinPayload(payload.playerName(), payload.requestId()));
                }
            })
            .add();

        CHANNEL.messageBuilder(de.chunkloader.network.payload.InvalidateCachePayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> de.chunkloader.network.payload.InvalidateCachePayload.STREAM_CODEC.encode(buf, payload))
            .decoder(buf -> de.chunkloader.network.payload.InvalidateCachePayload.STREAM_CODEC.decode(buf))
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> invalidateCacheHandler.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();

        CHANNEL.messageBuilder(ChunkMapLoaderDetailsPayload.class, messageId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> ChunkMapLoaderDetailsPayload.CODEC.encode(buf, payload))
            .decoder(ChunkMapLoaderDetailsPayload.CODEC::decode)
            .consumerMainThread((payload, ctx) -> {
                ctx.enqueueWork(() -> loaderDetailsHandler.accept(payload));
                ctx.setPacketHandled(true);
            })
            .add();
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
        openChunkMaps.put(player.getUUID(), createChunkKey(data.centerChunkX(), data.centerChunkZ(), data.dimensionKey()));
    }

    private static void clearOpenChunkMap(ServerPlayer player) {
        if (player == null) {
            return;
        }
        openChunkMaps.remove(player.getUUID());
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
            ChunkloaderForgeMod.LOGGER.warn("Failed to refresh open chunk map for {}: {}", player.getName().getString(), e.toString());
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

    public static void setLoaderDetailsHandler(Consumer<ChunkMapLoaderDetailsPayload> handler) {
        loaderDetailsHandler = handler;
    }

    public static void setOpenHelpHandler(Runnable handler) {
        openHelpHandler = handler;
    }

    public static void registerClientHandlers(
        java.util.function.Consumer<OpenChunkMapPayload> openChunkMap,
        java.util.function.Consumer<CloseChunkMapPayload> closeChunkMap,
        java.util.function.Consumer<FakePlayerVisibilityPayload> fakePlayerVisibility,
        java.util.function.Consumer<de.chunkloader.network.payload.EasterEggSkinPayload> easterEggSkin,
        java.util.function.Consumer<de.chunkloader.network.payload.EasterEggEmotePayload> easterEggEmote,
        java.util.function.Consumer<de.chunkloader.network.payload.SimulationStatusResponsePayload> simulationStatus,
        java.util.function.Consumer<de.chunkloader.network.payload.ChunkplayerStatusResponsePayload> chunkplayerStatus,
        java.util.function.Consumer<de.chunkloader.network.payload.DisabledChunkloadersListPayload> disabledChunkloadersList,
        java.util.function.Consumer<de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload> updateDisabledChunkloaderCoordsResponse,
        java.util.function.Consumer<de.chunkloader.network.payload.RenameChunkloaderResponsePayload> renameChunkloaderResponse,
        java.util.function.Consumer<de.chunkloader.network.payload.ClearCustomSkinPayload> clearCustomSkin,
        java.util.function.Consumer<de.chunkloader.network.payload.SyncCustomSkinPayload> syncCustomSkin,
        java.util.function.Consumer<de.chunkloader.network.payload.InvalidateCachePayload> invalidateCache
    ) {
        openChunkMapHandler = java.util.Objects.requireNonNull(openChunkMap);
        closeChunkMapHandler = java.util.Objects.requireNonNull(closeChunkMap);
        fakePlayerVisibilityHandler = java.util.Objects.requireNonNull(fakePlayerVisibility);
        easterEggSkinHandler = java.util.Objects.requireNonNull(easterEggSkin);
        easterEggEmoteHandler = java.util.Objects.requireNonNull(easterEggEmote);
        simulationStatusHandler = java.util.Objects.requireNonNull(simulationStatus);
        chunkplayerStatusHandler = java.util.Objects.requireNonNull(chunkplayerStatus);
        disabledChunkloadersListHandler = java.util.Objects.requireNonNull(disabledChunkloadersList);
        updateDisabledChunkloaderCoordsResponseHandler = java.util.Objects.requireNonNull(updateDisabledChunkloaderCoordsResponse);
        renameChunkloaderResponseHandler = java.util.Objects.requireNonNull(renameChunkloaderResponse);
        clearCustomSkinHandler = java.util.Objects.requireNonNull(clearCustomSkin);
        syncCustomSkinHandler = java.util.Objects.requireNonNull(syncCustomSkin);
        invalidateCacheHandler = java.util.Objects.requireNonNull(invalidateCache);
    }

    public static boolean tryOpenHelp(ServerPlayer player) {
        if (!HELP_CHANNEL.isRemotePresent(player.connection.getConnection())) {
            return false;
        }
        HELP_CHANNEL.send(new OpenHelpPayload(), PacketDistributor.PLAYER.with(player));
        return true;
    }

    public static void sendOpenChunkMap(ServerPlayer player, ChunkMapData data) {
        if (player != null) {
            var config = ChunkloaderForgeMod.getConfig();
            var entry = config != null
                    ? config.getEntry(data.fakeplayerChunkX(), data.fakeplayerChunkZ(), data.dimensionKey())
                    : null;
            boolean canModify = PermissionManager.canUse(player) && PermissionManager.canModifyEntry(player, entry);
            CHANNEL.send(new OpenChunkMapPayload(data, canModify,
                    !canModify && PermissionManager.canOperateEntry(player, entry)), PacketDistributor.PLAYER.with(player));
            if (entry != null) {
                var manager = ChunkloaderForgeMod.getChunkloaderManager();
                if (manager != null) {
                    var details = manager.buildMapLoaderDetails(data, entry, player,
                        PermissionManager.canOperateEntry(player, entry));
                    CHANNEL.send(details, PacketDistributor.PLAYER.with(player));
                }
            }
            trackOpenChunkMap(player, data);
        }
    }

    public static void sendCloseChunkMap(ServerPlayer player) {
        if (player != null) {
            CHANNEL.send(new CloseChunkMapPayload(), PacketDistributor.PLAYER.with(player));
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
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
                clearOpenChunkMap(player);
            }
        }
    }

    public static void sendCloseChunkMapToServer() {
        CHANNEL.send(new CloseChunkMapRequestPayload(), PacketDistributor.SERVER.noArg());
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

    public static void setClearCustomSkinClientHook(Consumer<String> hook) {
        clearCustomSkinClientHook = hook;
    }

    public static void setSyncCustomSkinClientHook(java.util.function.Consumer<de.chunkloader.network.payload.SyncCustomSkinPayload> hook) {
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
        de.chunkloader.network.payload.ClearCustomSkinPayload payload =
            new de.chunkloader.network.payload.ClearCustomSkinPayload(playerName);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
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
        CHANNEL.send(new de.chunkloader.network.payload.SyncCustomSkinPayload(playerName, layerMask, model, pngBytes),
            PacketDistributor.PLAYER.with(player));
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
        de.chunkloader.network.payload.SyncCustomSkinPayload payload =
            new de.chunkloader.network.payload.SyncCustomSkinPayload(playerName, layerMask, model, pngBytes);
        java.util.function.Consumer<de.chunkloader.network.payload.SyncCustomSkinPayload> clientHook = syncCustomSkinClientHook;
        if (clientHook != null) {
            clientHook.accept(payload);
        }
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void sendFakePlayerVisibility(ServerPlayer player, String fakePlayerName, boolean visible) {
        if (player == null || fakePlayerName == null || fakePlayerName.isBlank()) {
            return;
        }
        CHANNEL.send(new FakePlayerVisibilityPayload(fakePlayerName, visible), PacketDistributor.PLAYER.with(player));
    }

    public static void sendEasterEggSkin(ServerPlayer player, UUID playerUuid, int skinIndex) {
        if (player == null || playerUuid == null) {
            return;
        }
        CHANNEL.send(new de.chunkloader.network.payload.EasterEggSkinPayload(playerUuid, skinIndex), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastEasterEggSkin(MinecraftServer server, UUID playerUuid, int skinIndex) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        de.chunkloader.network.payload.EasterEggSkinPayload payload = new de.chunkloader.network.payload.EasterEggSkinPayload(playerUuid, skinIndex);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void sendEasterEggEmote(ServerPlayer player, UUID playerUuid, long startGameTime) {
        if (player == null || playerUuid == null) {
            return;
        }
        CHANNEL.send(new de.chunkloader.network.payload.EasterEggEmotePayload(playerUuid, startGameTime), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastEasterEggEmote(MinecraftServer server, UUID playerUuid, long startGameTime) {
        if (server == null || server.getPlayerList() == null || playerUuid == null) {
            return;
        }
        de.chunkloader.network.payload.EasterEggEmotePayload payload = new de.chunkloader.network.payload.EasterEggEmotePayload(playerUuid, startGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
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
            player.sendSystemMessage(Component.literal("No open chunk map session for this player.").withStyle(ChatFormatting.RED), false);
            return;
        }

        switch (payload.action()) {
            case TOGGLE_ENABLED -> {
                if (!entry.enabled()) {
                    String limitMessage = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
                    if (limitMessage != null) {
                        player.sendSystemMessage(Component.literal(limitMessage).withStyle(ChatFormatting.RED), false);
                        return;
                    }
                }
                manager.toggleChunkloaderAt(payload.chunkX(), payload.chunkZ(), dimension);
                var entryAfter = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
                if (entryAfter == null || entryAfter.enabled() == entry.enabled()) {
                    String limitMessage = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
                    player.sendSystemMessage(Component.literal(limitMessage != null
                            ? limitMessage : "Could not change this player's status.").withStyle(ChatFormatting.RED), false);
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
                    player.sendSystemMessage(Component.literal("Toggle failed: rename the player first to avoid a name conflict.").withStyle(ChatFormatting.RED), false);
                    sendCloseChunkMap(player);
                    return;
                }
            }
            case TOGGLE_MOB_TARGET -> manager.toggleChunkloaderMobTarget(payload.chunkX(), payload.chunkZ(), dimension);
            case TOGGLE_MOB_AI -> manager.toggleChunkloaderMobAi(payload.chunkX(), payload.chunkZ(), dimension);
            case RADIUS_INCREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, Math.max(1, payload.value()));
            case RADIUS_DECREMENT -> manager.adjustChunkloaderRadius(payload.chunkX(), payload.chunkZ(), dimension, -Math.max(1, payload.value()));
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

    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        CHANNEL.send(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value), PacketDistributor.SERVER.noArg());
    }

    public static void requestSimulationStatus() {
        CHANNEL.send(new de.chunkloader.network.payload.SimulationStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }

    public static void requestChunkplayerStatus() {
        CHANNEL.send(new de.chunkloader.network.payload.ChunkplayerStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }

    public static void requestDisabledChunkloadersList() {
        CHANNEL.send(new de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload(), PacketDistributor.SERVER.noArg());
    }

    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        CHANNEL.send(new de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension), PacketDistributor.SERVER.noArg());
    }

    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        CHANNEL.send(new de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension), PacketDistributor.SERVER.noArg());
    }

    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ, String newDimension) {
        CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ, newDimension), PacketDistributor.SERVER.noArg());
    }

    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName), PacketDistributor.SERVER.noArg());
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
        sendApplyCustomSkin(playerName, layerMask, model, pngBytes, 0);
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes, int requestId) {
        CHANNEL.send(new de.chunkloader.network.payload.ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes, requestId),
            PacketDistributor.SERVER.noArg());
    }

    public static void sendClearCustomSkin(String playerName) {
        sendClearCustomSkin(playerName, 0);
    }

    public static void sendClearCustomSkin(String playerName, int requestId) {
        CHANNEL.send(new de.chunkloader.network.payload.RequestClearCustomSkinPayload(playerName, requestId), PacketDistributor.SERVER.noArg());
    }

        private static void sendSkinChangeResult(ServerPlayer player, String name, int requestId,
            boolean removed, boolean success, String message) {
        if (requestId == 0) {
            if (!success) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            return;
        }
        var response = new de.chunkloader.network.payload.SkinChangeResultPayload(name, requestId, removed, success, message);
        CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
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

    private static void handleRenameChunkloader(ServerPlayer player, de.chunkloader.network.payload.RenameChunkloaderPayload payload) {
        if (!PermissionManager.canUse(player)) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "You don't have permission to rename players."), PacketDistributor.PLAYER.with(player));
            return;
        }
        if (isOnActionCooldown(player)) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "Please wait a moment and try again."), PacketDistributor.PLAYER.with(player));
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."), PacketDistributor.PLAYER.with(player));
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "Player not found."), PacketDistributor.PLAYER.with(player));
            return;
        }
        if (!PermissionManager.canOperateEntry(player, entry)) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "You don't own this player."), PacketDistributor.PLAYER.with(player));
            return;
        }
        if (!hasMatchingOpenChunkMap(player, payload.chunkX(), payload.chunkZ(), dimension)) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "No open chunk map session for this player."), PacketDistributor.PLAYER.with(player));
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, "Rename is temporarily unavailable."), PacketDistributor.PLAYER.with(player));
            return;
        }

        String newNameRaw = payload.newName();
        String newName = newNameRaw != null ? newNameRaw.trim() : null;
        if (newName == null || newName.isEmpty() || newName.length() > 16 || !newName.matches("^[a-zA-Z0-9]+$")) {
            String errorMessage = "Name must be 1-16 characters and can only contain letters and numbers.";
            CHANNEL.send(new de.chunkloader.network.payload.RenameChunkloaderResponsePayload(false, errorMessage), PacketDistributor.PLAYER.with(player));
            return;
        }

        boolean success = manager.renameChunkloader(payload.chunkX(), payload.chunkZ(), dimension, newName);
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
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
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

        CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
    }

    private static void handleChunkplayerStatusRequest(ServerPlayer player, boolean forceResponse) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        var world = (net.minecraft.server.level.ServerLevel) player.level();
        String dimension = world.dimension().identifier().toString();
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
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

        CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
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

    public static void invalidateChunkCache() {
        chunkSimulationCache.clear();
        chunkChunkplayerCache.clear();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            broadcastInvalidateCache(server);
        }
    }

    public static void broadcastInvalidateCache(MinecraftServer server) {
        if (server == null) {
            return;
        }
        de.chunkloader.network.payload.InvalidateCachePayload payload =
            new de.chunkloader.network.payload.InvalidateCachePayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
                continue;
            }
            if (player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
                CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void setActiveChunkloadersListHandler(java.util.function.Consumer<de.chunkloader.network.payload.ActiveChunkloadersListPayload> handler) {
        activeChunkloadersListHandler = java.util.Objects.requireNonNull(handler);
    }

    private static void handleActiveChunkloadersListRequest(ServerPlayer player) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(Component.literal("You don't have permission to view players."), false);
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        CHANNEL.send(new de.chunkloader.network.payload.ActiveChunkloadersListPayload(manager.getActiveChunkloadersList(player)), PacketDistributor.PLAYER.with(player));
    }

    private static void handleOpenChunkMapRequest(ServerPlayer player, de.chunkloader.network.payload.OpenChunkMapRequestPayload payload) {
        if (!PermissionManager.canUse(player)) {
            return;
        }
        var config = ChunkloaderForgeMod.getConfig();
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (config == null || manager == null) {
            return;
        }
        String dimension = payload.dimension() != null && !payload.dimension().isBlank() ? payload.dimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.chunkX(), payload.chunkZ(), dimension);
        if (entry == null || !entry.enabled() || !PermissionManager.canOperateEntry(player, entry)) {
            return;
        }
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
        List<de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry> payloadEntries =
                manager.getDisabledChunkloadersList(player);
        CHANNEL.send(new de.chunkloader.network.payload.DisabledChunkloadersListPayload(payloadEntries), PacketDistributor.PLAYER.with(player));
    }

    private static void handleDeleteDisabledChunkloader(ServerPlayer player, de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload payload) {
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

    private static void handleRestoreDisabledChunkloader(ServerPlayer player, de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload payload) {
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
            player.sendSystemMessage(Component.literal(limitMessage).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!manager.restoreDisabledChunkloader(payload.chunkX(), payload.chunkZ(), dimension)) {
            String failure = config.getActivationLimitMessage(entry.ownerUuid(), entry.ownerName());
            player.sendSystemMessage(Component.literal(failure != null
                    ? failure : "Could not restore this player.").withStyle(ChatFormatting.RED), false);
        }
        handleDisabledChunkloadersListRequest(player);
    }

    private static void handleUpdateDisabledChunkloaderCoords(ServerPlayer player, de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload payload) {
        if (!PermissionManager.canUse(player)) {
            CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't have permission to update disabled player coordinates."), PacketDistributor.PLAYER.with(player));
            return;
        }
        if (isOnActionCooldown(player)) {
            return;
        }
        markActionCooldown(player);

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(false, "Config not available."), PacketDistributor.PLAYER.with(player));
            return;
        }
        String oldDimension = payload.oldDimension() != null && !payload.oldDimension().isBlank()
                ? payload.oldDimension() : "minecraft:overworld";
        var entry = config.getEntry(payload.oldChunkX(), payload.oldChunkZ(), oldDimension);
        if (entry == null) {
            CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player not found."), PacketDistributor.PLAYER.with(player));
            return;
        }
        if (!PermissionManager.canOperateEntry(player, entry)) {
            CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(false, "You don't own this player."), PacketDistributor.PLAYER.with(player));
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(false, "Player manager not available."), PacketDistributor.PLAYER.with(player));
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
        boolean success = errorMessage == null;
        CHANNEL.send(new de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload(success, success ? "Coordinates updated successfully." : errorMessage), PacketDistributor.PLAYER.with(player));
        if (success) {
            refreshOpenChunkMapMarkers(player.level().getServer(), manager);
            var moved = config.getEntry(payload.newChunkX(), payload.newChunkZ(), newDimension);
            if (moved != null && moved.enabled()) {
                handleActiveChunkloadersListRequest(player);
            } else {
                handleDisabledChunkloadersListRequest(player);
            }
        }
    }
}
