package de.chunkloader.client.screen;

import de.chunkloader.ChunkloaderMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class ChunkTileImage implements AutoCloseable {

    private final ClientWorld world;
    private final ChunkPos chunkPos;
    private final int yLevel;

    private NativeImageBackedTexture texture;
    private Identifier textureId;

    public ChunkTileImage(ClientWorld world, ChunkPos chunkPos, int yLevel) {
        this.world = world;
        this.chunkPos = chunkPos;
        this.yLevel = yLevel;
    }

    public Identifier getTextureId() {
        if (world == null) {
            return null;
        }
        if (textureId == null) {
            texture = new NativeImageBackedTexture(() -> "chunkloader_map_tile", createImage());
            textureId = Identifier.of(ChunkloaderMod.MOD_ID, "chunkmap/" + chunkPos.x + "_" + chunkPos.z + "_" + yLevel);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
        }
        return textureId;
    }

    @Override
    public void close() {
        if (textureId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
            textureId = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private NativeImage createImage() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        boolean sampleSameLayer = world.getDimension().hasCeiling();

        if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    image.setColor(localX, localZ, 0xFF555555);
                }
            }
            return image;
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getStartX() + localX;
                int worldZ = chunkPos.getStartZ() + localZ;

                BlockPos samplePos;
                int northY = -1;
                int westY = -1;
                BlockState state;

                try {
                if (sampleSameLayer) {
                    samplePos = findSurfaceWithFallback(worldX, yLevel + 1, worldZ, 16);
                    if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z - 1)) {
                        BlockPos northPos = findSurfaceWithFallback(worldX, yLevel + 1, worldZ - 1, 16);
                        if (!world.getBlockState(northPos).isAir()) {
                            northY = northPos.getY();
                        }
                    }
                    if (world.getChunkManager().isChunkLoaded(chunkPos.x - 1, chunkPos.z)) {
                        BlockPos westPos = findSurfaceWithFallback(worldX - 1, yLevel + 1, worldZ, 16);
                        if (!world.getBlockState(westPos).isAir()) {
                            westY = westPos.getY();
                        }
                    }
                } else {
                    samplePos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ)).down();
                    if (world.isAir(samplePos)) {
                        samplePos = firstSolidBlockBelowUnlimited(worldX, samplePos.getY(), worldZ);
                    }
                    if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z - 1)) {
                        BlockPos northPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ - 1)).down();
                        if (world.isAir(northPos)) {
                            northPos = firstSolidBlockBelowUnlimited(worldX, northPos.getY(), worldZ - 1);
                        }
                        if (!world.getBlockState(northPos).isAir()) {
                            northY = northPos.getY();
                        }
                    }
                    if (world.getChunkManager().isChunkLoaded(chunkPos.x - 1, chunkPos.z)) {
                        BlockPos westPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(worldX - 1, 0, worldZ)).down();
                        if (world.isAir(westPos)) {
                            westPos = firstSolidBlockBelowUnlimited(worldX - 1, westPos.getY(), worldZ);
                        }
                        if (!world.getBlockState(westPos).isAir()) {
                            westY = westPos.getY();
                        }
                    }
                    }

                if (!world.isInBuildLimit(samplePos)) {
                        image.setColor(localX, localZ, 0xFF555555);
                        continue;
                    }

                state = world.getBlockState(samplePos);
                if (state.isAir()) {
                    image.setColor(localX, localZ, 0xFF000000);
                    continue;
                }
                
                Block block = state.getBlock();
                boolean isLava = block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON;
                boolean isNether = world.getRegistryKey() == World.NETHER;
                
                var mapColor = state.getMapColor(world, samplePos);
                int rgb = mapColor != null ? mapColor.color : 0x555555;

                int red = ((rgb >> 16) & 255);
                int green = ((rgb >> 8) & 255);
                int blue = (rgb & 255);
                
                int temp = red;
                red = blue;
                blue = temp;

                if (isLava && isNether) {
                    red = 0;
                    green = 165;
                    blue = 255;
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

                image.setColor(localX, localZ, (255 << 24) | (red << 16) | (green << 8) | blue);
                } catch (Exception e) {
                    image.setColor(localX, localZ, 0xFF555555);
                }
            }
        }

        return image;
    }


    private BlockPos findSurfaceWithFallback(int x, int startY, int z, int maxSteps) {
        BlockPos pos = firstSolidBlockBelow(x, startY, z, maxSteps);
        if (!world.isInBuildLimit(pos) || world.getBlockState(pos).isAir()) {
            pos = firstSolidBlockBelowUnlimited(x, startY, z);
        }
        return pos;
    }

    private BlockPos firstSolidBlockBelow(int x, int y, int z, int maxSteps) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, y, z);
        int steps = 0;
        int bottomY = world.getBottomY();
        while (mutable.getY() > bottomY && world.isAir(mutable) && steps++ < maxSteps) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.toImmutable();
    }

    private BlockPos firstSolidBlockBelowUnlimited(int x, int y, int z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, y, z);
        int bottomY = world.getBottomY();
        while (mutable.getY() > bottomY && world.isAir(mutable)) {
            mutable.move(Direction.DOWN);
        }
        if (mutable.getY() < bottomY) {
            mutable.setY(bottomY);
        }
        return mutable.toImmutable();
    }
}

