package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldTickChunkMixin {

    @Inject(
        method = "tickChunk(Lnet/minecraft/world/chunk/WorldChunk;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chunkloader$controlVanillaRandomTicks(WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerWorld self = (ServerWorld)(Object)this;

        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }

        String dimension = de.chunkloader.manager.ChunkloaderManager.getDimensionString(self);

        if (!manager.hasAnyActiveLoaderInDimension(dimension)) {
            return;
        }

        ChunkPos pos = chunk.getPos();

        MinecraftServer server = self.getServer();
        if (server != null) {
            int simulationDistance = server.getPlayerManager().getSimulationDistance();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player instanceof ChunkloaderFakePlayer) {
                    continue;
                }
                ChunkPos playerPos = new ChunkPos(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                int dx = Math.abs(playerPos.x - pos.x);
                int dz = Math.abs(playerPos.z - pos.z);
                if (Math.max(dx, dz) <= simulationDistance) {
                    return;
                }
            }
        }

        boolean isChunkplayerChunk   = manager.isChunkplayerRandomTickChunk(pos.x, pos.z, dimension);
        boolean isFakeplayerTickArea = manager.isFakeplayerRandomTickChunk(pos.x, pos.z, dimension);
        boolean allowTicks = isChunkplayerChunk || isFakeplayerTickArea;

        if (!allowTicks) {
            ci.cancel();
        }
    }
}


