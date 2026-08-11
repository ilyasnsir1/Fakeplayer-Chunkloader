package de.chunkloader.fakeplayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SyntheticPlayerContext {
    private static final ThreadLocal<Integer> SPAWNING_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

    private SyntheticPlayerContext() {
    }

    public static void enterSpawn() {
        SPAWNING_DEPTH.set(SPAWNING_DEPTH.get() + 1);
    }

    public static void exitSpawn() {
        int depth = SPAWNING_DEPTH.get() - 1;
        if (depth <= 0) {
            SPAWNING_DEPTH.remove();
        } else {
            SPAWNING_DEPTH.set(depth);
        }
    }

    public static boolean isSpawning() {
        Integer depth = SPAWNING_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void mark(ServerPlayerEntity player) {
        if (player != null) {
            MARKED.add(player.getUuid());
        }
    }

    public static void unmark(UUID uuid) {
        if (uuid != null) {
            MARKED.remove(uuid);
        }
    }

    public static boolean isMarked(Entity entity) {
        return entity != null && MARKED.contains(entity.getUuid());
    }
}
