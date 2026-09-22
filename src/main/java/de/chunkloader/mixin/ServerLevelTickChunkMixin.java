package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map;

@Mixin(ServerLevel.class)
public class ServerLevelTickChunkMixin {

    private static final Map<ServerLevel, Set<Long>> playerChunkCacheEntities = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<Long>> playerChunkCacheBlocks = new WeakHashMap<>();
    private static long lastCacheUpdateTick = -1;

    private static int cachedSimulationDistance = 10;
    private static long lastSimulationDistanceUpdate = 0;
    private static final long SIMULATION_DISTANCE_CACHE_MS = 1000;

    private static int getServerSimulationDistance(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastSimulationDistanceUpdate > SIMULATION_DISTANCE_CACHE_MS) {
            lastSimulationDistanceUpdate = now;
            try {
                Object playerList = server.getPlayerList();
                if (playerList != null) {
                    java.lang.reflect.Method m = playerList.getClass().getMethod("getSimulationDistance");
                    Object v = m.invoke(playerList);
                    if (v instanceof Integer i) {
                        cachedSimulationDistance = Math.max(0, i);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return cachedSimulationDistance;
    }

    private static void ensureCacheUpdated(ServerLevel level) {
        long currentTick = level.getServer().getTickCount();
        if (lastCacheUpdateTick == currentTick) {
            return;
        }
        lastCacheUpdateTick = currentTick;
        playerChunkCacheEntities.clear();
        playerChunkCacheBlocks.clear();
    }

    private static Set<Long> getPlayerChunksForEntities(ServerLevel level) {
        ensureCacheUpdated(level);
        return playerChunkCacheEntities.computeIfAbsent(level, l -> {
            Set<Long> chunks = new HashSet<>();
            int sim = getServerSimulationDistance(l.getServer());
            for (ServerPlayer player : l.players()) {
                if (player == null || player instanceof ChunkloaderFakePlayer
                        || player.isSpectator() || player.connection == null) {
                    continue;
                }
                ChunkPos center = player.chunkPosition();
                for (int dx = -sim; dx <= sim; dx++) {
                    for (int dz = -sim; dz <= sim; dz++) {
                        chunks.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                    }
                }
            }
            return chunks;
        });
    }

    private static Set<Long> getPlayerChunksForBlocks(ServerLevel level) {
        ensureCacheUpdated(level);
        return playerChunkCacheBlocks.computeIfAbsent(level, l -> {
            Set<Long> chunks = new HashSet<>();
            int blocks = getServerSimulationDistance(l.getServer()) + 1;
            for (ServerPlayer player : l.players()) {
                if (player == null || player instanceof ChunkloaderFakePlayer
                        || player.isSpectator() || player.connection == null) {
                    continue;
                }
                ChunkPos center = player.chunkPosition();
                for (int dx = -blocks; dx <= blocks; dx++) {
                    for (int dz = -blocks; dz <= blocks; dz++) {
                        chunks.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                    }
                }
            }
            return chunks;
        });
    }

    private static boolean isChunkNearRealPlayerEntities(ServerLevel level, int chunkX, int chunkZ) {
        return getPlayerChunksForEntities(level).contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    private static boolean isChunkNearRealPlayerBlocks(ServerLevel level, int chunkX, int chunkZ) {
        return getPlayerChunksForBlocks(level).contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    private static boolean isChunkNearRealPlayerRandomTicks(ServerLevel level, int chunkX, int chunkZ) {
        int sim = getServerSimulationDistance(level.getServer());
        if (sim < 0) sim = 0;
        for (ServerPlayer player : level.players()) {
            if (player == null) continue;
            if (player instanceof ChunkloaderFakePlayer) continue;
            if (player.isSpectator()) continue;
            if (player.connection == null) continue;
            int dx = Math.abs(player.chunkPosition().x - chunkX);
            int dz = Math.abs(player.chunkPosition().z - chunkZ);
            if (dx <= sim && dz <= sim) {
                return true;
            }
        }
        return false;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private int chunkloader$limitVanillaRandomTicks(int randomTickSpeed, LevelChunk chunk) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return randomTickSpeed;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        ChunkPos pos = chunk.getPos();

        if (isChunkNearRealPlayerRandomTicks(level, pos.x, pos.z)) {
            return randomTickSpeed;
        }

        String dimension = ChunkloaderManager.getDimensionString(level);
        if (!manager.shouldControlTicksInDimension(dimension)) {
            return randomTickSpeed;
        }

        boolean allowRandomTick =
            manager.isFakeplayerRandomTickChunk(pos.x, pos.z, dimension)
                || manager.isChunkplayerRandomTickChunk(pos.x, pos.z, dimension);

        return allowRandomTick ? randomTickSpeed : 0;
    }

    @Inject(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$limitChunkTicking(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        ServerLevel world = (ServerLevel) (Object) this;
        String dimension = ChunkloaderManager.getDimensionString(world);
        if (!manager.shouldControlTicksInDimension(dimension)) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        if (isChunkNearRealPlayerBlocks(world, pos.x, pos.z)) {
            return;
        }
        if (manager.isFakeplayerBlockTickChunk(pos.x, pos.z, dimension)
                || manager.isChunkplayerBlockTickChunk(pos.x, pos.z, dimension)) {
            return;
        }

        ci.cancel();
    }

    @Inject(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$limitEntityTicking(Entity entity, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        ServerLevel world = (ServerLevel) (Object) this;
        String dimension = ChunkloaderManager.getDimensionString(world);
        if (!manager.shouldControlTicksInDimension(dimension)) {
            return;
        }

        ChunkPos pos = entity.chunkPosition();
        if (isChunkNearRealPlayerEntities(world, pos.x, pos.z)) {
            return;
        }

        if (entity instanceof Player) {
            return;
        }

        boolean inFakeMobRadius = manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension);
        boolean inFakeCoreRadius = manager.isFakeplayerRandomTickChunk(pos.x, pos.z, dimension);

        boolean inChunkplayerRadius = manager.isChunkplayerEntityTickChunk(pos.x, pos.z, dimension);

        if (inFakeCoreRadius) {
            return;
        }
        if (inFakeMobRadius && entity instanceof Mob) {
            return;
        }
        if (inChunkplayerRadius && !(entity instanceof Mob)) {
            return;
        }

        ci.cancel();
    }

    @Inject(method = "shouldTickBlocksAt(J)Z", at = @At("HEAD"), cancellable = true)
    private void chunkloader$limitBlockEntityTicking(long chunkPosLong, CallbackInfoReturnable<Boolean> cir) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        ServerLevel world = (ServerLevel) (Object) this;
        String dimension = ChunkloaderManager.getDimensionString(world);
        if (!manager.shouldControlTicksInDimension(dimension)) {
            return;
        }

        ChunkPos pos = new ChunkPos(chunkPosLong);
        if (isChunkNearRealPlayerBlocks(world, pos.x, pos.z)) {
            return;
        }

        if (manager.isFakeplayerBlockTickChunk(pos.x, pos.z, dimension)
                || manager.isChunkplayerBlockTickChunk(pos.x, pos.z, dimension)) {
            return;
        }

        cir.setReturnValue(false);
    }
}
