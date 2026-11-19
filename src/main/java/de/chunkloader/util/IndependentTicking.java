package de.chunkloader.util;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.mixin.DistanceManagerAccessor;
import de.chunkloader.mixin.ServerChunkManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

public final class IndependentTicking {
    private static final int MAX_RADIUS = 32;
    private static final Map<ServerLevel, Coverage> CACHE = new WeakHashMap<>();
    private IndependentTicking() {}
    public static boolean isBlockTicking(ServerLevel level, int x, int z) {
        Coverage c = coverage(level); return c != null && c.block.contains(ChunkPos.asLong(x, z));
    }
    public static boolean isEntityTicking(ServerLevel level, int x, int z) {
        Coverage c = coverage(level); return c != null && c.entity.contains(ChunkPos.asLong(x, z));
    }
    private static Coverage coverage(ServerLevel level) {
        if (level == null || level.getServer() == null) return null;
        long tick = level.getServer().getTickCount();
        Coverage cached = CACHE.get(level);
        if (cached != null && tick - cached.tick < 20) return cached;
        Coverage built = build(level, tick); CACHE.put(level, built); return built;
    }
    private static Coverage build(ServerLevel level, long tick) {
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        String dimension = ChunkloaderManager.getDimensionString(level);
        LongOpenHashSet block = new LongOpenHashSet();
        LongOpenHashSet entity = new LongOpenHashSet();
        if (manager == null || !manager.shouldControlTicksInDimension(dimension)) return new Coverage(tick, block, entity);
        var tickets = ((DistanceManagerAccessor) ((ServerChunkManagerAccessor) level.getChunkSource()).chunkloader$getDistanceManager()).chunkloader$getTickets();
        if (tickets == null) return new Coverage(tick, block, entity);
        for (var entry : tickets.long2ObjectEntrySet()) {
            int ox = ChunkPos.getX(entry.getLongKey());
            int oz = ChunkPos.getZ(entry.getLongKey());
            int br = -1, er = -1;
            for (Ticket<?> ticket : entry.getValue()) {
                if (ChunkloaderManager.isOwnTicketType(ticket.getType()) || ticket.getType() == TicketType.PLAYER) continue;
                int levelValue = ticket.getTicketLevel();
                br = Math.max(br, levelValue <= 32 ? Math.min(32, 32 - levelValue) : -1);
                er = Math.max(er, levelValue <= 31 ? Math.min(32, 31 - levelValue) : -1);
            }
            fill(block, ox, oz, br); fill(entity, ox, oz, er);
        }
        return new Coverage(tick, block, entity);
    }
    private static void fill(LongOpenHashSet set, int ox, int oz, int radius) {
        if (radius < 0) return;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) set.add(ChunkPos.asLong(ox + dx, oz + dz));
    }
    private record Coverage(long tick, LongOpenHashSet block, LongOpenHashSet entity) {}
}
