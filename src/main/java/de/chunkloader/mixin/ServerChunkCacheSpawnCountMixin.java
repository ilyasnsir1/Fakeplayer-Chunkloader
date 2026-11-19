package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkManager.class)
public class ServerChunkCacheSpawnCountMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private ChunkLevelManager levelManager;

    @ModifyExpressionValue(method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;J)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ChunkLevelManager;getTickedChunkCount()I"))
    private int chunkloader$countFakeplayersForGlobalMobCaps(int realPlayerCount) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return realPlayerCount;
        }

        LongOpenHashSet extra = null;
        int radius = 8;
        for (ServerPlayerEntity player : world.getPlayers()) {
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
                    extra.add(ChunkPos.toLong(target.chunkX() + dx, target.chunkZ() + dz));
                }
            }
        }
        if (extra == null) {
            return realPlayerCount;
        }
        var realChunks = levelManager.iterateChunkPosToTick();
        while (realChunks.hasNext()) {
            extra.remove(realChunks.nextLong());
        }
        return realPlayerCount + extra.size();
    }
}
