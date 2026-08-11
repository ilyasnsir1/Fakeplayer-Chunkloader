package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPlayersMixin {

    @Inject(
        method = "players()Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void chunkloader$filterFakePlayersWithoutMobSpawning(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> players = cir.getReturnValue();
        if (players == null || players.isEmpty()) {
            return;
        }

        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        cir.setReturnValue(players.stream()
            .filter(player -> !(player instanceof ChunkloaderFakePlayer fake)
                    || manager.allowsMobSpawning(fake))
            .collect(Collectors.toList()));
    }
}
