package de.chunkloader.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

public final class EntitySyncUtil {
    
    private EntitySyncUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    public static void syncMetadataImmediately(@NotNull ServerWorld world, @NotNull Entity entity) {
        if (world == null) {
            throw new IllegalArgumentException("ServerWorld cannot be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        if (!entity.isAlive() || entity.getEntityWorld() != world) {
            return;
        }
        
        try {
            var dataTracker = entity.getDataTracker();
            
            var changedEntries = dataTracker.getChangedEntries();
            
            if (changedEntries == null || changedEntries.isEmpty()) {
                var customName = entity.getCustomName();
                var customNameVisible = entity.isCustomNameVisible();
                
                entity.setCustomName(net.minecraft.text.Text.literal(""));
                entity.setCustomNameVisible(!customNameVisible);
                
                entity.setCustomName(customName);
                entity.setCustomNameVisible(customNameVisible);
                
                changedEntries = dataTracker.getChangedEntries();
                
                if (changedEntries == null || changedEntries.isEmpty()) {
                    try {
                        java.lang.reflect.Method getAllEntriesMethod = dataTracker.getClass().getMethod("getAllEntries");
                        @SuppressWarnings("unchecked")
                        java.util.List<net.minecraft.entity.data.DataTracker.Entry<?>> allEntries = 
                            (java.util.List<net.minecraft.entity.data.DataTracker.Entry<?>>) getAllEntriesMethod.invoke(dataTracker);
                        
                        if (allEntries != null && !allEntries.isEmpty()) {
                            java.util.List<net.minecraft.entity.data.DataTracker.SerializedEntry<?>> serializedEntries = new java.util.ArrayList<>();
                            
                            for (net.minecraft.entity.data.DataTracker.Entry<?> entry : allEntries) {
                                try {
                                    java.lang.reflect.Method serializeMethod = entry.getClass().getMethod("toSerialized");
                                    net.minecraft.entity.data.DataTracker.SerializedEntry<?> serialized = 
                                        (net.minecraft.entity.data.DataTracker.SerializedEntry<?>) serializeMethod.invoke(entry);
                                    
                                    if (serialized != null) {
                                        serializedEntries.add(serialized);
                                    }
                                } catch (Exception e) {
                                }
                            }
                            
                            if (!serializedEntries.isEmpty()) {
                                changedEntries = serializedEntries;
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                
                if (changedEntries == null || changedEntries.isEmpty()) {
                    return;
                }
            }
            
            
            EntityTrackerUpdateS2CPacket packet = new EntityTrackerUpdateS2CPacket(
                entity.getId(),
                changedEntries
            );
            
            try {
                var chunkManager = world.getChunkManager();
                java.lang.reflect.Field entityTrackerField = chunkManager.getClass().getDeclaredField("entityTracker");
                entityTrackerField.setAccessible(true);
                var entityTracker = entityTrackerField.get(chunkManager);
                
                java.lang.reflect.Method getTrackingPlayersMethod = entityTracker.getClass()
                    .getMethod("getTrackingPlayers", Entity.class);
                @SuppressWarnings("unchecked")
                java.util.Set<ServerPlayerEntity> trackingPlayers = 
                    (java.util.Set<ServerPlayerEntity>) getTrackingPlayersMethod.invoke(entityTracker, entity);
                
                if (trackingPlayers != null && !trackingPlayers.isEmpty()) {
                    for (ServerPlayerEntity player : trackingPlayers) {
                        if (player != null && player.networkHandler != null) {
                            player.networkHandler.sendPacket(packet);
                        }
                    }
                    return;
                }
            } catch (Exception e) {
            }
            
            double maxDistance = 64.0;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player != null && player.networkHandler != null) {
                    double distance = player.squaredDistanceTo(entity);
                    if (distance <= maxDistance * maxDistance) {
                        player.networkHandler.sendPacket(packet);
                    }
                }
            }
            
        } catch (Exception e) {
            de.chunkloader.ChunkloaderMod.LOGGER.warn(
                "Failed to sync entity metadata for entity {}: {}", 
                entity.getUuid(), 
                e.getMessage()
            );
        }
    }
    
    public static void syncMetadataImmediately(@NotNull ServerWorld world, @NotNull LivingEntity entity) {
        syncMetadataImmediately(world, (Entity) entity);
    }
}

