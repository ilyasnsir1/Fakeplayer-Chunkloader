package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Mixin(Player.class)
public class PlayerEntityMixin {
    private static final Map<UUID, Long> lastPermissionMessageTime = new ConcurrentHashMap<>();
    private static final long PERMISSION_MESSAGE_COOLDOWN_MS = 500;

    @Inject(method = "interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void onInteract(Entity target, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        Player self = (Player)(Object)this;
        if (self instanceof ServerPlayer serverPlayer && target instanceof ChunkloaderFakePlayer fakePlayer &&
            hand == InteractionHand.MAIN_HAND &&
            ChunkloaderMod.getChunkloaderManager() != null &&
            fakePlayer.isVisibleAsMarker() && ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(target.getUUID())) {
            try {
                var manager = ChunkloaderMod.getChunkloaderManager();
                if (manager != null) {
                    if (!PermissionManager.canUse(serverPlayer)) {
                        UUID playerUuid = serverPlayer.getUUID();
                        long currentTime = System.currentTimeMillis();
                        Long lastMessageTime = lastPermissionMessageTime.get(playerUuid);

                        if (lastMessageTime == null || (currentTime - lastMessageTime) >= PERMISSION_MESSAGE_COOLDOWN_MS) {
                            serverPlayer.sendSystemMessage(Component.translatable("message.chunkloader.no_permission_interact"));
                            lastPermissionMessageTime.put(playerUuid, currentTime);
                        }
                        cir.setReturnValue(InteractionResult.FAIL);
                        cir.cancel();
                        return;
                    }

                    if (self.isShiftKeyDown()) {
                        de.chunkloader.config.ChunkloaderTarget entryToDelete = manager.getEntryByMarkerUuid(target.getUUID());
                        if (entryToDelete != null) {
                            manager.removeChunkloader(entryToDelete.chunkX(), entryToDelete.chunkZ(), entryToDelete.dimension());
                            serverPlayer.sendSystemMessage(Component.literal("Player deleted"));
                        }
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        cir.cancel();
                        return;
                    } else {
                        manager.openChunkMap(target.getUUID(), serverPlayer);
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        cir.cancel();
                        return;
                    }
                }
            } catch (Exception e) {
                ChunkloaderMod.LOGGER.error("Error in Player interact mixin", e);
            }
        }
    }

    @Inject(method = "getName()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player)(Object)this;
        if (self instanceof ChunkloaderFakePlayer fakePlayer && fakePlayer.isVisibleAsMarker()) {
            Component customName = fakePlayer.getCustomName();
            if (customName != null) {
                String plainName = customName.getString();
                cir.setReturnValue(Component.literal(plainName));
                return;
            }
            Component originalName = cir.getReturnValue();
            if (originalName != null) {
                cir.setReturnValue(Component.literal(originalName.getString()));
            }
        }
    }
}

