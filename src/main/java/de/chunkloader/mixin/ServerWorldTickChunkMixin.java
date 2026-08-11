package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(ServerWorld.class)
public class ServerWorldTickChunkMixin {

	private static final Map<ServerWorld, Set<Long>> playerChunkCacheEntities = new WeakHashMap<>();
	private static final Map<ServerWorld, Set<Long>> playerChunkCacheBlocks = new WeakHashMap<>();
	private static long lastCacheUpdateTick = -1;

	private static int cachedSimulationDistance = 10;
	private static long lastSimulationDistanceUpdate = 0;
	private static final long SIMULATION_DISTANCE_CACHE_MS = 1000;

	private static int getSimulationDistance(ServerWorld world) {
		if (world == null || world.getServer() == null || world.getServer().getPlayerManager() == null) {
			return cachedSimulationDistance;
		}
		long now = System.currentTimeMillis();
		if (now - lastSimulationDistanceUpdate > SIMULATION_DISTANCE_CACHE_MS) {
			lastSimulationDistanceUpdate = now;
			cachedSimulationDistance = Math.max(0, world.getServer().getPlayerManager().getSimulationDistance());
		}
		return cachedSimulationDistance;
	}

	private static void ensureCacheUpdated(ServerWorld world) {
		if (world == null || world.getServer() == null)
			return;
		long currentTick = world.getServer().getTicks();
		if (lastCacheUpdateTick == currentTick) {
			return;
		}
		lastCacheUpdateTick = currentTick;
		playerChunkCacheEntities.clear();
		playerChunkCacheBlocks.clear();
	}

	private static Set<Long> getPlayerChunksForEntities(ServerWorld world) {
		ensureCacheUpdated(world);
		return playerChunkCacheEntities.computeIfAbsent(world, w -> {
			Set<Long> chunks = new HashSet<>();
			if (w.getServer() == null || w.getServer().getPlayerManager() == null)
				return chunks;
			int sim = getSimulationDistance(w);
			List<ServerPlayerEntity> players = w.getPlayers();
			if (players == null)
				return chunks;
			for (ServerPlayerEntity player : players) {
				if (player instanceof ChunkloaderFakePlayer)
					continue;
				ChunkPos center = player.getChunkPos();
				for (int dx = -sim; dx <= sim; dx++) {
					for (int dz = -sim; dz <= sim; dz++) {
						chunks.add(ChunkPos.toLong(center.x + dx, center.z + dz));
					}
				}
			}
			return chunks;
		});
	}

	private static Set<Long> getPlayerChunksForBlocks(ServerWorld world) {
		ensureCacheUpdated(world);
		return playerChunkCacheBlocks.computeIfAbsent(world, w -> {
			Set<Long> chunks = new HashSet<>();
			if (w.getServer() == null || w.getServer().getPlayerManager() == null)
				return chunks;
			int blocks = getSimulationDistance(w) + 1;
			List<ServerPlayerEntity> players = w.getPlayers();
			if (players == null)
				return chunks;
			for (ServerPlayerEntity player : players) {
				if (player instanceof ChunkloaderFakePlayer)
					continue;
				ChunkPos center = player.getChunkPos();
				for (int dx = -blocks; dx <= blocks; dx++) {
					for (int dz = -blocks; dz <= blocks; dz++) {
						chunks.add(ChunkPos.toLong(center.x + dx, center.z + dz));
					}
				}
			}
			return chunks;
		});
	}

	private static boolean chunkloader$isChunkNearRealPlayerEntities(ServerWorld world, int chunkX, int chunkZ) {
		if (world == null)
			return false;
		return getPlayerChunksForEntities(world).contains(ChunkPos.toLong(chunkX, chunkZ));
	}

	private static boolean chunkloader$isChunkNearRealPlayerBlocks(ServerWorld world, int chunkX, int chunkZ) {
		if (world == null)
			return false;
		return getPlayerChunksForBlocks(world).contains(ChunkPos.toLong(chunkX, chunkZ));
	}

