package de.chunkloader;

import de.chunkloader.commands.ChunkloaderCommand;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkloaderCommon implements ModInitializer {
    public static final String MOD_ID = "chunkloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChunkloaderManager chunkloaderManager;
    private static ChunkloaderConfig config;

    public static void setConfig(ChunkloaderConfig newConfig) {
        config = newConfig;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Chunkloader Mod for Minecraft 1.21.10");

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
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey().getValue().toString().equals("minecraft:overworld")) {
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

        LOGGER.info("Chunkloader Mod initialized successfully - Commands registered");
    }

    public static ChunkloaderManager getChunkloaderManager() {
        return chunkloaderManager;
    }

    public static ChunkloaderConfig getConfig() {
        return config;
    }
}

