package de.chunkloader.client;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.client.screen.ChunkMapSessionScreens;
import de.chunkloader.client.screen.ActiveChunkloadersScreen;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen;
import de.chunkloader.client.screen.ChunkMapHelpScreen;
import de.chunkloader.util.KeybindHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class ChunkloaderClient implements ClientModInitializer {

    public static KeyBinding simulationStatusHUDToggleKey;
    public static KeyBinding chunkplayerStatusHUDToggleKey;
    public static KeyBinding disabledChunkloadersKey;
    public static KeyBinding activeChunkloadersKey;

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;

    private static long lastSimulationStatusRequest = 0;
    private static long lastChunkplayerStatusRequest = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 200;

    static void resetHudState() {
        SimulationStatusHUD.resetStatus();
        ChunkplayerStatusHUD.resetStatus();
        lastSimulationStatusRequest = 0;
        lastChunkplayerStatusRequest = 0;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

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
        ChunkloaderNetworking.setSkinChangeResultClientHook(
            de.chunkloader.client.screen.ChunkplayerSkinScreen::receiveSkinChangeResult);
        ChunkloaderNetworking.setClearCustomSkinClientHook(playerName -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> CustomFakePlayerSkinCache.clearPersistedSkin(playerName));
            } else {
                CustomFakePlayerSkinCache.clearPersistedSkin(playerName);
            }
        });
        ChunkloaderNetworking.setSyncCustomSkinClientHook(payload -> {
            MinecraftClient client = MinecraftClient.getInstance();
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

        simulationStatusHUDToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.simulation_status_hud_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.categories.misc"));

        chunkplayerStatusHUDToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.chunkplayer_status_hud_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "key.categories.misc"));

        activeChunkloadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.active_chunkloaders",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "key.categories.misc"));

        disabledChunkloadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.disabled_chunkloaders",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.misc"));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetHudState();
            FakePlayerNameCache.clear();
            FakePlayerVisibilityCache.clear();
            FakePlayerEasterEggSkinCache.clear();
            FakePlayerEasterEggEmoteCache.clear();
            CustomFakePlayerSkinCache.clearAllSkins();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetHudState();
            CustomFakePlayerSkinCache.clearAllSkins();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CustomFakePlayerSkinCache.refreshBindings();

            if (disabledChunkloadersKey != null && client.player != null) {
                try {
                    String currentKeyName = disabledChunkloadersKey.getBoundKeyLocalizedText().getString();
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

                if (simulationStatusHUDToggleKey.wasPressed()) {
                    if (client.currentScreen == null) {
                        SimulationStatusHUD.toggle();
                        if (SimulationStatusHUD.isEnabled()) {
                            lastSimulationStatusRequest = 0;
                            lastChunkX = client.player.getChunkPos().x;
                            lastChunkZ = client.player.getChunkPos().z;
                            if ((now - lastSimulationStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                                SimulationStatusHUD.setRequestPending(true);
                                ChunkloaderNetworking.requestSimulationStatus();
                                lastSimulationStatusRequest = now;
                            }
                        }
                    }
                }

                if (chunkplayerStatusHUDToggleKey.wasPressed()) {
                    ChunkplayerStatusHUD.toggle();
                    if (ChunkplayerStatusHUD.isEnabled()) {
                        lastChunkplayerStatusRequest = 0;
                        lastChunkX = client.player.getChunkPos().x;
                        lastChunkZ = client.player.getChunkPos().z;
                        if ((now - lastChunkplayerStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                            ChunkplayerStatusHUD.setRequestPending(true);
                            ChunkloaderNetworking.requestChunkplayerStatus();
                            lastChunkplayerStatusRequest = now;
                        }
                    }
                }

                if (activeChunkloadersKey != null && activeChunkloadersKey.wasPressed()) {
                    if (client.currentScreen == null) {
                        ChunkloaderNetworking.requestActiveChunkloadersList();
                    } else if (client.currentScreen instanceof ActiveChunkloadersScreen) {
                        client.setScreen(null);
                    }
                }

                if (disabledChunkloadersKey.wasPressed()) {
                    if (client.currentScreen == null) {
                        ChunkloaderNetworking.requestDisabledChunkloadersList();
                    } else {
                        Screen currentScreen = client.currentScreen;
                        if (currentScreen instanceof DisabledChunkloadersScreen) {
                            client.setScreen(null);
                        } else if (currentScreen instanceof EditDisabledChunkloaderCoordsScreen editScreen) {
                            client.setScreen(editScreen.getParent());
                        }
                    }
                }

                if (client.currentScreen == null && client.player != null) {
                    int currentChunkX = client.player.getChunkPos().x;
                    int currentChunkZ = client.player.getChunkPos().z;

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

        ClientPlayNetworking.registerGlobalReceiver(FakePlayerVisibilityPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EasterEggSkinPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerEasterEggSkinCache.setSkinIndex(payload.playerUuid(), payload.skinIndex());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EasterEggEmotePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                FakePlayerEasterEggEmoteCache.startEmote(payload.playerUuid(), payload.startGameTime());
            });
        });

        ClientPlayNetworking.PlayPayloadHandler<OpenChunkMapPayload> openChunkMapHandler = (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }
                var data = payload.data();
                var currentScreen = context.client().currentScreen;
                ChunkMapScreen existingScreen = ChunkMapSessionScreens.findChunkMapScreen(currentScreen);
                if (existingScreen != null) {
                    existingScreen.updateData(data, payload.canModify(), payload.shared());
                    return;
                }
                Screen returnScreen = currentScreen instanceof ActiveChunkloadersScreen ? currentScreen : null;
                context.client().setScreen(new ChunkMapScreen(data, returnScreen, payload.canModify(), payload.shared()));
            });
        };
        ClientPlayNetworking.registerGlobalReceiver(OpenChunkMapPayload.ID, openChunkMapHandler);
        ClientPlayNetworking.registerGlobalReceiver(OpenChunkMapPayload.WITH_ACCESS, openChunkMapHandler);

        ClientPlayNetworking.registerGlobalReceiver(ChunkMapLoaderDetailsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChunkMapScreen screen = ChunkMapSessionScreens.findChunkMapScreen(context.client().currentScreen);
                if (screen != null) {
                    screen.updateLoaderDetails(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenHelpPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    if (ChunkMapSessionScreens.isInChunkMapSession(client.currentScreen)) {
                        ChunkMapSessionScreens.closeToGame(client);
                    }
                    client.setScreen(new ChunkMapHelpScreen(null));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CloseChunkMapPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    ChunkMapSessionScreens.closeIfOpen(context.client());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SimulationStatusResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    SimulationStatusHUD.updateStatus(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChunkplayerStatusResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    ChunkplayerStatusHUD.updateStatus(payload);
                }
            });
        });

        ClientPlayNetworking.PlayPayloadHandler<ActiveChunkloadersListPayload> activeChunkloadersListHandler = (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) return;
                Screen currentScreen = context.client().currentScreen;
                if (currentScreen instanceof ActiveChunkloadersScreen existing) {
                    existing.updateEntries(payload.entries());
                } else if (currentScreen == null || currentScreen instanceof EditDisabledChunkloaderCoordsScreen || currentScreen instanceof ChunkMapScreen || currentScreen instanceof DisabledChunkloadersScreen) {
                    Screen parent = null;
                    if (currentScreen instanceof ChunkMapScreen) {
                        parent = currentScreen;
                    } else if (currentScreen instanceof DisabledChunkloadersScreen disabled) {
                        parent = disabled.getParentScreen();
                    }
                    context.client().setScreen(new ActiveChunkloadersScreen(payload.entries(), parent));
                }
            });
        };
        ClientPlayNetworking.registerGlobalReceiver(ActiveChunkloadersListPayload.ID, activeChunkloadersListHandler);
        ClientPlayNetworking.registerGlobalReceiver(ActiveChunkloadersListPayload.WITH_SHARES, activeChunkloadersListHandler);

        ClientPlayNetworking.PlayPayloadHandler<DisabledChunkloadersListPayload> disabledChunkloadersListHandler = (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }

                Screen currentScreen = context.client().currentScreen;
                if (currentScreen instanceof DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null || currentScreen instanceof ChunkMapScreen || currentScreen instanceof ActiveChunkloadersScreen) {
                    Screen parent = null;
                    if (currentScreen instanceof ChunkMapScreen) {
                        parent = currentScreen;
                    } else if (currentScreen instanceof ActiveChunkloadersScreen active) {
                        parent = active.getParentScreen();
                    }
                    context.client().setScreen(new DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                }
            });
        };
        ClientPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListPayload.ID, disabledChunkloadersListHandler);
        ClientPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListPayload.WITH_SHARES, disabledChunkloadersListHandler);

        ClientPlayNetworking.registerGlobalReceiver(UpdateDisabledChunkloaderCoordsResponsePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (context.client().player != null && context
                                .client().currentScreen instanceof EditDisabledChunkloaderCoordsScreen editScreen) {
                            editScreen.handleUpdateResponse(payload);
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(RenameChunkloaderResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null && context
                        .client().currentScreen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(InvalidateCachePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                SimulationStatusHUD.forceUpdate();
                ChunkplayerStatusHUD.forceUpdate();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.chunkloader.network.payload.SkinChangeResultPayload.ID, (value, context) -> {
            context.client().execute(() -> ChunkloaderNetworking.handleSkinChangeResultClient(value));
        });

        ClientPlayNetworking.registerGlobalReceiver(ClearCustomSkinPayload.ID, (payload, context) -> {
            context.client().execute(() -> CustomFakePlayerSkinCache.clearPersistedSkin(payload.playerName()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncCustomSkinPayload.ID, (payload, context) -> {
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

    @SuppressWarnings("unused")
    private static boolean shouldPlayEasterEggEmote(MinecraftClient client, java.util.UUID playerUuid) {
        if (client == null || client.world == null || client.player == null || playerUuid == null) {
            return false;
        }
        PlayerEntity target = null;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player != null && playerUuid.equals(player.getUuid())) {
                target = player;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        double maxDistance = 24.0;
        return client.player.squaredDistanceTo(target) <= (maxDistance * maxDistance);
    }
}
