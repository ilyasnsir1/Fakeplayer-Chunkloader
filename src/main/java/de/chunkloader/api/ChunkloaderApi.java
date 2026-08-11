package de.chunkloader.api;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class ChunkloaderApi {
    private ChunkloaderApi() {
    }

    public static boolean isSyntheticPlayer(Entity entity) {
        return entity instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(entity);
    }

    public static boolean isSyntheticPlayer(PlayerEntity player) {
        return player instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(player);
    }

    public static boolean isSpawningSyntheticPlayer() {
        return SyntheticPlayerContext.isSpawning();
    }
}
