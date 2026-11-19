package de.chunkloader.util;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.mixin.ChunkTicketManagerAccessor;
import de.chunkloader.mixin.ServerChunkManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

public final class IndependentTicking {
    private static final int MAX_RADIUS = 32;
    private static final int OWN_FLAG = 1 << 30;
    private static final Map<ServerWorld, Coverage> CACHE = new WeakHashMap<>();
    private IndependentTicking() {}
    public static boolean isBlockTicking(ServerWorld world, int chunkX, int chunkZ) {
        Coverage coverage = coverage(world);
        return coverage != null && coverage.block.contains(ChunkPos.toLong(chunkX, chunkZ));
    }
    public static boolean isEntityTicking(ServerWorld world, int chunkX, int chunkZ) {
        Coverage coverage = coverage(world);
        return coverage != null && coverage.entity.contains(ChunkPos.toLong(chunkX, chunkZ));
    }
    private static Coverage coverage(ServerWorld world) {
        if (world == null || world.getServer() == null) return null;
        long tick = world.getServer().getTicks();
        Coverage cached = CACHE.get(world);
        if (cached != null && tick - cached.tick < 20L) return cached;
        Coverage built = build(world, tick);
        CACHE.put(world, built);
        return built;
    }
    private static Coverage build(ServerWorld world, long tick) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        String dimension = ChunkloaderManager.getDimensionString(world);
        LongOpenHashSet block = new LongOpenHashSet();
        LongOpenHashSet entity = new LongOpenHashSet();
        if (manager == null || !manager.shouldControlTicksInDimension(dimension)) return new Coverage(tick, block, entity);
        var storage = ((ServerChunkManagerAccessor) world.getChunkManager()).chunkloader$getTicketManager();
        var tickets = ((ChunkTicketManagerAccessor) storage).chunkloader$getTickets();
        if (tickets == null) return new Coverage(tick, block, entity);
        for (var entry : tickets.long2ObjectEntrySet()) {
            int ox = new ChunkPos(entry.getLongKey()).x;
            int oz = new ChunkPos(entry.getLongKey()).z;
            int blockRadius = -1;
            int entityRadius = -1;
            for (Object raw : entry.getValue()) {
                ChunkTicket ticket = (ChunkTicket) raw;
                if (!counts(ticket)) continue;
                int level = ticket.getLevel();
                blockRadius = Math.max(blockRadius, radius(32 - level));
                entityRadius = Math.max(entityRadius, radius(31 - level));
            }
            fill(block, ox, oz, blockRadius);
            fill(entity, ox, oz, entityRadius);
        }
        return new Coverage(tick, block, entity);
    }
    private static boolean counts(ChunkTicket ticket) {
        if (ticket == null || ticket.getType() == null) return false;
        ChunkTicketType type = ticket.getType();
        if (!type.isForSimulation()) return false;
        String name = String.valueOf(type).toLowerCase();
        if (name.contains("player") || name.contains("chunkloader")) return false;
        try { if ((type.flags() & OWN_FLAG) != 0) return false; } catch (Throwable ignored) {}
        return true;
    }
    private static int radius(int raw) { return raw < 0 ? -1 : Math.min(MAX_RADIUS, raw); }
    private static void fill(LongOpenHashSet chunks, int ox, int oz, int radius) {
        if (radius < 0) return;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) chunks.add(ChunkPos.toLong(ox + dx, oz + dz));
    }
    private record Coverage(long tick, LongOpenHashSet block, LongOpenHashSet entity) {}
}
