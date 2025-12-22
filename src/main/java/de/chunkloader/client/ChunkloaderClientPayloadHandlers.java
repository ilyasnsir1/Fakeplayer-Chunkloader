package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import de.chunkloader.network.payload.CloseChunkMapPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.FakePlayerVisibilityPayload;
import de.chunkloader.network.payload.OpenChunkMapPayload;
import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ChunkloaderForgeMod.MODID, value = Dist.CLIENT)
public final class ChunkloaderClientPayloadHandlers {
    private ChunkloaderClientPayloadHandlers() {}

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(OpenChunkMapPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return;
                var data = payload.data();

                if (client.screen instanceof de.chunkloader.client.screen.ChunkMapScreen existingScreen) {
                    existingScreen.updateData(data);
                } else {
                    client.setScreen(new de.chunkloader.client.screen.ChunkMapScreen(data));
                }
            });
        });

        event.register(CloseChunkMapPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return;
                if (client.screen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                    client.setScreen(null);
                }
            });
        });

        event.register(FakePlayerVisibilityPayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
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
                if (client.player == null) return;

                var currentScreen = client.screen;
                if (currentScreen instanceof de.chunkloader.client.screen.DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null || currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                    var parent = currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen ? currentScreen : null;
                    client.setScreen(new de.chunkloader.client.screen.DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                }
            });
        });

        event.register(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.screen instanceof de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen editScreen) {
                    editScreen.handleUpdateResponse(payload);
                }
            });
        });

        event.register(RenameChunkloaderResponsePayload.TYPE, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.screen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });
    }
}
