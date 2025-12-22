package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPlayersMixin {

    @Inject(method = "players()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void chunkloader$filterChunkplayersFromPlayerList(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> players = cir.getReturnValue();
        if (players == null || players.isEmpty()) {
            return;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        boolean needsFilter = false;
        for (ServerPlayer player : players) {
            if (player instanceof ChunkloaderFakePlayer fakePlayer && !manager.allowsMobSpawning(fakePlayer)) {
                needsFilter = true;
                break;
            }
        }

        if (!needsFilter) {
            return;
        }

        List<ServerPlayer> filtered = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            if (!(player instanceof ChunkloaderFakePlayer fakePlayer) || manager.allowsMobSpawning(fakePlayer)) {
                filtered.add(player);
            }
        }

        cir.setReturnValue(Collections.unmodifiableList(filtered));
    }
}

