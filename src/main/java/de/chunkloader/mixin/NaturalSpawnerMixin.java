package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpawnHelper.class)
public class NaturalSpawnerMixin {
    @WrapOperation(
            method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper;isAcceptableSpawnPosition(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos$Mutable;D)Z"))
    private static boolean chunkloader$checkSpawnDistance(ServerWorld level, Chunk sourceChunk,
            BlockPos.Mutable pos, double distanceSquared, Operation<Boolean> original, @Local PlayerEntity player) {
        int standardDistance = ChunkloaderConstants.STANDARD_SPAWN_DISTANCE;
        double standardSquared = standardDistance * standardDistance;
        if (distanceSquared > standardSquared || !(player instanceof ChunkloaderFakePlayer fakePlayer)) {
            return original.call(level, sourceChunk, pos, distanceSquared);
        }

        var manager = ChunkloaderMod.getChunkloaderManager();
        var entry = manager == null ? null : manager.getActiveFakeplayerSpawnEntry(fakePlayer);
        if (entry == null || entry.minimumSpawnDistance() == standardDistance) {
            return original.call(level, sourceChunk, pos, distanceSquared);
        }

        int minimumDistance = entry.minimumSpawnDistance();
        if (distanceSquared <= minimumDistance * minimumDistance) {
            return false;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        for (ServerPlayerEntity nearby : level.getPlayers()) {
            if (!(nearby instanceof ChunkloaderFakePlayer) && !nearby.isSpectator()
                    && nearby.squaredDistanceTo(x, y, z) <= standardSquared) {
                return false;
            }
        }

        return original.call(level, sourceChunk, pos, Math.nextUp(standardSquared));
    }


    @WrapOperation(
            method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getClosestPlayer(DDDDZ)Lnet/minecraft/entity/player/PlayerEntity;"))
    private static PlayerEntity chunkloader$findSpawningPlayer(ServerWorld level, double x, double y, double z,
            double distance, boolean ignoreCreative, Operation<PlayerEntity> original,
            @Local(argsOnly = true) Chunk sourceChunk) {
        PlayerEntity nearest = original.call(level, x, y, z, distance, ignoreCreative);
        if (nearest == null) {
            return null;
        }
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return nearest;
        }
        ChunkPos pos = new ChunkPos(BlockPos.ofFloored(x, y, z));
        if (nearest instanceof ChunkloaderFakePlayer fakePlayer && manager.isFakeplayerSpawnChunk(fakePlayer, pos)) {
            return nearest;
        }

        ChunkPos source = sourceChunk.getPos();
        if (!source.equals(pos)) {
            String dimension = ChunkloaderManager.getDimensionString(level);
            if (manager.isFakeplayerEntityTickChunk(source.x, source.z, dimension)
                    && !manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension)
                    && !chunkloader$hasRealSpawningPlayer(level, source)) {
                return null;
            }
        }

        if (!(nearest instanceof ChunkloaderFakePlayer)) {
            return nearest;
        }
        var eligiblePlayers = ignoreCreative ? EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR : EntityPredicates.EXCEPT_SPECTATOR;
        return level.getClosestPlayer(x, y, z, distance, player -> eligiblePlayers.test(player)
                && (!(player instanceof ChunkloaderFakePlayer fakePlayer) || manager.isFakeplayerSpawnChunk(fakePlayer, pos)));
    }

    private static boolean chunkloader$hasRealSpawningPlayer(ServerWorld level, ChunkPos source) {
        int range = 128;
        for (ServerPlayerEntity player : level.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer || player.isSpectator()) {
                continue;
            }
            double dx = source.getCenterX() - player.getX();
            double dz = source.getCenterZ() - player.getZ();
            if (dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }
}
