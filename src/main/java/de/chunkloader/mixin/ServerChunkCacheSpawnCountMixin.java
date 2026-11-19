package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheSpawnCountMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyArg(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;JLjava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"), index = 0)
    private int chunkloader$countFakeplayersForGlobalMobCaps(int realPlayerCount) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return realPlayerCount;
        }

        LongOpenHashSet extra = null;
        int radius = NaturalSpawner.SPAWN_DISTANCE_CHUNK;
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof ChunkloaderFakePlayer fakePlayer) || player.isSpectator()) {
                continue;
            }
            ChunkloaderTarget target = manager.getActiveFakeplayerSpawnEntry(fakePlayer);
            if (target == null) {
                continue;
            }
            if (extra == null) {
                extra = new LongOpenHashSet();
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    extra.add(ChunkPos.asLong(target.chunkX() + dx, target.chunkZ() + dz));
                }
            }
        }
        if (extra == null) {
            return realPlayerCount;
        }
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        var realChunks = distanceManager.getSpawnCandidateChunks();
        while (realChunks.hasNext()) {
            extra.remove(realChunks.nextLong());
        }
        return realPlayerCount + extra.size();
    }
}
