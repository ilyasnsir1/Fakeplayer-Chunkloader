package de.chunkloader.network;

import de.chunkloader.ChunkloaderConstants;
import net.minecraft.network.FriendlyByteBuf;

public record ChunkloaderPosition(int chunkX, int chunkZ, int blockX, int blockZ, String name, boolean isFakeplayer) {
    public ChunkloaderPosition(int chunkX, int chunkZ, String name, boolean isFakeplayer) {
        this(chunkX, chunkZ, chunkX * ChunkloaderConstants.CHUNK_SIZE + ChunkloaderConstants.CHUNK_CENTER_OFFSET, chunkZ * ChunkloaderConstants.CHUNK_SIZE + ChunkloaderConstants.CHUNK_CENTER_OFFSET, name, isFakeplayer);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeInt(blockX);
        buf.writeInt(blockZ);
        buf.writeUtf(name != null ? name : "");
        buf.writeBoolean(isFakeplayer);
    }

    public static ChunkloaderPosition read(FriendlyByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int blockX = buf.readInt();
        int blockZ = buf.readInt();
        String name = buf.readUtf(32767);
        boolean isFakeplayer = buf.readBoolean();
        return new ChunkloaderPosition(chunkX, chunkZ, blockX, blockZ, name.isEmpty() ? null : name, isFakeplayer);
    }
}

