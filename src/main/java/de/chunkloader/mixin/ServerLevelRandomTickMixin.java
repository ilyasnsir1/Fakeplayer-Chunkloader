package de.chunkloader.mixin;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomTickMixin {

    @ModifyVariable(
        method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private int chunkloader$limitVanillaRandomTicks(int randomTickSpeed, LevelChunk chunk) {
        if (randomTickSpeed <= 0 || chunk == null) {
            return randomTickSpeed;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            return randomTickSpeed;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        MinecraftServer server = level.getServer();
        if (server == null) {
            return randomTickSpeed;
        }

        int simDist = getServerSimulationDistance(server);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        if (hasRealPlayerInRange(level, chunkX, chunkZ, simDist)) {
            return randomTickSpeed;
        }

        if (!hasChunkloaderFakePlayerInRange(level, chunkX, chunkZ, simDist)) {
            return randomTickSpeed;
        }

        String dimension = level.dimension().location().toString();

        if (manager.isFakeplayerRandomTickChunk(chunkX, chunkZ, dimension)) {
            return randomTickSpeed;
        }

        return 0;
    }

    private static boolean hasRealPlayerInRange(ServerLevel level, int chunkX, int chunkZ, int simDist) {
        if (simDist < 0) simDist = 0;
        for (ServerPlayer player : level.players()) {
            if (player == null) continue;
            if (player instanceof ChunkloaderFakePlayer) continue;
            if (player.isSpectator()) continue;
            if (player.connection == null) continue;
            int dx = Math.abs(player.chunkPosition().x - chunkX);
            int dz = Math.abs(player.chunkPosition().z - chunkZ);
            if (Math.max(dx, dz) <= simDist) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChunkloaderFakePlayerInRange(ServerLevel level, int chunkX, int chunkZ, int simDist) {
        if (simDist < 0) simDist = 0;
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof ChunkloaderFakePlayer)) continue;
            int dx = Math.abs(player.chunkPosition().x - chunkX);
            int dz = Math.abs(player.chunkPosition().z - chunkZ);
            if (Math.max(dx, dz) <= simDist) {
                return true;
            }
        }
        return false;
    }

    private static int getServerSimulationDistance(MinecraftServer server) {
        try {
            Object playerList = server.getPlayerList();
            if (playerList != null) {
                java.lang.reflect.Method m = playerList.getClass().getMethod("getSimulationDistance");
                Object v = m.invoke(playerList);
                if (v instanceof Integer i) {
                    return Math.max(0, i);
                }
            }
        } catch (Exception ignored) {
        }
        return 10;
    }
}

