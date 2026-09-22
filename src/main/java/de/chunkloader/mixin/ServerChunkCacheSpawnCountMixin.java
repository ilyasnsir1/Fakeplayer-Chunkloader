package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ServerChunkManager.class)
public class ServerChunkCacheSpawnCountMixin {

    @Shadow @Final ServerWorld world;

    private static final int REAL_PLAYER_SPAWN_CHUNK_RADIUS = 8;

    @ModifyArg(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/spawner/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/spawner/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/spawner/NaturalSpawner$SpawnState;"
        ),
        index = 0,
        require = 0
    )
    private int chunkloader$addFakeplayerSpawnChunkCount(int originalCount) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return originalCount;
        }

        String dimension = ChunkloaderManager.getDimensionString(world);
        List<ChunkloaderTarget> entries = manager.getActiveChunkloaderEntries();
        if (entries == null || entries.isEmpty()) {
            return originalCount;
        }

        List<ServerPlayerEntity> players = world.getPlayers();
        Set<Long> extra = new HashSet<>();

        for (ChunkloaderTarget entry : entries) {
            if (entry == null || !entry.enabled() || !entry.allowMobSpawning()) {
                continue;
            }
            if (entry.dimension() == null || !entry.dimension().equals(dimension)) {
                continue;
            }
            if (isMatchingFakeplayerPresent(players, entry.chunkX(), entry.chunkZ())) {
                continue;
            }

            int r = ChunkloaderManager.getEffectiveFakeplayerSpawnChunkRadius(entry);
            if (r <= 0) {
                continue;
            }

            int cx0 = entry.chunkX();
            int cz0 = entry.chunkZ();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    if (!((ServerChunkManager)(Object)this).isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    if (isChunkNearRealPlayer(players, cx, cz)) {
                        continue;
                    }
                    extra.add(ChunkPos.toLong(cx, cz));
                }
            }
        }

        if (extra.isEmpty()) {
            return originalCount;
        }
        long sum = (long) originalCount + (long) extra.size();
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static boolean isMatchingFakeplayerPresent(List<ServerPlayerEntity> players, int chunkX, int chunkZ) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        for (ServerPlayerEntity p : players) {
            if (!(p instanceof ChunkloaderFakePlayer)) {
                continue;
            }
            ChunkPos pc = p.getChunkPos();
            if (pc.x == chunkX && pc.z == chunkZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChunkNearRealPlayer(List<ServerPlayerEntity> players, int chunkX, int chunkZ) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        for (ServerPlayerEntity p : players) {
            if (p == null || p instanceof ChunkloaderFakePlayer) {
                continue;
            }
            ChunkPos pc = p.getChunkPos();
            int dx = Math.abs(pc.x - chunkX);
            int dz = Math.abs(pc.z - chunkZ);
            if (dx <= REAL_PLAYER_SPAWN_CHUNK_RADIUS && dz <= REAL_PLAYER_SPAWN_CHUNK_RADIUS) {
                return true;
            }
        }
        return false;
    }
}

