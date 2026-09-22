package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    private static final int REAL_PLAYER_SPAWN_CHUNK_RADIUS = 8;

    @Inject(method = "getPlayerViewDistance", at = @At("HEAD"), cancellable = true)
    private void chunkloader$forceViewDistance(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void chunkloader$extendFakeplayerSpawning(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        String dimension = ChunkloaderManager.getDimensionString(level);
        if (!manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension)) {
            return;
        }

        if (isChunkNearRealPlayer(level.players(), pos.x, pos.z)) {
            return;
        }

        cir.setReturnValue(true);
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
