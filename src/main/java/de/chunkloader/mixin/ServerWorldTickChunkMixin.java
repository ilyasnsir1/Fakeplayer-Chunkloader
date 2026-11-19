package de.chunkloader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.util.IndependentTicking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.rule.GameRules;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerWorld.class)
public class ServerWorldTickChunkMixin {
    @WrapOperation(method = {"tickEntity", "tickPassenger"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;age:I",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD), require = 2)
    private void chunkloader$skipPausedMobAge(Entity entity, int age, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        pausedMob.set(chunkloader$isPausedMob(entity));
        if (!pausedMob.get()) {
            original.call(entity, age);
        }
    }

    @WrapOperation(method = "tickEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void chunkloader$skipPausedMobTick(Entity entity, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        if (!pausedMob.get()) {
            original.call(entity);
        }
    }

    @WrapOperation(method = "tickPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tickRiding()V"))
    private void chunkloader$skipPausedMobRideTick(Entity rider, Operation<Void> original,
            @Share("pausedMob") LocalBooleanRef pausedMob) {
        if (!pausedMob.get()) {
            original.call(rider);
            return;
        }

        rider.setVelocity(Vec3d.ZERO);
        Entity vehicle = rider.getVehicle();
        if (vehicle != null) {
            vehicle.updatePassengerPosition(rider);
        }
        rider.onLanding();
    }

    private boolean chunkloader$isPausedMob(Entity entity) {
        if (!(entity instanceof MobEntity)) {
            return false;
        }
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            return false;
        }
        ServerWorld world = (ServerWorld) (Object) this;
        String dimension = ChunkloaderManager.getDimensionString(world);
        ChunkPos pos = entity.getChunkPos();
        return manager.isChunkplayerEntityTickChunk(pos.x, pos.z, dimension)
                && !manager.isChunkplayerMobAiChunk(pos.x, pos.z, dimension)
                && !manager.isFakeplayerEntityTickChunk(pos.x, pos.z, dimension)
                && !chunkloader$hasRealPlayerSimulation(world, pos)
                && !IndependentTicking.isEntityTicking(world, pos.x, pos.z);
    }

    private static boolean chunkloader$hasRealPlayerSimulation(ServerWorld world, ChunkPos pos) {
        int radius = world.getServer().getPlayerManager().getSimulationDistance();
        boolean spectatorsGenerateChunks = world.getGameRules().getValue(GameRules.SPECTATORS_GENERATE_CHUNKS);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer || (player.isSpectator() && !spectatorsGenerateChunks)) {
                continue;
            }
            ChunkPos center = player.getChunkPos();
            if (Math.abs(center.x - pos.x) <= radius && Math.abs(center.z - pos.z) <= radius) {
                return true;
            }
        }
        return false;
    }
}
