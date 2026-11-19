package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WardenSpawnTracker.class)
public class WardenSpawnTrackerMixin {
    @WrapOperation(method = "getNearbyPlayers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<ServerPlayer> chunkloader$ignoreNearbyLoaders(ServerLevel level,
            Predicate<ServerPlayer> eligible, Operation<List<ServerPlayer>> original) {
        return original.call(level, eligible.and(player -> !(player instanceof ChunkloaderFakePlayer)));
    }
}
