package de.chunkloader.mixin;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.equine.SkeletonTrapGoal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkeletonTrapGoal.class)
public class SkeletonTrapGoalMixin {
    @Redirect(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasNearbyAlivePlayer(DDDD)Z"
        )
    )
    private boolean chunkloader$trapNeedsRealPlayer(Level level, double x, double y, double z, double distance) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return level.hasNearbyAlivePlayer(x, y, z, distance);
        }
        double limit = distance * distance;
        for (ServerPlayer player : serverLevel.players()) {
            if (player instanceof ChunkloaderFakePlayer || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (player.distanceToSqr(x, y, z) < limit) {
                return true;
            }
        }
        return false;
    }
}
