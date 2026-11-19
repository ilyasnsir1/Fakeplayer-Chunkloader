package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerDetector.class)
public interface TrialSpawnerPlayerDetectorMixin {
    @WrapOperation(
            method = {"lambda$static$0", "lambda$static$3"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector$EntitySelector;getPlayers(Lnet/minecraft/server/level/ServerLevel;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 2
    )
    private static List<? extends Player> chunkloader$excludeChunkplayers(PlayerDetector.EntitySelector selector,
            ServerLevel level, Predicate<? super Player> eligible, Operation<List<? extends Player>> original) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        return original.call(selector, level, (Predicate<Player>) player -> eligible.test(player)
                && (!(player instanceof ChunkloaderFakePlayer fakePlayer)
                        || (manager != null && manager.allowsMobSpawning(fakePlayer))));
    }
}
