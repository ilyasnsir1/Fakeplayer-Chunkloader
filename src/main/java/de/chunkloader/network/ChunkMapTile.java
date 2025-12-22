package de.chunkloader.network;

import net.minecraft.network.FriendlyByteBuf;

public record ChunkMapTile(int chunkX, int chunkZ, int[] pixels) {

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeVarInt(pixels != null ? pixels.length : 0);
        if (pixels != null) {
            for (int pixel : pixels) {
                buf.writeInt(pixel);
            }
        }
    }

    public static ChunkMapTile read(FriendlyByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int length = buf.readVarInt();
        int[] pixels = new int[length];
        for (int i = 0; i < length; i++) {
            pixels[i] = buf.readInt();
        }
        return new ChunkMapTile(chunkX, chunkZ, pixels);
    }
}


