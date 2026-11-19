package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PatrolSpawner.class)
public class PatrolSpawnerMixin {
    @Redirect(method = "tick", remap = false,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;", remap = false))
    private List<ServerPlayer> chunkloader$patrolWithoutBots(ServerLevel level) {
        List<ServerPlayer> real = new ArrayList<>();
        for (ServerPlayer player : level.players()) if (!(player instanceof ChunkloaderFakePlayer)) real.add(player);
        return real;
    }
}
