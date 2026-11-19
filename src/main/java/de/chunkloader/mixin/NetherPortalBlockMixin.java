package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.util.IndependentTicking;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void chunkloader$skipChunkplayerPortalSpawns(BlockState state, ServerLevel world, BlockPos block,
            RandomSource random, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
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

        int radius = world.getServer().getPlayerList().getSimulationDistance();
        boolean spectatorsGenerate = world.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
        for (var player : world.players()) {
            if (player instanceof ChunkloaderFakePlayer || (player.isSpectator() && !spectatorsGenerate)) {
                continue;
            }
            ChunkPos center = player.chunkPosition();
            if (Math.abs(center.x - pos.x) <= radius && Math.abs(center.z - pos.z) <= radius) {
                return;
            }
        }
        ci.cancel();
    }
}
