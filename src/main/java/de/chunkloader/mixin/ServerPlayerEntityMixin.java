package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void onDamage(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (self instanceof ChunkloaderFakePlayer fakePlayer && ChunkloaderMod.getChunkloaderManager() != null) {
            try {
                if (fakePlayer.isVisibleAsMarker() && ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(self.getUUID())) {
                    Entity attacker = source.getEntity();
                    if (attacker instanceof net.minecraft.world.entity.player.Player) {
                        if (attacker instanceof ServerPlayer serverPlayer && !PermissionManager.canUse(serverPlayer)) {
                            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.chunkloader.no_permission_interact"));
                            cir.setReturnValue(false);
                            return;
                        }
                        ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(self.getUUID());
                        self.remove(Entity.RemovalReason.KILLED);
                        cir.setReturnValue(true);
                    } else {
                        cir.setReturnValue(false);
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error in ServerPlayer damage mixin", e);
            }
        }
    }
}

