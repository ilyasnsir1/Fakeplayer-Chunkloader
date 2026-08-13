package de.chunkloader;

import de.chunkloader.commands.ChunkloaderCommand;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkloaderMod implements ModInitializer {
    public static final String MOD_ID = "chunkloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChunkloaderManager chunkloaderManager;
    private static ChunkloaderConfig config;

    public static void setConfig(ChunkloaderConfig newConfig) {
        config = newConfig;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Chunkloader Mod for Minecraft 26.1.2");

        PermissionManager.init();

        ChunkloaderNetworking.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Server starting - Loading world-specific config");
            config = ChunkloaderConfig.load(server);
            PermissionManager.initConfig(server);
            LOGGER.info("Server starting - Initializing Chunkloader Manager");
            chunkloaderManager = new ChunkloaderManager(server, config);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Server started - Loading persistent chunkloaders");
            if (chunkloaderManager != null) {
                chunkloaderManager.loadPersistentChunkloaders();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping - Cleaning up chunkloaders");
            if (chunkloaderManager != null) {
                chunkloaderManager.cleanup();
                chunkloaderManager.savePersistentChunkloaders();
            }
            if (config != null) {
                config.flushPendingSave();
            }
        });

        ServerLevelEvents.LOAD.register((server, world) -> {
            if (world.dimension().identifier().toString().equals("minecraft:overworld")) {
                LOGGER.info("Overworld loaded - Checking if config needs to be reloaded");
                if (chunkloaderManager != null) {
                    chunkloaderManager.loadPersistentChunkloaders();
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (chunkloaderManager != null) {
                chunkloaderManager.tick();
            }
        });

        ChunkloaderCommand.register();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                ChunkloaderNetworking.clearPlayerCache(handler.player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player != null && chunkloaderManager != null) {
                if (handler.player instanceof ChunkloaderFakePlayer) {
                    return;
                }
                String playerName = handler.player.getName().getString();
                chunkloaderManager.checkAndRenameConflictingChunkloaders(playerName);
                chunkloaderManager.forceImmediateSync();
                chunkloaderManager.sendEasterEggSkinsToPlayer(handler.player);
                chunkloaderManager.sendFakePlayerVisibilitiesToPlayer(handler.player);
                chunkloaderManager.sendCustomSkinsToPlayer(handler.player);
                chunkloaderManager.sendEasterEggEmotesToPlayer(handler.player);
                chunkloaderManager.schedulePlayerJoinSync(handler.player, 5);
            }
        });

        LOGGER.info("Chunkloader Mod initialized successfully - Commands registered");
    }

    public static ChunkloaderManager getChunkloaderManager() {
        return chunkloaderManager;
    }

    public static ChunkloaderConfig getConfig() {
        return config;
    }
}
