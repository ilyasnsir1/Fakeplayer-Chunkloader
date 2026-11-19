package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.util.IndependentTicking;
import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void chunkloader$skipChunkplayerPortalSpawns(BlockState state, ServerWorld world, BlockPos block,
            Random random, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        ChunkPos pos = new ChunkPos(block);
        String dimension = ChunkloaderManager.getDimensionString(world);
        if (!manager.isChunkplayerEntityTickChunk(pos.x, pos.z, dimension)
                || manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension)
                || IndependentTicking.isEntityTicking(world, pos.x, pos.z)) {
            return;
        }

        int radius = world.getServer().getPlayerManager().getSimulationDistance();
        boolean spectatorsGenerate = world.getGameRules().getBoolean(GameRules.SPECTATORS_GENERATE_CHUNKS);
        for (var player : world.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer || (player.isSpectator() && !spectatorsGenerate)) {
                continue;
            }
            ChunkPos center = player.getChunkPos();
            if (Math.abs(center.x - pos.x) <= radius && Math.abs(center.z - pos.z) <= radius) {
                return;
            }
        }
        ci.cancel();
    }
}
