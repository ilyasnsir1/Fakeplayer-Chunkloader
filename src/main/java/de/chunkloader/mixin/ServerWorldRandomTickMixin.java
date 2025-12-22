package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerWorld.class)
public class ServerWorldRandomTickMixin {
    
    @Inject(
        method = "tick(Ljava/util/function/BooleanSupplier;)V",
        at = @At("TAIL")
    )
    private void performRandomTicksForChunkplayers(CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;
        
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        String dimension = de.chunkloader.manager.ChunkloaderManager.getDimensionString(self);
        Set<de.chunkloader.manager.ChunkloaderManager.ChunkKey> chunkplayerChunks = manager.getChunkplayerChunksForRandomTicks(dimension);
        
        if (chunkplayerChunks.isEmpty()) {
            return;
        }
        
        for (de.chunkloader.manager.ChunkloaderManager.ChunkKey chunkKey : chunkplayerChunks) {
            ChunkPos chunkPos = new ChunkPos(chunkKey.x(), chunkKey.z());
            
            try {
                net.minecraft.world.chunk.Chunk chunk = self.getChunk(chunkPos.x, chunkPos.z);
                if (chunk == null || !(chunk instanceof WorldChunk)) {
                    continue;
                }
                
                for (int i = 0; i < 3; i++) {
                    int x = chunkPos.getStartX() + self.random.nextInt(16);
                    int z = chunkPos.getStartZ() + self.random.nextInt(16);
                    int bottomY = self.getBottomY();
                    int topY = 320;
                    int height = topY - bottomY;
                    int y = bottomY + self.random.nextInt(height);
                    
                    if (y >= bottomY && y < topY) {
                        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y, z);
                        net.minecraft.block.BlockState state = self.getBlockState(pos);
                        if (state.hasRandomTicks()) {
                            state.randomTick(self, pos, self.random);
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }
}

