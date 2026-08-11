package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerManagerTabListMixin {

    @Inject(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void chunkloader$hideTabListEntriesOnJoin(Connection connection, ServerPlayer player, CommonListenerCookie data, CallbackInfo ci) {
        if (player == null || player instanceof ChunkloaderFakePlayer) {
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        try {
            ServerLevel world = player.level() instanceof ServerLevel sw ? sw : null;
            if (world == null) {
                return;
            }
            for (ServerPlayer p : world.players()) {
                if (p instanceof ChunkloaderFakePlayer && manager.isTabListHidden(p)) {
                    player.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, p));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
