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
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class ChunkloaderClient implements ClientModInitializer {

    public static KeyBinding simulationStatusHUDToggleKey;
    public static KeyBinding chunkplayerStatusHUDToggleKey;
    public static KeyBinding disabledChunkloadersKey;

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
                Category.MISC));

        chunkplayerStatusHUDToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.chunkplayer_status_hud_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                Category.MISC));

        disabledChunkloadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkloader.disabled_chunkloaders",
                InputUtil.Type.KEYSYM,
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
                            lastChunkX = client.player.getChunkPos().x;
                            lastChunkZ = client.player.getChunkPos().z;
                            if ((now - lastSimulationStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                                ChunkloaderNetworking.requestSimulationStatus();
                                lastSimulationStatusRequest = now;
                            }
                        }
                    }
                }

                if (chunkplayerStatusHUDToggleKey.wasPressed()) {
                    ChunkplayerStatusHUD.toggle();
                    if (ChunkplayerStatusHUD.isEnabled()) {
                        lastChunkX = client.player.getChunkPos().x;
                        lastChunkZ = client.player.getChunkPos().z;
                        if ((now - lastChunkplayerStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                            ChunkloaderNetworking.requestChunkplayerStatus();
                            lastChunkplayerStatusRequest = now;
                        }
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

        ClientPlayNetworking.registerGlobalReceiver(OpenChunkMapPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }
                var data = payload.data();
                ChunkMapScreen existingScreen = ChunkMapSessionScreens.findChunkMapScreen(context.client().currentScreen);
                if (existingScreen != null) {
                    existingScreen.updateData(data);
                    return;
                }
                context.client().setScreen(new ChunkMapScreen(data));
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

        ClientPlayNetworking.registerGlobalReceiver(DisabledChunkloadersListPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }

                Screen currentScreen = context.client().currentScreen;
                if (currentScreen instanceof DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null || currentScreen instanceof ChunkMapScreen) {
                    Screen parent = currentScreen instanceof ChunkMapScreen ? currentScreen : null;
                    context.client().setScreen(new DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                }
            });
        });

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
