package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    
    @Inject(method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void onDamage(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
        if (self instanceof ChunkloaderFakePlayer fakePlayer && ChunkloaderMod.getChunkloaderManager() != null) {
            try {
                if (fakePlayer.isVisibleAsMarker() && ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(self.getUuid())) {
                    Entity attacker = source.getAttacker();
                    if (attacker instanceof net.minecraft.entity.player.PlayerEntity) {
                    ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(self.getUuid());
                    self.remove(Entity.RemovalReason.KILLED);
                    cir.setReturnValue(true);
                    } else {
                        cir.setReturnValue(false);
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error in ServerPlayerEntity damage mixin", e);
            }
        }
    }
}

