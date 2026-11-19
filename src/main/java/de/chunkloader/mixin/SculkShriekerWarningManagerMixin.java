package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.function.Predicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.entity.SculkShriekerWarningManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SculkShriekerWarningManager.class)
public class SculkShriekerWarningManagerMixin {
    @ModifyArg(method = "getPlayersInRange",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"),
            index = 0)
    private static Predicate<ServerPlayerEntity> chunkloader$ignoreNearbyLoaders(Predicate<ServerPlayerEntity> eligible) {
        return eligible.and(player -> !(player instanceof ChunkloaderFakePlayer));
    }
}
