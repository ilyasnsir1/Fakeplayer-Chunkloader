package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    
    @Inject(
        method = "getPlayers()Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void filterFakePlayersWithoutMobSpawning(CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        List<ServerPlayerEntity> players = cir.getReturnValue();
        
        if (players == null || players.isEmpty()) {
            return;
        }
        
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        
        List<ServerPlayerEntity> filtered = players.stream()
            .filter(player -> {
                if (!(player instanceof ChunkloaderFakePlayer fakePlayer)) {
                    return true;
                }
                
                return manager.allowsMobSpawning(fakePlayer);
            })
            .collect(Collectors.toList());
        
        cir.setReturnValue(filtered);
    }
}
