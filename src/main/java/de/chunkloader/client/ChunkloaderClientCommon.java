package de.chunkloader.client;

import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen;
import net.minecraft.client.gui.screen.Screen;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ChunkloaderClientCommon implements ClientModInitializer {
    
    private static KeyBinding simulationStatusHUDToggleKey;
    private static KeyBinding chunkplayerStatusHUDToggleKey;
    private static KeyBinding disabledChunkloadersKey;
    
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    
    private static double lastPlayerX = Double.NaN;
    private static double lastPlayerY = Double.NaN;
    private static double lastPlayerZ = Double.NaN;
    private static long lastMovementTime = 0;
    private static final long MOVEMENT_TIMEOUT_MS = 1000;
    private static final double MOVEMENT_THRESHOLD = 0.01;
    
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
    public void onInitializeClient() {
        simulationStatusHUDToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkloader.simulation_status_hud_toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            Category.MISC
        ));
        
        chunkplayerStatusHUDToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkloader.chunkplayer_status_hud_toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            Category.MISC
        ));
        
        disabledChunkloadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chunkloader.disabled_chunkloaders",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            Category.MISC
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                long now = System.currentTimeMillis();
                
                if (simulationStatusHUDToggleKey.wasPressed()) {
                    if (client.currentScreen == null) {
                    SimulationStatusHUD.toggle();
                    if (SimulationStatusHUD.isEnabled() && client.player != null) {
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
                    if (ChunkplayerStatusHUD.isEnabled() && client.player != null) {
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
                            time -> lastSimulationStatusRequest = time
                        );
                        requestHUDStatusIfNeeded(
                            ChunkplayerStatusHUD.isEnabled(),
                            true,
                            () -> {
                            ChunkplayerStatusHUD.setRequestPending(true);
                            ChunkloaderNetworking.requestChunkplayerStatus();
                            },
                            lastChunkplayerStatusRequest,
                            now,
                            time -> lastChunkplayerStatusRequest = time
                        );
                    }
                    
                    if (client.player != null) {
                    double currentX = client.player.getX();
                    double currentY = client.player.getY();
                    double currentZ = client.player.getZ();
                    
                    boolean isMoving = false;
                    if (!Double.isNaN(lastPlayerX)) {
                        double dx = Math.abs(currentX - lastPlayerX);
                        double dy = Math.abs(currentY - lastPlayerY);
                        double dz = Math.abs(currentZ - lastPlayerZ);
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        
                        if (distance > MOVEMENT_THRESHOLD) {
                            isMoving = true;
                            lastMovementTime = now;
                        }
                    }
                    
                    lastPlayerX = currentX;
                    lastPlayerY = currentY;
                    lastPlayerZ = currentZ;
                    
                    boolean shouldUpdate = isMoving || (now - lastMovementTime) < MOVEMENT_TIMEOUT_MS;
                    
                    if (shouldUpdate) {
                            requestHUDStatusIfNeeded(
                                SimulationStatusHUD.isEnabled(),
                                SimulationStatusHUD.needsUpdate(),
                                () -> {
                                SimulationStatusHUD.setRequestPending(true);
                                ChunkloaderNetworking.requestSimulationStatus();
                                },
                                lastSimulationStatusRequest,
                                now,
                                time -> lastSimulationStatusRequest = time
                            );
                            requestHUDStatusIfNeeded(
                                ChunkplayerStatusHUD.isEnabled(),
                                ChunkplayerStatusHUD.needsUpdate(),
                                () -> {
                                ChunkplayerStatusHUD.setRequestPending(true);
                                ChunkloaderNetworking.requestChunkplayerStatus();
                                },
                                lastChunkplayerStatusRequest,
                                now,
                                time -> lastChunkplayerStatusRequest = time
                            );
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
        
        ClientPlayNetworking.registerGlobalReceiver(OpenChunkMapPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    var data = payload.data();
                    
                    if (context.client().currentScreen instanceof ChunkMapScreen existingScreen) {
                        existingScreen.updateData(data);
                    } else {
                        context.client().setScreen(new ChunkMapScreen(data));
                    }
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(CloseChunkMapPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    if (context.client().currentScreen instanceof ChunkMapScreen) {
                        context.client().setScreen(null);
                    }
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
        
        ClientPlayNetworking.registerGlobalReceiver(UpdateDisabledChunkloaderCoordsResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null && context.client().currentScreen instanceof EditDisabledChunkloaderCoordsScreen editScreen) {
                    editScreen.handleUpdateResponse(payload);
                }
            });
        });
    }
}

