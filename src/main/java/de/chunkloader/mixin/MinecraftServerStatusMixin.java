package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public class MinecraftServerStatusMixin {

    @Redirect(
        method = "buildPlayerStatus",
        at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    private int chunkloader$excludeFakePlayersFromStatusCount(List<?> players) {
        int real = 0;
        for (Object player : players) {
            if (!(player instanceof ChunkloaderFakePlayer)) {
                real++;
            }
        }
        return real;
    }
}
