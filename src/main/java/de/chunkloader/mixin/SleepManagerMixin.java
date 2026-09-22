package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(SleepManager.class)
public class SleepManagerMixin {

    @ModifyVariable(
        method = "update(Ljava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true,
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

