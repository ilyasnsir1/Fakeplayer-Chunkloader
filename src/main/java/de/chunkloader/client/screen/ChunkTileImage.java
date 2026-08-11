package de.chunkloader.client.screen;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;

public class ChunkTileImage implements AutoCloseable {

    private final ClientLevel world;
    private final ChunkPos chunkPos;
    private final int yLevel;
    private final int rotation;

    private DynamicTexture texture;
    private ResourceLocation textureId;

    public ChunkTileImage(ClientLevel world, ChunkPos chunkPos, int yLevel, int rotation) {
        this.world = world;
        this.chunkPos = chunkPos;
        this.yLevel = yLevel;
        this.rotation = Math.floorMod(rotation, 4);
    }

    public ResourceLocation getTextureId() {
        if (world == null) {
            return null;
        }
        if (textureId == null) {
            texture = new DynamicTexture(() -> "chunkloader_map_tile", createImage());
            textureId = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "chunkmap/" + chunkPos.x + "_" + chunkPos.z + "_" + yLevel + "_r" + rotation);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }
        return textureId;
    }

    @Override
    public void close() {
        if (textureId != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            textureId = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private NativeImage createImage() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        boolean sampleSameLayer = world.dimensionType().hasCeiling();

        if (!world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    setPixelWithRotation(image, localX, localZ, 0xFF555555);
                }
            }
            return image;
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;

                BlockPos samplePos;
                int northY = -1;
                int westY = -1;
                BlockState state;

                try {
                if (sampleSameLayer) {
                    samplePos = findSurfaceWithFallback(worldX, yLevel + 1, worldZ, 16);
                    if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                        BlockPos northPos = findSurfaceWithFallback(worldX, yLevel + 1, worldZ - 1, 16);
                        if (!world.getBlockState(northPos).isAir()) {
                            northY = northPos.getY();
                        }
                    }
                    if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                        BlockPos westPos = findSurfaceWithFallback(worldX - 1, yLevel + 1, worldZ, 16);
                        if (!world.getBlockState(westPos).isAir()) {
                            westY = westPos.getY();
                        }
                    }
                } else {
                    samplePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ)).below();
                    if (world.isEmptyBlock(samplePos)) {
                        samplePos = firstSolidBlockBelowUnlimited(worldX, samplePos.getY(), worldZ);
                    }
                    if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z - 1)) {
                        BlockPos northPos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ - 1)).below();
                        if (world.isEmptyBlock(northPos)) {
                            northPos = firstSolidBlockBelowUnlimited(worldX, northPos.getY(), worldZ - 1);
                        }
                        if (!world.getBlockState(northPos).isAir()) {
                            northY = northPos.getY();
                        }
                    }
                    if (world.getChunkSource().hasChunk(chunkPos.x - 1, chunkPos.z)) {
                        BlockPos westPos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX - 1, 0, worldZ)).below();
                        if (world.isEmptyBlock(westPos)) {
                            westPos = firstSolidBlockBelowUnlimited(worldX - 1, westPos.getY(), worldZ);
                        }
                        if (!world.getBlockState(westPos).isAir()) {
                            westY = westPos.getY();
                        }
                    }
                    }

                if (!world.isInWorldBounds(samplePos)) {
                        setPixelWithRotation(image, localX, localZ, 0xFF555555);
                        continue;
                    }

                state = world.getBlockState(samplePos);
                if (state.isAir()) {
                    setPixelWithRotation(image, localX, localZ, 0xFF000000);
                    continue;
                }

                Block block = state.getBlock();
                boolean isLava = block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON;
                boolean isNether = world.dimension() == Level.NETHER;

                var mapColor = state.getMapColor(world, samplePos);
                int rgb = mapColor != null ? mapColor.col : 0x555555;

                int red = ((rgb >> 16) & 255);
                int green = ((rgb >> 8) & 255);
                int blue = (rgb & 255);

                if (isLava && isNether) {
                    red = 255;
                    green = 100;
                    blue = 0;
                }

                    if (northY >= 0 || westY >= 0) {
                if ((samplePos.getY() > northY && northY >= 0) || (samplePos.getY() > westY && westY >= 0)) {
                    if (red == 0 && green == 0 && blue == 0) {
                        red = 3;
                        green = 3;
                        blue = 3;
                    } else {
                        if (red > 0 && red < 3) red = 3;
                        if (green > 0 && green < 3) green = 3;
                        if (blue > 0 && blue < 3) blue = 3;
                        red = Math.min((int)(red / 0.7F), 255);
                        green = Math.min((int)(green / 0.7F), 255);
                        blue = Math.min((int)(blue / 0.7F), 255);
                    }
                }
                if ((samplePos.getY() < northY && northY >= 0) || (samplePos.getY() < westY && westY >= 0)) {
                    red = Math.max((int)(red * 0.7F), 0);
                    green = Math.max((int)(green * 0.7F), 0);
                    blue = Math.max((int)(blue * 0.7F), 0);
                        }
                }

                setPixelWithRotation(image, localX, localZ, (255 << 24) | (red << 16) | (green << 8) | blue);
                } catch (Exception e) {
                    setPixelWithRotation(image, localX, localZ, 0xFF555555);
                }
            }
        }

        return image;
    }

    private void setPixelWithRotation(NativeImage image, int localX, int localZ, int argb) {
        int n = 15;
        int pixelX;
        int pixelY;
        switch (rotation) {
            case 1 -> {
                pixelX = n - localZ;
                pixelY = localX;
            }
            case 2 -> {
                pixelX = n - localX;
                pixelY = n - localZ;
            }
            case 3 -> {
                pixelX = localZ;
                pixelY = n - localX;
            }
            default -> {
                pixelX = localX;
                pixelY = localZ;
            }
        }
        image.setPixel(pixelX, pixelY, argb);
    }
    private BlockPos findSurfaceWithFallback(int x, int startY, int z, int maxSteps) {
        BlockPos pos = firstSolidBlockBelow(x, startY, z, maxSteps);
        if (!world.isInWorldBounds(pos) || world.getBlockState(pos).isAir()) {
            pos = firstSolidBlockBelowUnlimited(x, startY, z);
        }
        return pos;
    }

    private BlockPos firstSolidBlockBelow(int x, int y, int z, int maxSteps) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int steps = 0;
        int bottomY = world.getMinY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable) && steps++ < maxSteps) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
    }

    private BlockPos firstSolidBlockBelowUnlimited(int x, int y, int z) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, y, z);
        int bottomY = world.getMinY();
        while (mutable.getY() > bottomY && world.isEmptyBlock(mutable)) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.immutable();
    }
}

