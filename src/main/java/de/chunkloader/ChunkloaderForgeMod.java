package de.chunkloader;

import de.chunkloader.commands.ChunkloaderCommand;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.ChunkloaderNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.eclipse.jdt.annotation.Nullable;

@Mod(ChunkloaderForgeMod.MODID)
public class ChunkloaderForgeMod {
    public static final String MODID = "chunkloader";
    
    private static @Nullable ChunkloaderManager chunkloaderManager;
    private static @Nullable ChunkloaderConfig config;
    private java.util.Timer serverTickTimer;
    
    public ChunkloaderForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ChunkloaderNetworking::registerPayloadHandlers);
        NeoForge.EVENT_BUS.register(this);
    }
    
    public void commonSetup(final FMLCommonSetupEvent event) {
        PermissionManager.init();
    }
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        config = ChunkloaderConfig.load(event.getServer());
        PermissionManager.initConfig(event.getServer());
        chunkloaderManager = new ChunkloaderManager(event.getServer(), config);
    }
    
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (chunkloaderManager != null) {
            chunkloaderManager.loadPersistentChunkloaders();
        }
        if (serverTickTimer == null) {
            final net.minecraft.server.MinecraftServer server = event.getServer();
            serverTickTimer = new java.util.Timer("ChunkloaderServerTick", true);
            serverTickTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (chunkloaderManager != null && !server.isStopped()) {
                        server.execute(chunkloaderManager::tick);
                    }
                }
            }, 50L, 50L);
        }
    }
    
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (serverTickTimer != null) {
            serverTickTimer.cancel();
            serverTickTimer = null;
        }
        if (chunkloaderManager != null) {
            chunkloaderManager.cleanup();
            chunkloaderManager.savePersistentChunkloaders();
        }
    }
    
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == Level.OVERWORLD) {
                if (chunkloaderManager != null) {
                    chunkloaderManager.loadPersistentChunkloaders();
                }
            }
        }
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChunkloaderCommand.register(event.getDispatcher(), event.getBuildContext());
    }
    
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            ChunkloaderNetworking.clearPlayerCache(event.getEntity());
        }
    }
    
    private static final Map<UUID, Long> lastPermissionMessageTime = new ConcurrentHashMap<>();
    private static final long PERMISSION_MESSAGE_COOLDOWN_MS = 500;
    
    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() == null || !(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        Entity target = event.getTarget();
        if (!(target instanceof ChunkloaderFakePlayer fakePlayer)) {
            return;
        }
        
        if (chunkloaderManager == null) {
            return;
        }
        
        if (!fakePlayer.isVisibleAsMarker()) {
            return;
        }
        
        UUID markerUuid = target.getUUID();
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            chunkloaderManager.ensureMarkerMapping(markerUuid, fakePlayer);
        }
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            return;
        }
        
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        
        try {
            var entry = chunkloaderManager.getEntryByMarkerUuid(markerUuid);
            String entityTypeName = (entry != null && entry.allowMobSpawning()) ? "fakeplayers" : "chunkplayers";
            
            if (!PermissionManager.canUse(serverPlayer)) {
                UUID playerUuid = serverPlayer.getUUID();
                long currentTime = System.currentTimeMillis();
                Long lastMessageTime = lastPermissionMessageTime.get(playerUuid);
                
                if (lastMessageTime == null || (currentTime - lastMessageTime) >= PERMISSION_MESSAGE_COOLDOWN_MS) {
                    serverPlayer.sendSystemMessage(Component.literal("You don't have permission to interact with " + entityTypeName + "."));
                    lastPermissionMessageTime.put(playerUuid, currentTime);
                }
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                return;
            }
            
            if (serverPlayer.isShiftKeyDown()) {
                var entryToDelete = chunkloaderManager.getEntryByMarkerUuid(markerUuid);
                if (entryToDelete != null) {
                    chunkloaderManager.removeChunkloader(entryToDelete.chunkX(), entryToDelete.chunkZ());
                    serverPlayer.sendSystemMessage(Component.literal("Chunkloader deleted"));
                }
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                return;
            }
            
            chunkloaderManager.openChunkMap(markerUuid, serverPlayer);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        } catch (Exception e) {
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof ChunkloaderFakePlayer fakePlayer)) {
            return;
        }

        if (chunkloaderManager == null) {
            return;
        }

        if (!fakePlayer.isVisibleAsMarker()) {
            return;
        }

        UUID markerUuid = target.getUUID();
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            chunkloaderManager.ensureMarkerMapping(markerUuid, fakePlayer);
        }
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            return;
        }

        try {
            var entry = chunkloaderManager.getEntryByMarkerUuid(markerUuid);
            String entityTypeName = (entry != null && entry.allowMobSpawning()) ? "fakeplayers" : "chunkplayers";

            if (!PermissionManager.canUse(serverPlayer)) {
                UUID playerUuid = serverPlayer.getUUID();
                long currentTime = System.currentTimeMillis();
                Long lastMessageTime = lastPermissionMessageTime.get(playerUuid);

                if (lastMessageTime == null || (currentTime - lastMessageTime) >= PERMISSION_MESSAGE_COOLDOWN_MS) {
                    serverPlayer.sendSystemMessage(Component.literal("You don't have permission to interact with " + entityTypeName + "."));
                    lastPermissionMessageTime.put(playerUuid, currentTime);
                }
                return;
            }

            if (serverPlayer.isShiftKeyDown()) {
                chunkloaderManager.removeChunkloaderByMarkerUuid(markerUuid);
            } else {
                chunkloaderManager.handleMarkerDestroyed(markerUuid);
            }

            try {
                fakePlayer.despawn();
            } catch (Exception ignored) {
            }
            fakePlayer.remove(Entity.RemovalReason.KILLED);
        } catch (Exception e) {
        }
    }
    
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ChunkloaderFakePlayer fakePlayer)) {
            return;
        }
        
        if (chunkloaderManager == null) {
            return;
        }
        
        if (!fakePlayer.isVisibleAsMarker()) {
            return;
        }
        
        UUID markerUuid = entity.getUUID();
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            chunkloaderManager.ensureMarkerMapping(markerUuid, fakePlayer);
        }
        if (!chunkloaderManager.isChunkloaderMarker(markerUuid)) {
            return;
        }
        
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) {
            event.setNewDamage(0.0f);
            return;
        }
        
        try {
            if (player.isShiftKeyDown()) {
                chunkloaderManager.removeChunkloaderByMarkerUuid(markerUuid);
            } else {
                chunkloaderManager.handleMarkerDestroyed(markerUuid);
            }
            
            try {
                fakePlayer.despawn();
            } catch (Exception ignored) {
            }
            entity.remove(Entity.RemovalReason.KILLED);
            
            event.setNewDamage(0.0f);
        } catch (Exception e) {
        }
    }
    
    @SubscribeEvent
    public void onEntityRemove(PlayerEvent.StopTracking event) {
        Entity entity = event.getEntity();
        if (entity instanceof ChunkloaderFakePlayer fakePlayer && chunkloaderManager != null) {
            if (fakePlayer.isVisibleAsMarker() && chunkloaderManager.isChunkloaderMarker(entity.getUUID())) {
                if (entity.isRemoved() && entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
                    chunkloaderManager.handleMarkerDestroyed(entity.getUUID());
                }
            }
        }
    }
    
    public static @Nullable ChunkloaderManager getChunkloaderManager() {
        return chunkloaderManager;
    }
    
    public static @Nullable ChunkloaderConfig getConfig() {
        return config;
    }
    
    public static void setConfig(ChunkloaderConfig newConfig) {
        config = newConfig;
    }
}