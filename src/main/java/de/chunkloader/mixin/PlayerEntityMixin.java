package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    private static final Map<UUID, Long> lastPermissionMessageTime = new ConcurrentHashMap<>();
    private static final long PERMISSION_MESSAGE_COOLDOWN_MS = 500;
    
    @Inject(method = "interact(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void onInteract(Entity target, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self instanceof ServerPlayerEntity serverPlayer && target instanceof ChunkloaderFakePlayer fakePlayer && 
            hand == Hand.MAIN_HAND &&
            ChunkloaderMod.getChunkloaderManager() != null && 
            fakePlayer.isVisibleAsMarker() && ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(target.getUuid())) {
            try {
                var manager = ChunkloaderMod.getChunkloaderManager();
                if (manager != null) {
                    de.chunkloader.config.ChunkloaderTarget entry = manager.getEntryByMarkerUuid(target.getUuid());
                    String entityTypeName = (entry != null && entry.allowMobSpawning()) ? "fakeplayers" : "chunkplayers";
                    
                    if (!PermissionManager.canUse(serverPlayer)) {
                        UUID playerUuid = serverPlayer.getUuid();
                        long currentTime = System.currentTimeMillis();
                        Long lastMessageTime = lastPermissionMessageTime.get(playerUuid);
                        
                        if (lastMessageTime == null || (currentTime - lastMessageTime) >= PERMISSION_MESSAGE_COOLDOWN_MS) {
                            serverPlayer.sendMessage(Text.literal("You don't have permission to interact with " + entityTypeName + "."), false);
                            lastPermissionMessageTime.put(playerUuid, currentTime);
                        }
                        cir.setReturnValue(ActionResult.FAIL);
                        cir.cancel();
                        return;
                    }
                    
                    if (self.isSneaking()) {
                        de.chunkloader.config.ChunkloaderTarget entryToDelete = manager.getEntryByMarkerUuid(target.getUuid());
                        if (entryToDelete != null) {
                            manager.removeChunkloader(entryToDelete.chunkX(), entryToDelete.chunkZ());
                            serverPlayer.sendMessage(Text.literal("Chunkloader deleted"), false);
                        }
                        cir.setReturnValue(ActionResult.SUCCESS);
                        cir.cancel();
                        return;
                    } else {
                        manager.openChunkMap(target.getUuid(), serverPlayer);
                        cir.setReturnValue(ActionResult.SUCCESS);
                        cir.cancel();
                        return;
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error in PlayerEntity interact mixin", e);
            }
        }
    }
    
    @Inject(method = "getName()Lnet/minecraft/text/Text;", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
            Text customName = fakePlayer.getCustomName();
            if (customName != null) {
                String plainName = customName.getString();
                cir.setReturnValue(Text.literal(plainName));
                return;
            }
            Text originalName = cir.getReturnValue();
            if (originalName != null) {
                cir.setReturnValue(Text.literal(originalName.getString()));
            }
        }
    }
}

