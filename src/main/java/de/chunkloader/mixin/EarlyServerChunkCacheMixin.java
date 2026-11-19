package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class EarlyServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final public ChunkMap chunkMap;
    @Shadow protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Inject(method = "collectTickingChunks", at = @At("RETURN"))
    private void chunkloader$collectLoaderTickingChunks(List<LevelChunk> chunks, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        String dimension = ChunkloaderManager.getDimensionString(level);
        Set<LevelChunk> visited = null;
        for (ChunkloaderTarget loader : manager.getActiveChunkloaderEntries()) {
            if (!loader.enabled() || !dimension.equals(loader.dimension())) {
                continue;
            }
            if (visited == null) {
                visited = new HashSet<>(chunks);
            }
            int radius = loader.chunkRadius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkHolder holder = getVisibleChunkIfPresent(ChunkPos.asLong(loader.chunkX() + dx, loader.chunkZ() + dz));
                    LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
                    if (chunk != null && visited.add(chunk)) {
                        chunks.add(chunk);
                    }
                }
            }
        }
    }

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;JLjava/util/List;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void chunkloader$excludeChunkplayerOnlySpawning(ServerLevel world, LevelChunk chunk,
            NaturalSpawner.SpawnState state, List<MobCategory> categories) {
        ChunkPos pos = chunk.getPos();
        if (!chunkloader$isChunkplayerCore(pos) || !chunkMap.getPlayersCloseForSpawning(pos).isEmpty()) {
            NaturalSpawner.spawnForChunk(world, chunk, state, categories);
        }
    }

    private boolean chunkloader$isChunkplayerCore(ChunkPos pos) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        return manager != null && manager.isChunkplayerEntityTickChunk(pos.x, pos.z,
                ChunkloaderManager.getDimensionString(level));
    }
}
