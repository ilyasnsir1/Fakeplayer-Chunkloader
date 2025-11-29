package de.chunkloader.network;

import net.minecraft.network.PacketByteBuf;

public record ChunkMapCell(int offsetX, int offsetZ, boolean loaded, boolean withinRange, boolean occupiedByOther, boolean simulatedByFakeplayer, String simulatingFakeplayerName) {

    public void write(PacketByteBuf buf) {
        buf.writeByte(offsetX);
        buf.writeByte(offsetZ);
        buf.writeBoolean(loaded);
        buf.writeBoolean(withinRange);
        buf.writeBoolean(occupiedByOther);
        buf.writeBoolean(simulatedByFakeplayer);
        buf.writeString(simulatingFakeplayerName != null ? simulatingFakeplayerName : "");
    }

    public static ChunkMapCell read(PacketByteBuf buf) {
        int offsetX = buf.readByte();
        int offsetZ = buf.readByte();
        boolean loaded = buf.readBoolean();
        boolean withinRange = buf.readBoolean();
        boolean occupied = buf.readBoolean();
        boolean simulated = buf.readBoolean();
        String fakeplayerName = buf.readString(32767);
        return new ChunkMapCell(offsetX, offsetZ, loaded, withinRange, occupied, simulated, fakeplayerName.isEmpty() ? null : fakeplayerName);
    }
}

