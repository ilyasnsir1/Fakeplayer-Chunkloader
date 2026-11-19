package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WardenSpawnTracker.class)
public class WardenSpawnTrackerMixin {
    @ModifyArg(remap = false, method = "getNearbyPlayers",
            at = @At(remap = false, value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"),
            index = 0)
    private static Predicate<ServerPlayer> chunkloader$ignoreNearbyLoaders(Predicate<ServerPlayer> eligible) {
        return eligible.and(player -> !(player instanceof ChunkloaderFakePlayer));
    }
}
