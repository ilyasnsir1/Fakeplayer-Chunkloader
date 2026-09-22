package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SleepStatus.class)
public class SleepStatusMixin {

    @Redirect(
        method = "update(Ljava/util/List;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"
        )
    )
    private boolean chunkloader$excludeFakePlayersFromSleep(ServerPlayer player) {
        if (player instanceof ChunkloaderFakePlayer) {
            return true;
        }
        return player.isSpectator();
    }
}

