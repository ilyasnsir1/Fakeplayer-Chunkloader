package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    protected ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        throw new AssertionError();
    }

    @Unique
    private Set<Long> chunkloader$spawnCandidateChunks;

    private static final int REAL_PLAYER_SPAWN_CHUNK_RADIUS = 8;

    @Inject(method = "getPlayerViewDistance", at = @At("HEAD"), cancellable = true)
    private void chunkloader$forceViewDistance(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(0);
        }
    }

    @ModifyVariable(method = "forEachSpawnCandidateChunk", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Consumer<ChunkHolder> chunkloader$trackSpawnConsumer(Consumer<ChunkHolder> original) {
        chunkloader$spawnCandidateChunks = new HashSet<>();
        return holder -> {
            if (holder == null) {
                return;
            }
            long key = holder.getPos().toLong();
            if (chunkloader$spawnCandidateChunks.add(key)) {
                original.accept(holder);
            }
        };
    }

    @Inject(method = "forEachSpawnCandidateChunk", at = @At("TAIL"))
    private void chunkloader$addFakeplayerSpawningChunks(Consumer<ChunkHolder> consumer, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null || chunkloader$spawnCandidateChunks == null) {
            chunkloader$spawnCandidateChunks = null;
            return;
        }

        String dimension = ChunkloaderManager.getDimensionString(level);
        List<ChunkloaderTarget> entries = manager.getActiveChunkloaderEntries();
        if (entries == null || entries.isEmpty()) {
            chunkloader$spawnCandidateChunks = null;
            return;
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
                    long key = ChunkPos.asLong(cx, cz);
                    if (!chunkloader$spawnCandidateChunks.add(key)) {
                        continue;
                    }
                    ChunkHolder holder = getVisibleChunkIfPresent(key);
                    if (holder != null) {
                        consumer.accept(holder);
                    }
                }
            }
        }

        chunkloader$spawnCandidateChunks = null;
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
