package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    private static final int REAL_PLAYER_SPAWN_CHUNK_RADIUS = 8;

    @Inject(method = "getPlayerViewDistance", at = @At("HEAD"), cancellable = true)
    private void chunkloader$forceViewDistance(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "collectSpawningChunks", at = @At("TAIL"))
    private void chunkloader$addFakeplayerSpawningChunks(List<LevelChunk> chunks, CallbackInfo ci) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        String dimension = ChunkloaderManager.getDimensionString(level);
        List<ChunkloaderTarget> entries = manager.getActiveChunkloaderEntries();
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Set<Long> existing = new HashSet<>(chunks.size() * 2);
        for (LevelChunk c : chunks) {
            if (c != null) {
                existing.add(c.getPos().toLong());
            }
        }

        List<ServerPlayer> players = level.players();

        for (ChunkloaderTarget entry : entries) {
            if (entry == null || !entry.enabled() || !entry.allowMobSpawning()) {
                continue;
            }
            if (entry.dimension() == null || !entry.dimension().equals(dimension)) {
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
                    if (!level.getChunkSource().hasChunk(cx, cz)) {
                        continue;
                    }
                    if (isChunkNearRealPlayer(players, cx, cz)) {
                        continue;
                    }
                    LevelChunk wc = level.getChunkSource().getChunkNow(cx, cz);
                    if (wc == null) {
                        continue;
                    }
                    long key = new ChunkPos(cx, cz).toLong();
                    if (existing.add(key)) {
                        chunks.add(wc);
                    }
                }
            }
        }
    }

    private static boolean isChunkNearRealPlayer(List<ServerPlayer> players, int chunkX, int chunkZ) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        for (ServerPlayer p : players) {
            if (p == null || p instanceof ChunkloaderFakePlayer) {
                continue;
            }
            ChunkPos pc = p.chunkPosition();
            int dx = Math.abs(pc.x - chunkX);
            int dz = Math.abs(pc.z - chunkZ);
            if (dx <= REAL_PLAYER_SPAWN_CHUNK_RADIUS && dz <= REAL_PLAYER_SPAWN_CHUNK_RADIUS) {
                return true;
            }
        }
        return false;
    }
}
