package de.chunkloader.manager;

import java.util.Arrays;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.Heightmap;

public final class ChunkMapRenderer {

    private ChunkMapRenderer() {
    }

    public static final int DEFAULT_TILE_COLOR_ABGR = argbToAbgr(0xFF555555);
    public static final int ERROR_TILE_COLOR_ABGR = argbToAbgr(0xFFFF00FF);
    public static final int AIR_TILE_COLOR_ABGR = argbToAbgr(0xFF000000);

    public static int computeMapSize(int chunkRadius) {
        int desired = Math.max(9, chunkRadius * 2 + 3);
        if ((desired & 1) == 0) {
            desired++;
        }
        return Math.min(33, desired);
    }

    public static int[] generateChunkTilePixels(ServerWorld world, ChunkPos chunkPos, int yLevel) {
        if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            return solidTile(DEFAULT_TILE_COLOR_ABGR);
        }
        int[] pixels = new int[16 * 16];
        boolean sampleSameLayer = world.getDimension().hasCeiling();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int index = localZ * 16 + localX;
                pixels[index] = sampleChunkPixel(world, chunkPos, localX, localZ, yLevel, sampleSameLayer);
            }
        }
        return pixels;
    }

    public static int sampleChunkPixel(ServerWorld world, ChunkPos chunkPos, int localX, int localZ, int yLevel,
            boolean sampleSameLayer) {
        int worldX = chunkPos.getStartX() + localX;
        int worldZ = chunkPos.getStartZ() + localZ;
        try {
            BlockPos samplePos;
            int northY = -1;
            int westY = -1;
            if (sampleSameLayer) {
                samplePos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ, 16);
                if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ - 1, 16);
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkManager().isChunkLoaded(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = findSurfaceWithFallback(world, worldX - 1, yLevel + 1, worldZ, 16);
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            } else {
                BlockPos surfacePos = world
                        .getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ)).down();
                if (world.isAir(surfacePos)) {
                    surfacePos = firstSolidBlockBelowUnlimited(world, worldX, surfacePos.getY(), worldZ);
                }
                samplePos = surfacePos;
                if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = world
                            .getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ - 1))
                            .down();
                    if (world.isAir(northPos)) {
                        northPos = firstSolidBlockBelowUnlimited(world, worldX, northPos.getY(), worldZ - 1);
                    }
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkManager().isChunkLoaded(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = world
                            .getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX - 1, 0, worldZ))
                            .down();
                    if (world.isAir(westPos)) {
                        westPos = firstSolidBlockBelowUnlimited(world, worldX - 1, westPos.getY(), worldZ);
                    }
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            }

            if (!world.isInBuildLimit(samplePos)) {
                return ERROR_TILE_COLOR_ABGR;
            }

            BlockState state = world.getBlockState(samplePos);
            if (state.isAir()) {
                return AIR_TILE_COLOR_ABGR;
            }

            Block block = state.getBlock();
            boolean isLava = block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON;
            boolean isNether = world.getRegistryKey() == World.NETHER;
            int rgb = state.getMapColor(world, samplePos) != null ? state.getMapColor(world, samplePos).color
                    : 0x555555;
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            if (isLava && isNether) {
                red = 255;
                green = 100;
                blue = 0;
            }
            if (northY >= 0 || westY >= 0) {
                if ((northY >= 0 && samplePos.getY() > northY) || (westY >= 0 && samplePos.getY() > westY)) {
                    if (red == 0 && green == 0 && blue == 0) {
                        red = 3;
                        green = 3;
                        blue = 3;
                    } else {
                        if (red > 0 && red < 3)
                            red = 3;
                        if (green > 0 && green < 3)
                            green = 3;
                        if (blue > 0 && blue < 3)
                            blue = 3;
                        red = Math.min((int) (red / 0.7F), 255);
                        green = Math.min((int) (green / 0.7F), 255);
                        blue = Math.min((int) (blue / 0.7F), 255);
                    }
                }
                if ((northY >= 0 && samplePos.getY() < northY) || (westY >= 0 && samplePos.getY() < westY)) {
                    red = Math.max((int) (red * 0.7F), 0);
                    green = Math.max((int) (green * 0.7F), 0);
                    blue = Math.max((int) (blue * 0.7F), 0);
                }
            }
            int argb = (255 << 24) | (red << 16) | (green << 8) | blue;
            return argbToAbgr(argb);
        } catch (Exception e) {
            return ERROR_TILE_COLOR_ABGR;
        }
    }

    public static BlockPos findSurfaceWithFallback(ServerWorld world, int x, int startY, int z, int maxSteps) {
        BlockPos pos = firstSolidBlockBelow(world, x, startY, z, maxSteps);
        if (!world.isInBuildLimit(pos) || world.getBlockState(pos).isAir()) {
            return firstSolidBlockBelowUnlimited(world, x, startY, z);
        }
        return pos;
    }

    public static BlockPos firstSolidBlockBelow(ServerWorld world, int x, int y, int z, int maxSteps) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, y, z);
        int steps = 0;
        int bottomY = world.getDimension().minY();
        while (mutable.getY() > bottomY && world.isAir(mutable) && steps++ < maxSteps) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.toImmutable();
    }

    public static BlockPos firstSolidBlockBelowUnlimited(ServerWorld world, int x, int y, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, y, z);
        int bottomY = world.getDimension().minY();
        while (mutable.getY() > bottomY && world.isAir(mutable)) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.toImmutable();
    }

    public static int[] solidTile(int colorAbgr) {
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, colorAbgr);
        return pixels;
    }

    public static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0x000000FF) | ((argb << 16) & 0x00FF0000);
    }
}
