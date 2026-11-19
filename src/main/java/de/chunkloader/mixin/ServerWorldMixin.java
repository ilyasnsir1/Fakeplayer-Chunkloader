package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Unique private List<ServerPlayerEntity> chunkloader$sourcePlayers;
    @Unique private List<ServerPlayerEntity> chunkloader$spawningPlayers;
    @Unique private ChunkloaderManager chunkloader$playerCacheManager;
    @Unique private long chunkloader$playerCacheTick;
    @Unique private long chunkloader$playerCacheRevision;
    @Unique private int chunkloader$playerCacheSize;

    @Inject(method = "getPlayers()Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void filterFakePlayersWithoutMobSpawning(CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        List<ServerPlayerEntity> players = cir.getReturnValue();
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (players == null || players.isEmpty() || manager == null) {
            chunkloader$sourcePlayers = null;
            chunkloader$spawningPlayers = null;
            chunkloader$playerCacheManager = null;
            return;
        }
        long tick = ((ServerWorld) (Object) this).getTime();
        long revision = manager.getSpawnStateRevision();
        if (chunkloader$spawningPlayers != null && chunkloader$sourcePlayers == players
                && chunkloader$playerCacheManager == manager && chunkloader$playerCacheTick == tick
                && chunkloader$playerCacheRevision == revision && chunkloader$playerCacheSize == players.size()) {
            cir.setReturnValue(new de.chunkloader.util.SnapshotList<>(chunkloader$spawningPlayers));
            return;
        }

        List<ServerPlayerEntity> filtered = new ArrayList<>(players.size());
        for (ServerPlayerEntity player : players) {
            if (!(player instanceof ChunkloaderFakePlayer fake) || manager.allowsMobSpawning(fake)) filtered.add(player);
        }
        chunkloader$sourcePlayers = players;
        chunkloader$spawningPlayers = filtered;
        chunkloader$playerCacheManager = manager;
        chunkloader$playerCacheTick = tick;
        chunkloader$playerCacheRevision = revision;
        chunkloader$playerCacheSize = players.size();
        cir.setReturnValue(new de.chunkloader.util.SnapshotList<>(chunkloader$spawningPlayers));
    }
}
