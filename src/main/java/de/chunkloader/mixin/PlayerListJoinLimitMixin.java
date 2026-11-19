package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PlayerList.class)
public class PlayerListJoinLimitMixin {

    @Redirect(
        method = "canPlayerLogin",
        at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    private int chunkloader$excludeFakePlayersFromJoinLimit(List<?> players) {
        int real = 0;
        for (Object player : players) {
            if (!(player instanceof ChunkloaderFakePlayer)) {
                real++;
            }
        }
        return real;
    }

}
