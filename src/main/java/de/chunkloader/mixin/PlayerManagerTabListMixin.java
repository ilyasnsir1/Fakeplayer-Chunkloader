package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerTabListMixin {

    @Inject(
        method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void chunkloader$hideTabListEntriesOnJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData data, CallbackInfo ci) {
        if (player == null || player instanceof ChunkloaderFakePlayer) {
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        try {
            ServerWorld world = player.getEntityWorld() instanceof ServerWorld sw ? sw : null;
            if (world == null) {
                return;
            }
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p instanceof ChunkloaderFakePlayer && manager.isTabListHidden(p)) {
                    player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_LISTED, p));
                }
            }
        } catch (Exception ignored) {
        }
    }
}

