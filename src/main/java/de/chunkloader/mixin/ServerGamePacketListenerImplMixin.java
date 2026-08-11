package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void chunkloader$skipSendToFakePlayer(Packet<?> packet, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(player)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void chunkloader$skipSendToFakePlayer(Packet<?> packet, Object listener, CallbackInfo ci) {
        if (player instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(player)) {
            ci.cancel();
        }
    }
}
