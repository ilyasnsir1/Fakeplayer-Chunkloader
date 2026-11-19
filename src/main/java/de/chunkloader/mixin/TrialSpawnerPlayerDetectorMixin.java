package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrialSpawnerData.class)
public class TrialSpawnerPlayerDetectorMixin {
    @Redirect(method = "tryDetectPlayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector;detect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector$EntitySelector;Lnet/minecraft/core/BlockPos;DZ)Ljava/util/List;"), require = 2)
    private List<UUID> chunkloader$excludeChunkplayers(PlayerDetector detector, ServerLevel level,
            PlayerDetector.EntitySelector selector, BlockPos pos, double radius, boolean lineOfSight) {
        List<UUID> detected = detector.detect(level, selector, pos, radius, lineOfSight);
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (detected.isEmpty()) {
            return detected;
        }
        List<ServerPlayer> excluded = level.getPlayers(player -> player instanceof ChunkloaderFakePlayer fakePlayer
                && (manager == null || !manager.allowsMobSpawning(fakePlayer)));
        if (excluded.isEmpty()) {
            return detected;
        }
        return detected.stream().filter(id -> excluded.stream().noneMatch(player -> player.getUUID().equals(id))).toList();
    }
}
