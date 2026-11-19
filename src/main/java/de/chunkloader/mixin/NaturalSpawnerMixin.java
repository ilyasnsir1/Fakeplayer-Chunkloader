package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    @WrapOperation(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;isRightDistanceToPlayerAndSpawnPoint(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos$MutableBlockPos;D)Z"))
    private static boolean chunkloader$checkSpawnDistance(ServerLevel level, ChunkAccess sourceChunk,
            BlockPos.MutableBlockPos pos, double distanceSquared, Operation<Boolean> original, @Local Player player) {
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
        for (ServerPlayer nearby : level.players()) {
            if (!(nearby instanceof ChunkloaderFakePlayer) && !nearby.isSpectator()
                    && nearby.distanceToSqr(x, y, z) <= standardSquared) {
                return false;
            }
        }

        return original.call(level, sourceChunk, pos, Math.nextUp(standardSquared));
    }

    @WrapOperation(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;"))
    private static Player chunkloader$findSpawningPlayer(ServerLevel level, double x, double y, double z,
            double distance, boolean ignoreCreative, Operation<Player> original,
            @Local(argsOnly = true) ChunkAccess sourceChunk) {
        Player nearest = original.call(level, x, y, z, distance, ignoreCreative);
        if (nearest == null) {
            return null;
        }
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return nearest;
        }
        ChunkPos pos = ChunkPos.containing(BlockPos.containing(x, y, z));
        if (nearest instanceof ChunkloaderFakePlayer fakePlayer && manager.isFakeplayerSpawnChunk(fakePlayer, pos)) {
            return nearest;
        }

        ChunkPos source = sourceChunk.getPos();
        if (!source.equals(pos)) {
            String dimension = ChunkloaderManager.getDimensionString(level);
            if (manager.isFakeplayerEntityTickChunk(source.x(), source.z(), dimension)
                    && !manager.isFakeplayerEntityTickChunk(pos.x(), pos.z(), dimension)
                    && !chunkloader$hasRealSpawningPlayer(level, source)) {
                return null;
            }
        }

        if (!(nearest instanceof ChunkloaderFakePlayer)) {
            return nearest;
        }
        var eligiblePlayers = ignoreCreative ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return level.getNearestPlayer(x, y, z, distance, player -> eligiblePlayers.test(player)
                && (!(player instanceof ChunkloaderFakePlayer fakePlayer) || manager.isFakeplayerSpawnChunk(fakePlayer, pos)));
    }

    private static boolean chunkloader$hasRealSpawningPlayer(ServerLevel level, ChunkPos source) {
        int range = NaturalSpawner.SPAWN_DISTANCE_BLOCK;
        for (ServerPlayer player : level.players()) {
            if (player instanceof ChunkloaderFakePlayer || player.isSpectator()) {
                continue;
            }
            double dx = source.getMiddleBlockX() - player.getX();
            double dz = source.getMiddleBlockZ() - player.getZ();
            if (dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }
}
