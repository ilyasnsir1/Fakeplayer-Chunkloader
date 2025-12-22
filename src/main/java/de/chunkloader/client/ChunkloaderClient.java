package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.network.ChunkloaderNetworking;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ChunkloaderForgeMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChunkloaderClient {
    
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
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        InputEvent.Key.BUS.addListener(KeyInputHandler::onKeyInput);
        startHUDUpdateTimer();
    }
    
    
    private static void requestHUDStatusIfNeeded(boolean isEnabled, boolean needsUpdate, Runnable requestAction,
                                                 long lastRequestTime, long currentTime, java.util.function.LongConsumer setLastRequestTime) {
        if (isEnabled && needsUpdate && (currentTime - lastRequestTime) >= MIN_REQUEST_INTERVAL_MS) {
            requestAction.run();
            setLastRequestTime.accept(currentTime);
        }
    }

    static void handleKeyToggles(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (ChunkloaderKeyMappings.simulationStatusHUDToggleKey != null && ChunkloaderKeyMappings.simulationStatusHUDToggleKey.consumeClick()) {
            if (client.screen == null) {
                SimulationStatusHUD.toggle();
                if (SimulationStatusHUD.isEnabled()) {
                    lastChunkX = client.player.chunkPosition().x;
                    lastChunkZ = client.player.chunkPosition().z;
                    if ((now - lastSimulationStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                        ChunkloaderNetworking.requestSimulationStatus();
                        lastSimulationStatusRequest = now;
                    }
                }
            }
        }

        if (ChunkloaderKeyMappings.chunkplayerStatusHUDToggleKey != null && ChunkloaderKeyMappings.chunkplayerStatusHUDToggleKey.consumeClick()) {
            ChunkplayerStatusHUD.toggle();
            if (ChunkplayerStatusHUD.isEnabled() && client.player != null) {
                lastChunkX = client.player.chunkPosition().x;
                lastChunkZ = client.player.chunkPosition().z;
                if ((now - lastChunkplayerStatusRequest) >= MIN_REQUEST_INTERVAL_MS) {
                    ChunkloaderNetworking.requestChunkplayerStatus();
                    lastChunkplayerStatusRequest = now;
                }
            }
        }

        if (ChunkloaderKeyMappings.disabledChunkloadersKey != null && ChunkloaderKeyMappings.disabledChunkloadersKey.consumeClick()) {
            if (client.screen == null) {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            }
        }
    }

    private static class KeyInputHandler {
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) {
                return;
            }
            handleKeyToggles(client);
        }
    }
    
    private static void startHUDUpdateTimer() {
        new java.util.Timer().scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                Minecraft client = Minecraft.getInstance();
                if (client == null || client.player == null || client.screen != null) {
                    return;
                }
                client.execute(() -> handleKeyToggles(Minecraft.getInstance()));

                long now = System.currentTimeMillis();
                int currentChunkX = client.player.chunkPosition().x;
                int currentChunkZ = client.player.chunkPosition().z;
                
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
        }, 100, 100);
    }
}

