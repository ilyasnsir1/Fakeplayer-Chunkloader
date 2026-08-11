package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonNetworkHandlerMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$suppressFakePlayerPackets(Packet<?> packet, CallbackInfo ci) {
        if (shouldSuppress()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void chunkloader$suppressFakePlayerPacketsWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (shouldSuppress()) {
            ci.cancel();
        }
    }

    private boolean shouldSuppress() {

        ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
        if (self instanceof ServerGamePacketListenerImpl handler) {
            ServerPlayer player = handler.player;
            return player instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(player);
        }
        return false;
    }
}
