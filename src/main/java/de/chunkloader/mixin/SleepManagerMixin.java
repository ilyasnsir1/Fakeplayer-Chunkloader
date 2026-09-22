package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SleepStatus.class)
public class SleepManagerMixin {

    @Shadow
    private int activePlayers;

    @Shadow
    private int sleepingPlayers;

    @Shadow
    public int sleepersNeeded(int percentage) {
        throw new AssertionError();
    }

    @Inject(method = "update(Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void chunkloader$excludeFakePlayersFromSleepStatus(List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        if (players == null || players.isEmpty()) {
            return;
        }

        int previousActive = this.activePlayers;
        int previousSleeping = this.sleepingPlayers;
        this.activePlayers = 0;
        this.sleepingPlayers = 0;

        for (ServerPlayer player : players) {
            if (player instanceof ChunkloaderFakePlayer || player.isSpectator()) {
                continue;
            }
            this.activePlayers++;
            if (player.isSleeping()) {
                this.sleepingPlayers++;
            }
        }

        boolean changed = (previousSleeping > 0 || this.sleepingPlayers > 0)
                && (previousActive != this.activePlayers || previousSleeping != this.sleepingPlayers);
        cir.setReturnValue(changed);
    }

    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true, require = 0)
    private void chunkloader$excludeFakePlayersFromDeepSleep(int percentage, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        if (players == null || players.isEmpty()) {
            return;
        }
        int deepSleeping = 0;
        for (ServerPlayer player : players) {
            if (!(player instanceof ChunkloaderFakePlayer) && player.isSleepingLongEnough()) {
                deepSleeping++;
            }
        }
        cir.setReturnValue(deepSleeping >= this.sleepersNeeded(percentage));
    }
}

