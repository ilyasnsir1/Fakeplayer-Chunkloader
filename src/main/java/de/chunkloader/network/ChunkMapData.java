package de.chunkloader.network;

import net.minecraft.network.FriendlyByteBuf;

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
    int entityTickRadius,
    int blockTickRadius,
    int loadingRadius,
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
    long mapGeneration,
    float fakeplayerYaw,
    String fakeplayerName,
    boolean nameVisible,
    boolean visualizeActive,
    boolean visualize3DActive,
    boolean canIncreaseRadius,
    List<ChunkloaderPosition> otherChunkloaders,
    String ownerName,
    boolean easterEgg,
    boolean mobTarget
) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(displayName);
        buf.writeBoolean(enabled);
        buf.writeBoolean(allowMobSpawning);
        buf.writeInt(centerChunkX);
        buf.writeInt(centerChunkZ);
        buf.writeInt(blockY);
        buf.writeInt(chunkRadius);
        buf.writeInt(entityTickRadius);
        buf.writeInt(blockTickRadius);
        buf.writeInt(loadingRadius);
        buf.writeVarInt(mapWidth);
        buf.writeVarInt(mapHeight);
        buf.writeInt(topLeftChunkX);
        buf.writeInt(topLeftChunkZ);
        buf.writeUtf(dimensionKey);
        buf.writeVarInt(cells.size());
        for (ChunkMapCell cell : cells) {
            cell.write(buf);
        }
        buf.writeInt(fakeplayerChunkX);
        buf.writeInt(fakeplayerChunkZ);
        buf.writeInt(fakeplayerBlockX);
        buf.writeInt(fakeplayerBlockZ);
        buf.writeLong(mapGeneration);
        buf.writeFloat(fakeplayerYaw);
        buf.writeUtf(fakeplayerName != null ? fakeplayerName : "");
        buf.writeBoolean(nameVisible);
        buf.writeBoolean(visualizeActive);
        buf.writeBoolean(visualize3DActive);
        buf.writeBoolean(canIncreaseRadius);
        buf.writeVarInt(otherChunkloaders.size());
        for (de.chunkloader.network.ChunkloaderPosition pos : otherChunkloaders) {
            pos.write(buf);
        }
        buf.writeUtf(ownerName != null ? ownerName : "");
        buf.writeBoolean(easterEgg);
        buf.writeBoolean(mobTarget);
    }

    public static ChunkMapData read(FriendlyByteBuf buf) {
        String name = buf.readUtf(32767);
        boolean enabled = buf.readBoolean();
        boolean allowMobSpawning = buf.readBoolean();
        int centerChunkX = buf.readInt();
        int centerChunkZ = buf.readInt();
        int blockY = buf.readInt();
        int chunkRadius = buf.readInt();
        int entityTickRadius = buf.readInt();
        int blockTickRadius = buf.readInt();
        int loadingRadius = buf.readInt();
        int mapWidth = buf.readVarInt();
        int mapHeight = buf.readVarInt();
        int topLeftChunkX = buf.readInt();
        int topLeftChunkZ = buf.readInt();
        String dimensionKey = buf.readUtf(32767);
        int cellCount = buf.readVarInt();
        List<ChunkMapCell> cells = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            cells.add(ChunkMapCell.read(buf));
        }
        int fakeplayerChunkX = buf.readInt();
        int fakeplayerChunkZ = buf.readInt();
        int fakeplayerBlockX = buf.readInt();
        int fakeplayerBlockZ = buf.readInt();
        long mapGeneration = buf.readLong();
        float fakeplayerYaw = buf.readFloat();
        String fakeplayerName = buf.readUtf(32767);
        boolean nameVisible = buf.readBoolean();
        boolean visualizeActive = buf.readBoolean();
        boolean visualize3DActive = buf.readBoolean();
        boolean canIncreaseRadius = buf.readBoolean();
        int otherChunkloadersCount = buf.readVarInt();
        List<de.chunkloader.network.ChunkloaderPosition> otherChunkloaders = new ArrayList<>(otherChunkloadersCount);
        for (int i = 0; i < otherChunkloadersCount; i++) {
            otherChunkloaders.add(de.chunkloader.network.ChunkloaderPosition.read(buf));
        }
        String ownerName = buf.readUtf(32767);
        boolean easterEgg = buf.readBoolean();
        boolean mobTarget = buf.readBoolean();
        return new ChunkMapData(
            name,
            enabled,
            allowMobSpawning,
            centerChunkX,
            centerChunkZ,
            blockY,
            chunkRadius,
            entityTickRadius,
            blockTickRadius,
            loadingRadius,
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
            mapGeneration,
            fakeplayerYaw,
            fakeplayerName.isEmpty() ? null : fakeplayerName,
            nameVisible,
            visualizeActive,
            visualize3DActive,
            canIncreaseRadius,
            Collections.unmodifiableList(otherChunkloaders),
            ownerName.isEmpty() ? null : ownerName,
            easterEgg,
            mobTarget
        );
    }
}
