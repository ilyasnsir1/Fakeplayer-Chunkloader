package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.spawner.EntityDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityDetector.class)
public interface TrialSpawnerPlayerDetectorMixin {
    @WrapOperation(
            method = {"method_55155", "method_56722"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/spawner/EntityDetector$Selector;getPlayers(Lnet/minecraft/server/world/ServerWorld;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 2
    )
    private static List<? extends PlayerEntity> chunkloader$excludeChunkplayers(EntityDetector.Selector selector,
            ServerWorld level, Predicate<? super PlayerEntity> eligible, Operation<List<? extends PlayerEntity>> original) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        return original.call(selector, level, (Predicate<PlayerEntity>) player -> eligible.test(player)
                && (!(player instanceof ChunkloaderFakePlayer fakePlayer)
                        || (manager != null && manager.allowsMobSpawning(fakePlayer))));
    }
}
