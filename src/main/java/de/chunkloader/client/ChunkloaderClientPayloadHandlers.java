package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.EasterEggEmotePayload;
import de.chunkloader.network.payload.EasterEggSkinPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import de.chunkloader.network.payload.InvalidateCachePayload;
import de.chunkloader.network.payload.ClearCustomSkinPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ChunkloaderForgeMod.MODID, value = Dist.CLIENT)
public final class ChunkloaderClientPayloadHandlers {
    private ChunkloaderClientPayloadHandlers() {
    }

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(OpenChunkMapPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null)
                    return;
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

        event.register(CloseChunkMapPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null)
                    return;
                de.chunkloader.client.screen.ChunkMapSessionScreens.closeIfOpen(client);
            });
        });

        event.register(FakePlayerVisibilityPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
            });
        });

        event.register(EasterEggSkinPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                FakePlayerEasterEggSkinCache.setSkinIndex(payload.playerUuid(), payload.skinIndex());
            });
        });

        event.register(EasterEggEmotePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (ChunkloaderClient.shouldPlayEasterEggEmote(client, payload.playerUuid())) {
                    FakePlayerEasterEggEmoteCache.startEmote(payload.playerUuid(), payload.startGameTime());
                } else {
                    ChunkloaderClient.queuePendingEmote(payload.playerUuid(), client, payload.startGameTime(), false);
                }
            });
        });

        event.register(SimulationStatusResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                de.chunkloader.client.hud.SimulationStatusHUD.updateStatus(payload);
            });
        });

        event.register(ChunkplayerStatusResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                de.chunkloader.client.hud.ChunkplayerStatusHUD.updateStatus(payload);
            });
        });

        event.register(DisabledChunkloadersListPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null)
                    return;

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

        event.register(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null
                        && client.screen instanceof de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen editScreen) {
                    editScreen.handleUpdateResponse(payload);
                }
            });
        });

        event.register(RenameChunkloaderResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null
                        && client.screen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });

        event.register(InvalidateCachePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                de.chunkloader.client.hud.SimulationStatusHUD.forceUpdate();
                de.chunkloader.client.hud.ChunkplayerStatusHUD.forceUpdate();
            });
        });

        event.register(ClearCustomSkinPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> CustomFakePlayerSkinCache.clearPersistedSkin(payload.playerName()));
        });

        event.register(de.chunkloader.network.payload.SyncCustomSkinPayload.TYPE, (payload, ctx) -> {
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
