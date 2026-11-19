package de.chunkloader.util;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.mixin.ServerChunkManagerAccessor;
import de.chunkloader.mixin.TicketStorageAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

public final class IndependentTicking {
    private static final int MAX_RADIUS = 32;
    private static final int OWN_TICKET_FLAG = 1 << 30;
    private static final int ENTITY_LIMIT = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int BLOCK_LIMIT = ENTITY_LIMIT + 1;
    private static final long REBUILD_INTERVAL_TICKS = 20L;

    private static final Map<ServerLevel, Coverage> CACHE = new WeakHashMap<>();

    private IndependentTicking() {
    }

    public static boolean isBlockTicking(ServerLevel level, int chunkX, int chunkZ) {
        Coverage coverage = coverage(level);
        return coverage != null && coverage.blockChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static boolean isEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
        Coverage coverage = coverage(level);
        return coverage != null && coverage.entityChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    private static Coverage coverage(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return null;
        }
        long tick = level.getServer().getTickCount();
        Coverage cached = CACHE.get(level);
        if (cached != null && tick >= cached.builtTick && tick - cached.builtTick < REBUILD_INTERVAL_TICKS) {
            return cached;
        }
        Coverage built = build(level, tick);
        CACHE.put(level, built);
        return built;
    }

    private static Coverage build(ServerLevel level, long tick) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        String dimension = ChunkloaderManager.getDimensionString(level);
        if (manager == null || !manager.shouldControlTicksInDimension(dimension)) {
            return Coverage.empty(tick);
        }
        LongOpenHashSet blockChunks = new LongOpenHashSet();
        LongOpenHashSet entityChunks = new LongOpenHashSet();
        var storage = ((ServerChunkManagerAccessor) level.getChunkSource()).chunkloader$getTicketStorage();
        var tickets = ((TicketStorageAccessor) (Object) storage).chunkloader$getTickets();
        if (tickets == null || tickets.isEmpty()) {
            return new Coverage(tick, blockChunks, entityChunks);
        }
        for (var entry : tickets.long2ObjectEntrySet()) {
            int originX = ChunkPos.getX(entry.getLongKey());
            int originZ = ChunkPos.getZ(entry.getLongKey());
            List<Ticket> list = entry.getValue();
            if (list == null) {
                continue;
            }
            int blockRadius = -1;
            int entityRadius = -1;
            for (Ticket ticket : list) {
                if (!counts(ticket)) {
                    continue;
                }
                int ticketLevel = ticket.getTicketLevel();
                blockRadius = Math.max(blockRadius, radius(BLOCK_LIMIT - ticketLevel));
                entityRadius = Math.max(entityRadius, radius(ENTITY_LIMIT - ticketLevel));
            }
            fill(blockChunks, originX, originZ, blockRadius);
            fill(entityChunks, originX, originZ, entityRadius);
        }
        return new Coverage(tick, blockChunks, entityChunks);
    }

    private static boolean counts(Ticket ticket) {
        if (ticket == null || ticket.getType() == null || !ticket.getType().doesSimulate()) {
            return false;
        }
        TicketType type = ticket.getType();
        if ((type.flags() & OWN_TICKET_FLAG) != 0) {
            return false;
        }
        if (type == TicketType.PLAYER_LOADING || type == TicketType.PLAYER_SIMULATION) {
            return false;
        }
        return !ChunkloaderManager.isOwnTicketType(type);
    }

    private static int radius(int raw) {
        if (raw < 0) {
            return -1;
        }
        return Math.min(MAX_RADIUS, raw);
    }

    private static void fill(LongOpenHashSet chunks, int originX, int originZ, int radius) {
        if (radius < 0) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(ChunkPos.asLong(originX + dx, originZ + dz));
            }
        }
    }

    private record Coverage(long builtTick, LongOpenHashSet blockChunks, LongOpenHashSet entityChunks) {
        private static Coverage empty(long tick) {
            return new Coverage(tick, new LongOpenHashSet(), new LongOpenHashSet());
        }
    }
}
