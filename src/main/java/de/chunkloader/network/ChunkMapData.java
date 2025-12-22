package de.chunkloader.network;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ChunkMapData(
    String displayName,
    boolean enabled,
    boolean allowMobSpawning,
    int centerChunkX,
    int centerChunkZ,
    int blockY,
    int chunkRadius,
    int mapWidth,
    int mapHeight,
    int topLeftChunkX,
    int topLeftChunkZ,
    String dimensionKey,
    List<ChunkMapCell> cells,
    int fakeplayerChunkX,
    int fakeplayerChunkZ,
    int fakeplayerBlockX,
    int fakeplayerBlockZ,
    String fakeplayerName,
    boolean nameVisible,
    boolean visualizeActive,
    boolean visualize3DActive,
    boolean canIncreaseRadius,
    List<ChunkloaderPosition> otherChunkloaders,
    String ownerName,
    boolean hideOtherDots
) {

    public void write(PacketByteBuf buf) {
        buf.writeString(displayName);
        buf.writeBoolean(enabled);
        buf.writeBoolean(allowMobSpawning);
        buf.writeInt(centerChunkX);
        buf.writeInt(centerChunkZ);
        buf.writeInt(blockY);
        buf.writeInt(chunkRadius);
        buf.writeVarInt(mapWidth);
        buf.writeVarInt(mapHeight);
        buf.writeInt(topLeftChunkX);
        buf.writeInt(topLeftChunkZ);
        buf.writeString(dimensionKey);
        buf.writeVarInt(cells.size());
        for (ChunkMapCell cell : cells) {
            cell.write(buf);
        }
        buf.writeInt(fakeplayerChunkX);
        buf.writeInt(fakeplayerChunkZ);
        buf.writeInt(fakeplayerBlockX);
        buf.writeInt(fakeplayerBlockZ);
        buf.writeString(fakeplayerName != null ? fakeplayerName : "");
        buf.writeBoolean(nameVisible);
        buf.writeBoolean(visualizeActive);
        buf.writeBoolean(visualize3DActive);
        buf.writeBoolean(canIncreaseRadius);
        buf.writeVarInt(otherChunkloaders.size());
        for (de.chunkloader.network.ChunkloaderPosition pos : otherChunkloaders) {
            pos.write(buf);
        }
        buf.writeString(ownerName != null ? ownerName : "");
        buf.writeBoolean(hideOtherDots);
    }

    public static ChunkMapData read(PacketByteBuf buf) {
        String name = buf.readString(32767);
        boolean enabled = buf.readBoolean();
        boolean allowMobSpawning = buf.readBoolean();
        int centerChunkX = buf.readInt();
        int centerChunkZ = buf.readInt();
        int blockY = buf.readInt();
        int chunkRadius = buf.readInt();
        int mapWidth = buf.readVarInt();
        int mapHeight = buf.readVarInt();
        int topLeftChunkX = buf.readInt();
        int topLeftChunkZ = buf.readInt();
        String dimensionKey = buf.readString(32767);
        int cellCount = buf.readVarInt();
        List<ChunkMapCell> cells = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            cells.add(ChunkMapCell.read(buf));
        }
        int fakeplayerChunkX = buf.readInt();
        int fakeplayerChunkZ = buf.readInt();
        int fakeplayerBlockX = buf.readInt();
        int fakeplayerBlockZ = buf.readInt();
        String fakeplayerName = buf.readString(32767);
        boolean nameVisible = buf.readBoolean();
        boolean visualizeActive = buf.readBoolean();
        boolean visualize3DActive = buf.readBoolean();
        boolean canIncreaseRadius = buf.readBoolean();
        int otherChunkloadersCount = buf.readVarInt();
        List<de.chunkloader.network.ChunkloaderPosition> otherChunkloaders = new ArrayList<>(otherChunkloadersCount);
        for (int i = 0; i < otherChunkloadersCount; i++) {
            otherChunkloaders.add(de.chunkloader.network.ChunkloaderPosition.read(buf));
        }
        String ownerName = buf.readString(32767);
        boolean hideOtherDots = buf.readBoolean();
        return new ChunkMapData(
            name,
            enabled,
            allowMobSpawning,
            centerChunkX,
            centerChunkZ,
            blockY,
            chunkRadius,
            mapWidth,
            mapHeight,
            topLeftChunkX,
            topLeftChunkZ,
            dimensionKey,
            Collections.unmodifiableList(cells),
            fakeplayerChunkX,
            fakeplayerChunkZ,
            fakeplayerBlockX,
            fakeplayerBlockZ,
            fakeplayerName.isEmpty() ? null : fakeplayerName,
            nameVisible,
            visualizeActive,
            visualize3DActive,
            canIncreaseRadius,
            Collections.unmodifiableList(otherChunkloaders),
            ownerName.isEmpty() ? null : ownerName,
            hideOtherDots
        );
    }
}

