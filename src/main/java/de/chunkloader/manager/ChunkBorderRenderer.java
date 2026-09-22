package de.chunkloader.manager;

import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

public final class ChunkBorderRenderer {

    private ChunkBorderRenderer() {
    }

    public static void renderChunkBorders2D(ServerWorld world, ChunkloaderTarget entry, int y) {
        if (world == null)
            return;

        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            int worldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
            for (int z = minChunkZ * ChunkloaderConstants.CHUNK_SIZE; z <= (maxChunkZ + 1)
                    * ChunkloaderConstants.CHUNK_SIZE; z += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                sendParticles(world, ParticleTypes.ELECTRIC_SPARK, worldX, y, z,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int worldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
            for (int x = minChunkX * ChunkloaderConstants.CHUNK_SIZE; x <= (maxChunkX + 1)
                    * ChunkloaderConstants.CHUNK_SIZE; x += ChunkloaderConstants.VISUALIZATION_2D_SPACING) {
                sendParticles(world, ParticleTypes.ELECTRIC_SPARK, x, y, worldZ,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_COUNT, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_OFFSET_Y, 0,
                        ChunkloaderConstants.VISUALIZATION_2D_PARTICLE_SPEED);
            }
        }
    }

    public static void renderChunkBorders3D(ServerWorld world, ChunkloaderTarget entry, int minY, int maxY,
            int tickCounter) {
        if (world == null)
            return;

        int radius = entry.chunkRadius();
        int minChunkX = entry.chunkX() - radius;
        int maxChunkX = entry.chunkX() + radius;
        int minChunkZ = entry.chunkZ() - radius;
        int maxChunkZ = entry.chunkZ() + radius;

        ParticleEffect particleType = getParticleForTime(world);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                boolean onPerimeter = chunkX == minChunkX || chunkX == maxChunkX || chunkZ == minChunkZ || chunkZ == maxChunkZ;
                if (!onPerimeter) {
                    continue;
                }
                int chunkWorldX = chunkX * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZ = chunkZ * ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldXEnd = chunkWorldX + ChunkloaderConstants.CHUNK_SIZE;
                int chunkWorldZEnd = chunkWorldZ + ChunkloaderConstants.CHUNK_SIZE;

                if (tickCounter % 10 == 0) {
                    renderVerticalEdge(world, particleType, chunkWorldX, chunkWorldZ, minY, maxY);
                    renderVerticalEdge(world, particleType, chunkWorldXEnd, chunkWorldZ, minY, maxY);
                    renderVerticalEdge(world, particleType, chunkWorldX, chunkWorldZEnd, minY, maxY);
                    renderVerticalEdge(world, particleType, chunkWorldXEnd, chunkWorldZEnd, minY, maxY);
                }

                if (tickCounter % 10 == 0) {
                    renderTopBottomEdges(world, particleType, chunkWorldX, chunkWorldXEnd, chunkWorldZ, chunkWorldZEnd,
                            minY, maxY);
                }
            }
        }
    }

    private static void renderVerticalEdge(ServerWorld world, ParticleEffect particle, int x, int z, int minY,
            int maxY) {
        for (int y = minY; y <= maxY; y += ChunkloaderConstants.VISUALIZATION_3D_VERTICAL_SPACING) {
            sendParticles(world, particle, x, y, z,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
        }
    }

    private static void renderTopBottomEdges(ServerWorld world, ParticleEffect particle, int xStart, int xEnd,
            int zStart, int zEnd, int minY, int maxY) {
        for (int x = xStart; x <= xEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
            sendParticles(world, particle, x, maxY, zStart,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
            sendParticles(world, particle, x, maxY, zEnd,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
        }
        for (int z = zStart; z <= zEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
            sendParticles(world, particle, xStart, maxY, z,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
            sendParticles(world, particle, xEnd, maxY, z,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
        }

        for (int x = xStart; x <= xEnd; x += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
            sendParticles(world, particle, x, minY, zStart,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
            sendParticles(world, particle, x, minY, zEnd,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
        }
        for (int z = zStart; z <= zEnd; z += ChunkloaderConstants.VISUALIZATION_3D_HORIZONTAL_SPACING) {
            sendParticles(world, particle, xStart, minY, z,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
            sendParticles(world, particle, xEnd, minY, z,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_COUNT, 0, 0, 0,
                    ChunkloaderConstants.VISUALIZATION_3D_PARTICLE_SPEED);
        }
    }

    public static ParticleEffect getParticleForTime(ServerWorld world) {
        if (world == null) {
            return ParticleTypes.SCRAPE;
        }
        long dayTime = world.getTimeOfDay() % 24000L;
        boolean isDay = dayTime >= 0 && dayTime < 13000L;
        return isDay ? ParticleTypes.SCRAPE : ParticleTypes.FLAME;
    }

    public static void sendParticles(ServerWorld world, ParticleEffect particleType,
            double x, double y, double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        if (world == null)
            return;
        world.spawnParticles(particleType, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }
}
