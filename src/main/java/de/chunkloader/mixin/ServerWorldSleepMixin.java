package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ServerWorld.class)
public class ServerWorldSleepMixin {

    @ModifyArg(
        method = "updateSleepingPlayers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/SleepManager;update(Ljava/util/List;)V"
        ),
        index = 0,
        require = 0
    )
    private List<ServerPlayerEntity> chunkloader$excludeFakePlayersFromSleep(List<ServerPlayerEntity> players) {
        if (players == null || players.isEmpty()) {
            return players;
        }
        return players.stream()
            .filter(p -> !(p instanceof ChunkloaderFakePlayer))
            .collect(Collectors.toList());
    }
}

