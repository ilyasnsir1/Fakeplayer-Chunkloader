package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VillageSiege.class)
public class VillageSiegeMixin {
    @Redirect(method = "tryToSetupSiege", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<ServerPlayer> chunkloader$siegeWithoutBots(ServerLevel level) {
        List<ServerPlayer> real = new ArrayList<>();
        for (ServerPlayer player : level.players()) if (!(player instanceof ChunkloaderFakePlayer)) real.add(player);
        return real;
    }
}
