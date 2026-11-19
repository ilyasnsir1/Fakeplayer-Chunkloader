package de.chunkloader.client;
import de.chunkloader.client.screen.ChunkMapHelpScreen;

import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.OpenHelpPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.EasterEggEmotePayload;
import de.chunkloader.network.payload.EasterEggSkinPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.InvalidateCachePayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.ChunkMapLoaderDetailsPayload;
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
                var currentScreen = client.screen;
                de.chunkloader.client.screen.ChunkMapScreen existingScreen =
                    de.chunkloader.client.screen.ChunkMapSessionScreens.findChunkMapScreen(currentScreen);
                if (existingScreen != null) {
                    existingScreen.updateData(data, payload.canModify(), payload.shared());
                    return;
                }
                net.minecraft.client.gui.screens.Screen returnScreen =
                    currentScreen instanceof de.chunkloader.client.screen.ActiveChunkloadersScreen ? currentScreen : null;
                client.setScreen(new de.chunkloader.client.screen.ChunkMapScreen(data, returnScreen, payload.canModify(), payload.shared()));
            });
        });

        registrar.optional().playToClient(ChunkMapLoaderDetailsPayload.TYPE, ChunkMapLoaderDetailsPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                var screen = de.chunkloader.client.screen.ChunkMapSessionScreens.findChunkMapScreen(client.screen);
                if (screen != null) {
                    screen.updateLoaderDetails(payload);
                }
            });
        });

        registrar.optional().playToClient(OpenHelpPayload.TYPE, OpenHelpPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    if (de.chunkloader.client.screen.ChunkMapSessionScreens.isInChunkMapSession(client.screen)) {
                        de.chunkloader.client.screen.ChunkMapSessionScreens.closeToGame(client);
                    }
                    client.setScreen(new ChunkMapHelpScreen(null));
                }
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
                        || currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen
                        || currentScreen instanceof de.chunkloader.client.screen.ActiveChunkloadersScreen) {
                    net.minecraft.client.gui.screens.Screen parent = null;
                    if (currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                        parent = currentScreen;
                    } else if (currentScreen instanceof de.chunkloader.client.screen.ActiveChunkloadersScreen active) {
                        parent = active.getParentScreen();
                    }
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
