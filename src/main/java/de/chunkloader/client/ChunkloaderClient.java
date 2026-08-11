package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.network.ChunkloaderNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ChunkloaderForgeMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChunkloaderClient {
    private static final Map<UUID, PendingEmote> pendingEmotes = new ConcurrentHashMap<>();

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
        event.enqueueWork(() -> CustomFakePlayerSkinCache.loadConfiguredSkins(ClientConfig.load()));
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

                }
            };
            if (client != null) {
                client.execute(apply);
            } else {
                apply.run();
            }
        });
        InputEvent.Key.BUS.addListener(KeyInputHandler::onKeyInput);
        ChunkloaderNetworking.registerClientHandlers(
            payload -> {
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
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    de.chunkloader.client.screen.ChunkMapSessionScreens.closeIfOpen(client);
                }
            },
            payload -> {
                FakePlayerVisibilityCache.setVisibility(payload.fakePlayerName(), payload.visible());
            },
            payload -> {
                FakePlayerEasterEggSkinCache.setSkinIndex(payload.playerUuid(), payload.skinIndex());
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (shouldPlayEasterEggEmote(client, payload.playerUuid())) {
                    FakePlayerEasterEggEmoteCache.startEmote(payload.playerUuid(), payload.startGameTime());
                } else {
                    queuePendingEmote(payload.playerUuid(), client, payload.startGameTime(), false);
                }
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    SimulationStatusHUD.updateStatus(payload);
                }
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    ChunkplayerStatusHUD.updateStatus(payload);
                }
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                    return;
                }

                net.minecraft.client.gui.screens.Screen currentScreen = client.screen;
                if (currentScreen instanceof de.chunkloader.client.screen.DisabledChunkloadersScreen existingScreen) {
                    existingScreen.updateDisabledChunkloaders(payload.disabledChunkloaders());
                } else if (currentScreen == null || currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen) {
                    net.minecraft.client.gui.screens.Screen parent = currentScreen instanceof de.chunkloader.client.screen.ChunkMapScreen ? currentScreen : null;
                    client.setScreen(new de.chunkloader.client.screen.DisabledChunkloadersScreen(payload.disabledChunkloaders(), parent));
                }
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.screen instanceof de.chunkloader.client.screen.EditDisabledChunkloaderCoordsScreen editScreen) {
                    editScreen.handleUpdateResponse(payload);
                }
            },
            payload -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.screen instanceof de.chunkloader.client.screen.RenameChunkloaderScreen renameScreen) {
                    renameScreen.handleRenameResponse(payload);
                }
            },
            payload -> CustomFakePlayerSkinCache.clearPersistedSkin(payload.playerName()),
            payload -> {
                try {
                    CustomFakePlayerSkinCache.applySyncedSkin(
                        payload.playerName(),
                        payload.pngBytes(),
                        payload.layerMask()
                    );
                } catch (Exception ignored) {
                }
            },
            payload -> {
                SimulationStatusHUD.forceUpdate();
                ChunkplayerStatusHUD.forceUpdate();
            }
        );
        startHUDUpdateTimer();
    }

    private static boolean shouldPlayEasterEggEmote(Minecraft client, java.util.UUID playerUuid) {
        if (client == null || client.level == null || client.player == null || playerUuid == null) {
            return false;
        }
        Player target = null;
        for (Player player : client.level.players()) {
            if (player != null && playerUuid.equals(player.getUUID())) {
                target = player;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        double maxDistance = 24.0;
        return client.player.distanceToSqr(target) <= (maxDistance * maxDistance);
    }

    static void processPendingEmotes(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        long now = client.level.getGameTime();
        for (var entry : pendingEmotes.entrySet()) {
            UUID uuid = entry.getKey();
            PendingEmote pending = entry.getValue();
            if (pending == null) {
                pendingEmotes.remove(uuid);
                continue;
            }
            if (now > pending.expireAtGameTime) {
                pendingEmotes.remove(uuid);
                continue;
            }
            Player target = findPlayer(client, uuid);
            if (target == null) {
                continue;
            }
            if (client.player.distanceToSqr(target) <= (24.0 * 24.0)) {
                if (!pending.immediate) {
                    FakePlayerEasterEggEmoteCache.startEmote(uuid, pending.startGameTime);
                }
            }
            pendingEmotes.remove(uuid);
        }
    }

    private static void queuePendingEmote(UUID playerUuid, Minecraft client, long startGameTime, boolean immediate) {
        if (playerUuid == null || client == null || client.level == null) {
            return;
        }
        long expireAt = client.level.getGameTime() + 100L;
        pendingEmotes.put(playerUuid, new PendingEmote(startGameTime, immediate, expireAt));
    }

    private static Player findPlayer(Minecraft client, UUID playerUuid) {
        if (client == null || client.level == null || playerUuid == null) {
            return null;
        }
        for (Player player : client.level.players()) {
            if (player != null && playerUuid.equals(player.getUUID())) {
                return player;
            }
        }
        return null;
    }

    private record PendingEmote(long startGameTime, boolean immediate, long expireAtGameTime) {}

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

        try {
            if (ChunkloaderKeyMappings.disabledChunkloadersKey != null) {
                String currentKeyName = ChunkloaderKeyMappings.disabledChunkloadersKey.getTranslatedKeyMessage().getString();
                if (currentKeyName != null && !currentKeyName.isEmpty() && !currentKeyName.equals(de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName())) {
                    de.chunkloader.util.KeybindHelper.setDisabledChunkloadersKeyName(currentKeyName);
                    ClientConfig config = ClientConfig.load();
                    config.setDisabledChunkloadersKeyName(currentKeyName);
                    de.chunkloader.util.KeybindHelper.setDisabledChunkloadersKeyName(currentKeyName);
                }
            }
        } catch (Exception ignored) {
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
                if (client == null) {
                    return;
                }
                client.execute(() -> CustomFakePlayerSkinCache.refreshBindings());
                if (client.player == null || client.screen != null) {
                    return;
                }
                client.execute(() -> handleKeyToggles(Minecraft.getInstance()));
                processPendingEmotes(client);

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

