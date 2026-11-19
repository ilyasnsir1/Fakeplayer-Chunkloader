package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerChunkCache.class)
public abstract class EarlyServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final public ChunkMap chunkMap;

    @Redirect(method = "tickChunks()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean chunkloader$includeChunkplayerRandomTicks(ChunkMap map, ChunkPos pos) {
        return chunkloader$isChunkplayerCore(pos) || !map.getPlayersCloseForSpawning(pos).isEmpty();
    }

    @Redirect(method = "tickChunks()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void chunkloader$excludeChunkplayerOnlySpawning(ServerLevel world, LevelChunk chunk,
            NaturalSpawner.SpawnState state, boolean friendly, boolean hostile, boolean rare) {
        ChunkPos pos = chunk.getPos();
        if (!chunkloader$isChunkplayerCore(pos)
                || chunkMap.getDistanceManager().shouldForceTicks(pos.toLong())
                || !chunkMap.getPlayersCloseForSpawning(pos).isEmpty()) {
            NaturalSpawner.spawnForChunk(world, chunk, state, friendly, hostile, rare);
        }
    }

    private boolean chunkloader$isChunkplayerCore(ChunkPos pos) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        return manager != null && manager.isChunkplayerEntityTickChunk(pos.x, pos.z,
                ChunkloaderManager.getDimensionString(level));
    }
}
