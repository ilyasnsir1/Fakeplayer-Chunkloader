package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.ZombieSiegeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ZombieSiegeManager.class)
public class VillageSiegeMixin {
    @Redirect(method = "spawn(Lnet/minecraft/server/world/ServerWorld;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getPlayers()Ljava/util/List;"))
    private List<ServerPlayerEntity> chunkloader$siegeWithoutBots(ServerWorld world) {
        List<ServerPlayerEntity> real = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!(player instanceof ChunkloaderFakePlayer)) real.add(player);
        }
        return real;
    }
}
