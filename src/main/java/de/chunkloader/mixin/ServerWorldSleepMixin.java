package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ServerLevel.class)
public class ServerWorldSleepMixin {

    @ModifyArg(
        method = "updateSleepingPlayerList",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;update(Ljava/util/List;)V"
        ),
        index = 0,
        require = 0
    )
    private List<ServerPlayer> chunkloader$excludeFakePlayersFromSleep(List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            return players;
        }
        return players.stream()
            .filter(p -> !(p instanceof ChunkloaderFakePlayer))
            .collect(Collectors.toList());
    }
}

