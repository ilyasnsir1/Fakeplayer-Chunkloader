package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.entity.ai.goal.SkeletonHorseTrapTriggerGoal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkeletonHorseTrapTriggerGoal.class)
public class SkeletonTrapGoalMixin {
    @Redirect(method = "canStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isPlayerInRange(DDDD)Z"))
    private boolean chunkloader$trapNeedsRealPlayer(World world, double x, double y, double z, double distance) {
        if (!(world instanceof ServerWorld serverWorld)) return world.isPlayerInRange(x, y, z, distance);
        double limit = distance * distance;
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer || !player.isAlive() || player.isSpectator()) continue;
            if (player.squaredDistanceTo(x, y, z) < limit) return true;
        }
        return false;
    }
}
