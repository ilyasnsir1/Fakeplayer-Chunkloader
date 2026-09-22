package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListS2CPacket.Entry.class)
public class PlayerListEntryMixin {

    @Shadow @Final @Mutable private boolean listed;

    @Inject(method = "<init>(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("TAIL"), require = 0)
    private void chunkloader$applyListedFlag(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ChunkloaderFakePlayer)) {
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return;
        }
        this.listed = !manager.isTabListHidden(player);
    }
}