	private static boolean chunkloader$isChunkNearRealPlayerRandomTicks(ServerWorld world, int chunkX, int chunkZ) {
		if (world == null)
			return false;
		return getPlayerChunksForEntities(world).contains(ChunkPos.toLong(chunkX, chunkZ));
	}

	@Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
	private void chunkloader$limitChunkTicking(WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
		if (manager == null) {
			return;
		}

		ServerWorld world = (ServerWorld) (Object) this;

		ChunkPos pos = chunk.getPos();
		if (chunkloader$isChunkNearRealPlayerBlocks(world, pos.x, pos.z)) {
			return;
		}

		String dimension = ChunkloaderManager.getDimensionString(world);
		if (!manager.shouldControlTicksInDimension(dimension)) {
			return;
		}

		if (manager.isFakeplayerBlockTickChunk(pos.x, pos.z, dimension)
				|| manager.isChunkplayerBlockTickChunk(pos.x, pos.z, dimension)) {
			return;
		}

		ci.cancel();
	}

	@ModifyVariable(method = "tickChunk", at = @At("HEAD"), argsOnly = true, index = 2, require = 1)
	private int chunkloader$limitRandomTickSpeed(int randomTickSpeed, WorldChunk chunk) {
		ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
		if (manager == null) {
			return randomTickSpeed;
		}

		ServerWorld world = (ServerWorld) (Object) this;
		ChunkPos pos = chunk.getPos();
		if (chunkloader$isChunkNearRealPlayerRandomTicks(world, pos.x, pos.z)) {
			return randomTickSpeed;
		}

		String dimension = ChunkloaderManager.getDimensionString(world);
		if (!manager.shouldControlTicksInDimension(dimension)) {
			return randomTickSpeed;
		}

		boolean allowRandomTick = manager.isFakeplayerRandomTickChunk(pos.x, pos.z, dimension)
				|| manager.isChunkplayerRandomTickChunk(pos.x, pos.z, dimension);

		return allowRandomTick ? randomTickSpeed : 0;
	}

	@Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
	private void chunkloader$limitEntityTicking(Entity entity, CallbackInfo ci) {
		ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
		if (manager == null) {
			return;
		}

		ServerWorld world = (ServerWorld) (Object) this;

		ChunkPos pos = entity.getChunkPos();
		if (chunkloader$isChunkNearRealPlayerEntities(world, pos.x, pos.z)) {
			return;
		}

		if (entity instanceof PlayerEntity) {
			return;
		}

		String dimension = ChunkloaderManager.getDimensionString(world);
		if (!manager.shouldControlTicksInDimension(dimension)) {
			return;
		}

		boolean inFakeMobRadius = manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension);
		boolean inFakeCoreRadius = manager.isFakeplayerRandomTickChunk(pos.x, pos.z, dimension);
		boolean inChunkplayerRadius = manager.isChunkplayerEntityTickChunk(pos.x, pos.z, dimension);

		if (inFakeCoreRadius) {
			return;
		}
		if (inFakeMobRadius && entity instanceof MobEntity) {
			return;
		}

		if (inChunkplayerRadius && !(entity instanceof MobEntity)) {
			return;
		}

		ci.cancel();
	}

	@Inject(method = "shouldTickBlocksInChunk", at = @At("HEAD"), cancellable = true)
	private void chunkloader$limitBlockEntityTicking(long chunkPosLong, CallbackInfoReturnable<Boolean> cir) {
		ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
		if (manager == null) {
			return;
		}

		ServerWorld world = (ServerWorld) (Object) this;
		ChunkPos pos = new ChunkPos(chunkPosLong);
		if (chunkloader$isChunkNearRealPlayerBlocks(world, pos.x, pos.z)) {
			return;
		}

		String dimension = ChunkloaderManager.getDimensionString(world);
		if (!manager.shouldControlTicksInDimension(dimension)) {
			return;
		}

		if (manager.isFakeplayerBlockTickChunk(pos.x, pos.z, dimension)
				|| manager.isChunkplayerBlockTickChunk(pos.x, pos.z, dimension)) {
			return;
		}

		cir.setReturnValue(false);
	}
}
