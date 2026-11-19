package de.chunkloader.util;

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
    public static final int OWN_TICKET_FLAG = 1 << 30;
    private static final int MAX_RADIUS = 32;
    private static final int ENTITY_LIMIT = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final long REBUILD_INTERVAL_TICKS = 20L;

    private static final Map<ServerLevel, Coverage> CACHE = new WeakHashMap<>();

    private IndependentTicking() {
    }

    public static boolean isEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
        long tick = level.getServer().getTickCount();
        Coverage coverage = CACHE.get(level);
        if (coverage == null || tick < coverage.builtTick || tick - coverage.builtTick >= REBUILD_INTERVAL_TICKS) {
            coverage = build(level, tick);
            CACHE.put(level, coverage);
        }
        return coverage.entityChunks.contains(ChunkPos.pack(chunkX, chunkZ));
    }

    private static Coverage build(ServerLevel level, long tick) {
        LongOpenHashSet entityChunks = new LongOpenHashSet();
        var storage = ((ServerChunkManagerAccessor) level.getChunkSource()).chunkloader$getTicketStorage();
        var tickets = ((TicketStorageAccessor) (Object) storage).chunkloader$getTickets();
        for (var entry : tickets.long2ObjectEntrySet()) {
            List<Ticket> list = entry.getValue();
            int radius = -1;
            for (Ticket ticket : list) {
                TicketType type = ticket.getType();
                if (type.doesSimulate() && (type.flags() & OWN_TICKET_FLAG) == 0
                        && type != TicketType.PLAYER_SIMULATION) {
                    radius = Math.max(radius, Math.min(MAX_RADIUS, ENTITY_LIMIT - ticket.getTicketLevel()));
                }
            }
            int originX = ChunkPos.getX(entry.getLongKey());
            int originZ = ChunkPos.getZ(entry.getLongKey());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    entityChunks.add(ChunkPos.pack(originX + dx, originZ + dz));
                }
            }
        }
        return new Coverage(tick, entityChunks);
    }

    private record Coverage(long builtTick, LongOpenHashSet entityChunks) {}
}
