package de.chunkloader.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public final class EntitySyncUtil {

    private EntitySyncUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void syncMetadataImmediately(@NotNull ServerLevel world, @NotNull Entity entity) {
        if (world == null) {
            throw new IllegalArgumentException("ServerLevel cannot be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        if (!entity.isAlive() || entity.level() != world) {
            return;
        }

        try {
            var dataTracker = entity.getEntityData();

            var changedEntries = dataTracker.packDirty();

            if (changedEntries == null || changedEntries.isEmpty()) {
                var customName = entity.getCustomName();
                var customNameVisible = entity.isCustomNameVisible();

                entity.setCustomName(net.minecraft.network.chat.Component.literal(""));
                entity.setCustomNameVisible(!customNameVisible);

                entity.setCustomName(customName);
                entity.setCustomNameVisible(customNameVisible);

                changedEntries = dataTracker.packDirty();

                if (changedEntries == null || changedEntries.isEmpty()) {
                    var nonDefault = dataTracker.getNonDefaultValues();
                    if (nonDefault != null && !nonDefault.isEmpty()) {
                        changedEntries = nonDefault;
                    }
                }

                if (changedEntries == null || changedEntries.isEmpty()) {
                    return;
                }
            }

            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(
                entity.getId(),
                changedEntries
            );

            try {
                var chunkManager = world.getChunkSource();
                java.lang.reflect.Field entityTrackerField = chunkManager.getClass().getDeclaredField("entityTracker");
                entityTrackerField.setAccessible(true);
                var entityTracker = entityTrackerField.get(chunkManager);

                java.lang.reflect.Method getTrackingPlayersMethod = entityTracker.getClass()
                    .getMethod("getTrackingPlayers", Entity.class);
                @SuppressWarnings("unchecked")
                java.util.Set<ServerPlayer> trackingPlayers =
                    (java.util.Set<ServerPlayer>) getTrackingPlayersMethod.invoke(entityTracker, entity);

                if (trackingPlayers != null && !trackingPlayers.isEmpty()) {
                    for (ServerPlayer player : trackingPlayers) {
                        if (player != null && player.connection != null) {
                            player.connection.send(packet);
                        }
                    }
                    return;
                }
            } catch (Exception e) {
            }

            double maxDistance = 64.0;
            for (ServerPlayer player : world.players()) {
                if (player != null && player.connection != null) {
                    double distance = player.distanceToSqr(entity);
                    if (distance <= maxDistance * maxDistance) {
                        player.connection.send(packet);
                    }
                }
            }

        } catch (Exception e) {
            de.chunkloader.ChunkloaderMod.LOGGER.warn(
                "Failed to sync entity metadata for entity {}: {}",
                entity.getUUID(),
                e.getMessage()
            );
        }
    }

    public static void syncMetadataImmediately(@NotNull ServerLevel world, @NotNull LivingEntity entity) {
        syncMetadataImmediately(world, (Entity) entity);
    }
}

