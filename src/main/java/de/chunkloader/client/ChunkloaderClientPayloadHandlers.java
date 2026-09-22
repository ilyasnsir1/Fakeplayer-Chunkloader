package de.chunkloader.client;

import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.EasterEggEmotePayload;
import de.chunkloader.network.payload.EasterEggSkinPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.InvalidateCachePayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import de.chunkloader.network.payload.SyncCustomSkinPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChunkloaderClientPayloadHandlers {
    private ChunkloaderClientPayloadHandlers() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(OpenChunkMapPayload.TYPE, OpenChunkMapPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                    return;
                }
                var data = payload.data();
                de.chunkloader.client.screen.ChunkMapScreen existingScreen =
                    de.chunkloader.client.screen.ChunkMapSessionScreens.findChunkMapScreen(client.screen);
                if (existingScreen != null) {
                    existingScreen.updateData(data);
                    return;
                }
                client.setScreen(new de.chunkloader.client.screen.ChunkMapScreen(data));
            });
        });

        registrar.playToClient(CloseChunkMapPayload.TYPE, CloseChunkMapPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                    return;
                }
                de.chunkloader.client.screen.ChunkMapSessionScreens.closeIfOpen(client);
            });
        });

        registrar.playToClient(FakePlayerVisibilityPayload.TYPE, FakePlayerVisibilityPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible()));
        });

        registrar.playToClient(EasterEggSkinPayload.TYPE, EasterEggSkinPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> FakePlayerEasterEggSkinCache.setSkinIndex(payload.playerUuid(), payload.skinIndex()));
        });

        registrar.playToClient(EasterEggEmotePayload.TYPE, EasterEggEmotePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (ChunkloaderClient.shouldPlayEasterEggEmote(client, payload.playerUuid())) {
                    FakePlayerEasterEggEmoteCache.startEmote(payload.playerUuid(), payload.startGameTime());
                } else {
                    ChunkloaderClient.queuePendingEmote(payload.playerUuid(), client, payload.startGameTime(), false);
                }
            });
        });

        registrar.playToClient(SimulationStatusResponsePayload.TYPE, SimulationStatusResponsePayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> de.chunkloader.client.hud.SimulationStatusHUD.updateStatus(payload)));

        registrar.playToClient(ChunkplayerStatusResponsePayload.TYPE, ChunkplayerStatusResponsePayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> de.chunkloader.client.hud.ChunkplayerStatusHUD.updateStatus(payload)));

        registrar.playToClient(DisabledChunkloadersListPayload.TYPE, DisabledChunkloadersListPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                    return;
                }

                var currentScreen = client.screen;
                if (currentScreen instanceof de.chunkloader.client.screen.DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null
                        || currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                    var parent = currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen ? currentScreen
                            : null;
                    client.setScreen(new de.chunkloader.client.screen.DisabledChunkloadersScreen(
                            payload.disabledChunkloaders(), parent));
                }
            });
        });

        registrar.playToClient(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE,
            UpdateDisabledChunkloaderCoordsResponsePayload.STREAM_CODEC, (payload, ctx) -> {
                ctx.enqueueWork(() -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null
                            && client.screen instanceof de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen editScreen) {
                        editScreen.handleUpdateResponse(payload);
                    }
                });
            });

        registrar.playToClient(RenameChunkloaderResponsePayload.TYPE, RenameChunkloaderResponsePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null
                        && client.screen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });

        registrar.playToClient(InvalidateCachePayload.TYPE, InvalidateCachePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                de.chunkloader.client.hud.SimulationStatusHUD.forceUpdate();
                de.chunkloader.client.hud.ChunkplayerStatusHUD.forceUpdate();
            });
        });

        registrar.playToClient(SyncCustomSkinPayload.TYPE, SyncCustomSkinPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                try {
                    CustomFakePlayerSkinCache.applySyncedSkin(
                        payload.playerName(),
                        payload.pngBytes(),
                        payload.layerMask()
                    );
                } catch (Exception ignored) {
                }
            });
        });
    }
}
