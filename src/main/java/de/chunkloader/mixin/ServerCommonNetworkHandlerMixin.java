package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.SyntheticPlayerContext;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void chunkloader$suppressFakePlayerPackets(Packet<?> packet, CallbackInfo ci) {
        if (shouldSuppress()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V",
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

        ServerCommonNetworkHandler self = (ServerCommonNetworkHandler) (Object) this;
        if (self instanceof ServerPlayNetworkHandler handler) {
            ServerPlayerEntity player = handler.player;
            return player instanceof ChunkloaderFakePlayer || SyntheticPlayerContext.isMarked(player);
        }
        return false;
    }
}
