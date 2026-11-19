package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @Shadow public abstract List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pos);

    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void chunkloader$useLoaderTickets(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ChunkloaderFakePlayer) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void chunkloader$skipChunkPackets(ServerPlayer player, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer) {
            ci.cancel();
        }
    }

    @Inject(method = "playerIsCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void chunkloader$limitFakeplayerSpawnProximity(ServerPlayer player, ChunkPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ChunkloaderFakePlayer fakePlayer) {
            ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
            cir.setReturnValue(manager != null && !player.isSpectator() && manager.isFakeplayerSpawnChunk(fakePlayer, pos));
        }
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("RETURN"), cancellable = true)
    private void chunkloader$includeFakeplayersForSpawnProximity(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && !getPlayersCloseForSpawning(pos).isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPlayersCloseForSpawning", at = @At("RETURN"), cancellable = true)
    private void chunkloader$includeFakeplayersForLocalMobCaps(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        List<ServerPlayer> players = cir.getReturnValue();
        List<ServerPlayer> extended = null;
        for (ServerPlayer player : level.players()) {
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
