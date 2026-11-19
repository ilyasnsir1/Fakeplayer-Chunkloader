package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkLoadingManager.class)
public abstract class ServerChunkLoadingManagerMixin {
    @Shadow @Final ServerWorld world;

    @Shadow public abstract List<ServerPlayerEntity> getPlayersWatchingChunk(ChunkPos pos);

    @Inject(method = "doesNotGenerateChunks", at = @At("HEAD"), cancellable = true)
    private void chunkloader$useLoaderTickets(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$skipChunkPackets(ServerPlayerEntity player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            ci.cancel();
        }
    }

    @Inject(method = "canTickChunk", at = @At("HEAD"), cancellable = true)
    private void chunkloader$limitFakeplayerSpawnProximity(ServerPlayerEntity player, ChunkPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ChunkloaderFakePlayer fakePlayer) {
            ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
            cir.setReturnValue(manager != null && !player.isSpectator() && manager.isFakeplayerSpawnChunk(fakePlayer, pos));
        }
    }

    @Inject(method = "shouldTick", at = @At("RETURN"), cancellable = true)
    private void chunkloader$includeFakeplayersForSpawnProximity(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && !getPlayersWatchingChunk(pos).isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPlayersWatchingChunk(Lnet/minecraft/util/math/ChunkPos;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void chunkloader$includeFakeplayersForLocalMobCaps(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        List<ServerPlayerEntity> players = cir.getReturnValue();
        List<ServerPlayerEntity> extended = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer fakePlayer && !player.isSpectator()
                    && manager.isFakeplayerSpawnChunk(fakePlayer, pos) && !players.contains(player)) {
                if (extended == null) {
                    extended = new ArrayList<>(players);
                }
                extended.add(player);
            }
        }
        if (extended != null) {
            cir.setReturnValue(extended);
        }
    }
}
