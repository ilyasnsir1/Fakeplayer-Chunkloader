package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.util.IndependentTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void chunkloader$excludeChunkplayerPortalSpawns(BlockState state, ServerLevel level,
            BlockPos pos, RandomSource random, CallbackInfo ci) {
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        ChunkPos chunk = new ChunkPos(pos);
        String dimension = ChunkloaderManager.getDimensionString(level);
        if (!manager.isChunkplayerEntityTickChunk(chunk.x, chunk.z, dimension)
                || manager.isFakeplayerEntityTickChunk(chunk.x, chunk.z, dimension)
                || IndependentTicking.isEntityTicking(level, chunk.x, chunk.z)) {
            return;
        }

        int radius = level.getServer().getPlayerList().getSimulationDistance();
        boolean spectatorsGenerate = level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
        for (ServerPlayer player : level.players()) {
            if (player instanceof ChunkloaderFakePlayer || (player.isSpectator() && !spectatorsGenerate)) {
                continue;
            }
            ChunkPos center = player.chunkPosition();
            if (Math.abs(center.x - chunk.x) <= radius && Math.abs(center.z - chunk.z) <= radius) {
                return;
            }
        }
        ci.cancel();
    }
}
