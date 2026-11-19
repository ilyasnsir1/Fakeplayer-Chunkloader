package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

    @ModifyExpressionValue(method = "tickChunks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ChunkTicketManager;getTickedChunkCount()I"))
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
        var tickets = ((ServerChunkManagerAccessor) this).chunkloader$getTicketManager();
        var candidates = extra.iterator();
        while (candidates.hasNext()) {
            if (tickets.shouldTick(candidates.nextLong())) {
                candidates.remove();
            }
        }
        return realPlayerCount + extra.size();
    }

    @WrapOperation(method = "tickChunks()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;ZZZ)V"))
    private void chunkloader$spawnOnlyNearEligiblePlayers(ServerWorld world, net.minecraft.world.chunk.WorldChunk chunk,
            net.minecraft.world.SpawnHelper.Info info, boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn,
            Operation<Void> original) {
        var map = ((ServerChunkManagerAccessor) this).chunkloader$getChunkLoadingManager();
        if (!map.getPlayersWatchingChunk(chunk.getPos()).isEmpty()) {
            original.call(world, chunk, info, spawnAnimals, spawnMonsters, rareSpawn);
        }
    }

    @ModifyExpressionValue(method = "tickChunks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkLoadingManager;shouldTick(Lnet/minecraft/util/math/ChunkPos;)Z"))
    private boolean chunkloader$tickLoaderCore(boolean vanilla,
            @com.llamalad7.mixinextras.sugar.Local ChunkPos pos) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        return vanilla || (manager != null && manager.isChunkplayerEntityTickChunk(
                pos.x, pos.z, ChunkloaderManager.getDimensionString(world)));
    }

}
