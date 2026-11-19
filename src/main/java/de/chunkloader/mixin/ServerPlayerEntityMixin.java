package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
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
                    if (!(attacker instanceof ServerPlayerEntity serverPlayer)) {
                        cir.setReturnValue(false);
                        return;
                    }
                    if (!PermissionManager.canUse(serverPlayer)) {
                        serverPlayer.sendMessage(net.minecraft.text.Text.translatable("message.chunkloader.no_permission_interact"), false);
                        cir.setReturnValue(false);
                        return;
                    }
                    var manager = ChunkloaderMod.getChunkloaderManager();
                    var entry = manager.getEntryByMarkerUuid(self.getUuid());
                    boolean deleting = serverPlayer.isSneaking();
                    boolean allowed = deleting ? PermissionManager.canModifyEntry(serverPlayer, entry)
                            : PermissionManager.canOperateEntry(serverPlayer, entry);
                    if (!allowed) {
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("You don't own this player."), false);
                        cir.setReturnValue(false);
                        return;
                    }
                    if (deleting) {
                        if (!manager.confirmMarkerDelete(serverPlayer, self.getUuid(), true)) {
                            cir.setReturnValue(false);
                            return;
                        }
                        manager.removeChunkloaderByMarkerUuid(self.getUuid());
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Player deleted"), false);
                    } else {
                        manager.handleMarkerDestroyed(self.getUuid());
                    }
                    self.remove(Entity.RemovalReason.KILLED);
                    cir.setReturnValue(true);
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error in ServerPlayerEntity damage mixin", e);
            }
        }
    }
}

