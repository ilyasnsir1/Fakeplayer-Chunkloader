package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {
    
    @Inject(
        method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventPacketSendToInvalidFakePlayer(Packet<?> packet, CallbackInfo ci) {
        ServerCommonNetworkHandler self = (ServerCommonNetworkHandler)(Object)this;
        if (self instanceof net.minecraft.server.network.ServerPlayNetworkHandler handler) {
            ServerPlayerEntity player = handler.player;
            if (player instanceof ChunkloaderFakePlayer fakePlayer) {
                if (fakePlayer.networkHandler == null || handler != fakePlayer.networkHandler) {
                    ci.cancel();
                    return;
                }
                try {
                    Class<?> staticPacketContextClass = Class.forName("xyz.nucleoid.packettweaker.impl.StaticPacketContext");
                    java.lang.reflect.Method getPacketListenerMethod = staticPacketContextClass.getMethod("getPacketListener");
                    Object packetListener = getPacketListenerMethod.invoke(null);
                    if (packetListener == null) {
                        ci.cancel();
                        return;
                    }
                } catch (Exception e) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}

