package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.util.IndependentTicking;
import net.minecraft.server.level.ServerLevel;
import org.objectweb.asm.Opcodes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public class ServerLevelTickChunkMixin {
    @WrapOperation(method = {"tickNonPassenger", "tickPassenger"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;tickCount:I", opcode = Opcodes.PUTFIELD), require = 2)
    private void chunkloader$skipPausedMobTickCount(Entity entity, int tickCount, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        pausedMob.set(chunkloader$isPausedMob(entity));
        if (!pausedMob.get()) {
            original.call(entity, tickCount);
        }
    }

    @WrapOperation(method = "tickNonPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void chunkloader$skipPausedMobTick(Entity entity, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        if (!pausedMob.get()) {
            original.call(entity);
        }
    }

    @WrapOperation(method = "tickPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void chunkloader$skipPausedMobRideTick(Entity rider, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        if (!pausedMob.get()) {
            original.call(rider);
            return;
        }

        rider.setDeltaMovement(Vec3.ZERO);
        Entity vehicle = rider.getVehicle();
        if (vehicle != null) {
            vehicle.positionRider(rider);
        }
        rider.resetFallDistance();
    }

    private boolean chunkloader$isPausedMob(Entity entity) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return false;
        }
        ServerLevel world = (ServerLevel) (Object) this;
        String dimension = ChunkloaderManager.getDimensionString(world);
        ChunkPos pos = entity.chunkPosition();
        return manager.isChunkplayerEntityTickChunk(pos.x(), pos.z(), dimension)
                && !manager.isChunkplayerMobAiChunk(pos.x(), pos.z(), dimension)
                && !manager.isFakeplayerEntityTickChunk(pos.x(), pos.z(), dimension)
                && !chunkloader$hasRealPlayerSimulation(world, pos)
                && !IndependentTicking.isEntityTicking(world, pos.x(), pos.z());
    }

    private static boolean chunkloader$hasRealPlayerSimulation(ServerLevel world, ChunkPos pos) {
        int radius = world.getServer().getPlayerList().getSimulationDistance();
        boolean spectatorsGenerateChunks = world.getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS);
        for (ServerPlayer player : world.players()) {
            if (player instanceof ChunkloaderFakePlayer || (player.isSpectator() && !spectatorsGenerateChunks)) {
                continue;
            }
            ChunkPos center = player.chunkPosition();
            if (Math.abs(center.x() - pos.x()) <= radius && Math.abs(center.z() - pos.z()) <= radius) {
                return true;
            }
        }
        return false;
    }
}
