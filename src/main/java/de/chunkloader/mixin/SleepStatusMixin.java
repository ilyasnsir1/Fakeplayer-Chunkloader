package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {

    @ModifyVariable(
        method = "update(Ljava/util/List;)Z",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    private List<ServerPlayer> chunkloader$excludeFakePlayersFromSleep(List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            return players;
        }
        boolean needsFilter = false;
        for (ServerPlayer player : players) {
            if (player instanceof ChunkloaderFakePlayer) {
                needsFilter = true;
                break;
            }
        }
        if (!needsFilter) {
            return players;
        }
        List<ServerPlayer> filtered = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            if (!(player instanceof ChunkloaderFakePlayer)) {
                filtered.add(player);
            }
        }
        return filtered;
    }
}

