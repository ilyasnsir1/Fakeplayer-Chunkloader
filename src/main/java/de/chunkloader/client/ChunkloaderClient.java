package de.chunkloader.client;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.client.screen.ChunkMapSessionScreens;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen;
import de.chunkloader.util.KeybindHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class ChunkloaderClient implements ClientModInitializer {

    public static KeyMapping simulationStatusHUDToggleKey;
    public static KeyMapping chunkplayerStatusHUDToggleKey;
    public static KeyMapping disabledChunkloadersKey;

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;

    private static long lastSimulationStatusRequest = 0;
    private static long lastChunkplayerStatusRequest = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 200;

    private static void requestHUDStatusIfNeeded(boolean isEnabled, boolean needsUpdate, Runnable requestAction,
            long lastRequestTime, long currentTime, java.util.function.LongConsumer setLastRequestTime) {
        if (isEnabled && needsUpdate && (currentTime - lastRequestTime) >= MIN_REQUEST_INTERVAL_MS) {
            requestAction.run();
            setLastRequestTime.accept(currentTime);
        }
    }

    @Override
    @SuppressWarnings("all")
    public void onInitializeClient() {
        ChunkloaderNetworking.setClearCustomSkinClientHook(playerName -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> CustomFakePlayerSkinCache.clearPersistedSkin(playerName));
            } else {
                CustomFakePlayerSkinCache.clearPersistedSkin(playerName);
            }
        });
        ChunkloaderNetworking.setSyncCustomSkinClientHook(payload -> {
            Minecraft client = Minecraft.getInstance();
            Runnable apply = () -> {
                try {
                    CustomFakePlayerSkinCache.applySyncedSkin(
                        payload.playerName(),
                        payload.pngBytes(),
                        payload.layerMask()
                    );
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.warn("Failed to apply synced custom skin '{}': {}",
                        payload.playerName(), e.getMessage());
                }
            };
            if (client != null) {
                client.execute(apply);
            } else {
                apply.run();
            }
        });

        simulationStatusHUDToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chunkloader.simulation_status_hud_toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                Category.MISC));

        chunkplayerStatusHUDToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chunkloader.chunkplayer_status_hud_toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                Category.MISC));

        disabledChunkloadersKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chunkloader.disabled_chunkloaders",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                Category.MISC));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FakePlayerNameCache.clear();
            FakePlayerVisibilityCache.clear();
            FakePlayerEasterEggSkinCache.clear();
            FakePlayerEasterEggEmoteCache.clear();
            CustomFakePlayerSkinCache.clearAllSkins();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            CustomFakePlayerSkinCache.clearAllSkins()
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CustomFakePlayerSkinCache.refreshBindings();

            if (disabledChunkloadersKey != null && client.player != null) {
                try {
                    String currentKeyName = disabledChunkloadersKey.getTranslatedKeyMessage().getString();
                    if (currentKeyName != null && !currentKeyName.isEmpty()
                            && !currentKeyName.equals(KeybindHelper.getDisabledChunkloadersKeyName())) {
                        KeybindHelper.setDisabledChunkloadersKeyName(currentKeyName);
                        ClientConfig config = ClientConfig.load();
                        config.setDisabledChunkloadersKeyName(currentKeyName);
                        KeybindHelper.setDisabledChunkloadersKeyName(currentKeyName);
                    }
                } catch (Exception e) {
                }
            }
            if (client.player != null) {
                long now = System.currentTimeMillis();

                if (simulationStatusHUDToggleKey.consumeClick()) {
                    if (client.gui.screen() == null) {
                        SimulationStatusHUD.toggle();
                        if (SimulationStatusHUD.isEnabled()) {
                            lastChunkX = client.player.chunkPosition().x();
                            lastChunkZ = client.player.chunkPosition().z();
                            if ((now - lastSimulationStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                                ChunkloaderNetworking.requestSimulationStatus();
                                lastSimulationStatusRequest = now;
                            }
                        }
                    }
                }

                if (chunkplayerStatusHUDToggleKey.consumeClick()) {
                    ChunkplayerStatusHUD.toggle();
                    if (ChunkplayerStatusHUD.isEnabled() && client.player != null) {
                        lastChunkX = client.player.chunkPosition().x();
                        lastChunkZ = client.player.chunkPosition().z();
                        if ((now - lastChunkplayerStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                            ChunkloaderNetworking.requestChunkplayerStatus();
                            lastChunkplayerStatusRequest = now;
                        }
                    }
                }

                if (disabledChunkloadersKey != null && disabledChunkloadersKey.consumeClick()) {
                    if (client.gui.screen() == null) {
                        ChunkloaderNetworking.requestDisabledChunkloadersList();
                    } else {
                        Screen currentScreen = client.gui.screen();
                        if (currentScreen instanceof DisabledChunkloadersScreen) {
                            client.gui.setScreen(null);
                        } else if (currentScreen instanceof EditDisabledChunkloaderCoordsScreen editScreen) {
                            client.gui.setScreen(editScreen.getParent());
                        }
                    }
                }

                if (client.gui.screen() == null && client.player != null) {
                    int currentChunkX = client.player.chunkPosition().x();
                    int currentChunkZ = client.player.chunkPosition().z();

                    boolean chunkChanged = (lastChunkX != currentChunkX || lastChunkZ != currentChunkZ);

                    if (chunkChanged) {
                        lastChunkX = currentChunkX;
                        lastChunkZ = currentChunkZ;

                        requestHUDStatusIfNeeded(
                                SimulationStatusHUD.isEnabled(),
                                true,
                                () -> {
                                    SimulationStatusHUD.setRequestPending(true);
                                    ChunkloaderNetworking.requestSimulationStatus();
                                },
                                lastSimulationStatusRequest,
                                now,
                                time -> lastSimulationStatusRequest = time);
                        requestHUDStatusIfNeeded(
                                ChunkplayerStatusHUD.isEnabled(),
                                true,
                                () -> {
                                    ChunkplayerStatusHUD.setRequestPending(true);
                                    ChunkloaderNetworking.requestChunkplayerStatus();
                                },
                                lastChunkplayerStatusRequest,
                                now,
                                time -> lastChunkplayerStatusRequest = time);
                    }

                    if (SimulationStatusHUD.isEnabled() && SimulationStatusHUD.needsUpdate()) {
                        if ((now - lastSimulationStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                            SimulationStatusHUD.setRequestPending(true);
                            ChunkloaderNetworking.requestSimulationStatus();
                            lastSimulationStatusRequest = now;
                        }
                    }
                    if (ChunkplayerStatusHUD.isEnabled() && ChunkplayerStatusHUD.needsUpdate()) {
                        if ((now - lastChunkplayerStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                            ChunkplayerStatusHUD.setRequestPending(true);
                            ChunkloaderNetworking.requestChunkplayerStatus();
                            lastChunkplayerStatusRequest = now;
                        }
                    }
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FakePlayerVisibilityPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EasterEggSkinPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerEasterEggSkinCache.setSkinIndex(payload.playerUuid(), payload.skinIndex());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EasterEggEmotePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerEasterEggEmoteCache.startEmote(payload.playerUuid(), payload.startGameTime());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenChunkMapPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }
                var data = payload.data();
                ChunkMapScreen existingScreen = ChunkMapSessionScreens.findChunkMapScreen(context.client().gui.screen());
                if (existingScreen != null) {
                    existingScreen.updateData(data);
                    return;
                }
                context.client().gui.setScreen(new ChunkMapScreen(data));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CloseChunkMapPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    ChunkMapSessionScreens.closeIfOpen(context.client());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SimulationStatusResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    SimulationStatusHUD.updateStatus(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChunkplayerStatusResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    ChunkplayerStatusHUD.updateStatus(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }

                Screen currentScreen = context.client().gui.screen();
                if (currentScreen instanceof DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null || currentScreen instanceof ChunkMapScreen) {
                    Screen parent = currentScreen instanceof ChunkMapScreen ? currentScreen : null;
                    context.client().gui.setScreen(new DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UpdateDisabledChunkloaderCoordsResponsePayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (context.client().player != null && context
                                .client().gui.screen() instanceof EditDisabledChunkloaderCoordsScreen editScreen) {
                            editScreen.handleUpdateResponse(payload);
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(RenameChunkloaderResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null && context
                        .client().gui.screen() instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(InvalidateCachePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                SimulationStatusHUD.forceUpdate();
                ChunkplayerStatusHUD.forceUpdate();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClearCustomSkinPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> CustomFakePlayerSkinCache.clearPersistedSkin(payload.playerName()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncCustomSkinPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    CustomFakePlayerSkinCache.applySyncedSkin(
                        payload.playerName(),
                        payload.pngBytes(),
                        payload.layerMask()
                    );
                } catch (Exception e) {
                    ChunkloaderMod.LOGGER.warn("Failed to apply synced custom skin '{}': {}",
                        payload.playerName(), e.getMessage());
                }
            });
        });
    }
}