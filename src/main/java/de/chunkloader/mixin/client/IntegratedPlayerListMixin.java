package de.chunkloader.mixin.client;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.List;

@Mixin(IntegratedPlayerList.class)
public abstract class IntegratedPlayerListMixin extends PlayerList {

    private IntegratedPlayerListMixin() {
        super(null, null, null, null);
    }

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), require = 0)
    private void chunkloader$resolveNameConflictBeforeLogin(SocketAddress address, NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir) {
        try {
            String joiningName = nameAndId.name();
            if (joiningName == null || joiningName.isBlank()) {
                return;
            }

            List<ServerPlayer> playerList = this.getPlayers();
            boolean hasConflict = false;
            for (ServerPlayer existing : playerList) {
                if (existing instanceof ChunkloaderFakePlayer &&
                        joiningName.equalsIgnoreCase(existing.getName().getString())) {
                    hasConflict = true;
                    break;
                }
            }

            if (hasConflict) {
                var manager = ChunkloaderMod.getChunkloaderManager();
                if (manager != null) {
                    manager.checkAndRenameConflictingChunkloaders(joiningName);
                }
            }
        } catch (Exception ignored) {

        }
    }
}
