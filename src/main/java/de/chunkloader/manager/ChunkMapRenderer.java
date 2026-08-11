package de.chunkloader.manager;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

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

    public static int[] generateChunkTilePixels(ServerLevel world, ChunkPos chunkPos, int yLevel) {
        if (!world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
            return solidTile(DEFAULT_TILE_COLOR_ABGR);
        }
        int[] pixels = new int[16 * 16];
        boolean sampleSameLayer = world.dimensionType().hasCeiling();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int index = localZ * 16 + localX;
                pixels[index] = sampleChunkPixel(world, chunkPos, localX, localZ, yLevel, sampleSameLayer);
            }
        }
        return pixels;
    }

    public static int sampleChunkPixel(ServerLevel world, ChunkPos chunkPos, int localX, int localZ, int yLevel,
            boolean sampleSameLayer) {
        int worldX = chunkPos.getMinBlockX() + localX;
        int worldZ = chunkPos.getMinBlockZ() + localZ;
        try {
            BlockPos samplePos;
            int northY = -1;
            int westY = -1;
            if (sampleSameLayer) {
                samplePos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ, 16);
                if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = findSurfaceWithFallback(world, worldX, yLevel + 1, worldZ - 1, 16);
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = findSurfaceWithFallback(world, worldX - 1, yLevel + 1, worldZ, 16);
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            } else {
                BlockPos surfacePos = world
                        .getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ)).below();
                if (world.isEmptyBlock(surfacePos)) {
                    surfacePos = firstSolidBlockBelowUnlimited(world, worldX, surfacePos.getY(), worldZ);
                }
                samplePos = surfacePos;
                if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                    BlockPos northPos = world
                            .getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ - 1))
                            .below();
                    if (world.isEmptyBlock(northPos)) {
                        northPos = firstSolidBlockBelowUnlimited(world, worldX, northPos.getY(), worldZ - 1);
                    }
                    if (!world.getBlockState(northPos).isAir()) {
                        northY = northPos.getY();
                    }
                }
                if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                    BlockPos westPos = world
                            .getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX - 1, 0, worldZ))
                            .below();
                    if (world.isEmptyBlock(westPos)) {
                        westPos = firstSolidBlockBelowUnlimited(world, worldX - 1, westPos.getY(), worldZ);
                    }
                    if (!world.getBlockState(westPos).isAir()) {
                        westY = westPos.getY();
                    }
                }
            }

            if (!world.isInWorldBounds(samplePos)) {
                return ERROR_TILE_COLOR_ABGR;
            }

            BlockState state = world.getBlockState(samplePos);
            if (state.isAir()) {
                return AIR_TILE_COLOR_ABGR;
            }

            Block block = state.getBlock();
            boolean isLava = block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON;
            boolean isNether = world.dimension() == Level.NETHER;
            int rgb = state.getMapColor(world, samplePos) != null ? state.getMapColor(world, samplePos).col : 0x555555;
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

    public static BlockPos findSurfaceWithFallback(ServerLevel world, int x, int startY, int z, int maxSteps) {
        BlockPos pos = firstSolidBlockBelow(world, x, startY, z, maxSteps);
        if (!world.isInWorldBounds(pos) || world.getBlockState(pos).isAir()) {
            return firstSolidBlockBelowUnlimited(world, x, startY, z);
        }
        return pos;
    }

    public static BlockPos firstSolidBlockBelow(ServerLevel world, int x, int y, int z, int maxSteps) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int steps = 0;
        int bottomY = world.dimensionType().minY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable) && steps++ < maxSteps) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
    }

    public static BlockPos firstSolidBlockBelowUnlimited(ServerLevel world, int x, int y, int z) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int bottomY = world.dimensionType().minY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable)) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
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
