package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateDisabledChunkloaderCoordsPayload(
    int oldChunkX,
    int oldChunkZ,
    String oldDimension,
    int newChunkX,
    int newChunkZ,
    int newBlockX,
    int newBlockY,
    int newBlockZ
) implements CustomPayload {

    public static final CustomPayload.Id<UpdateDisabledChunkloaderCoordsPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "update_disabled_chunkloader_coords"));

    public static final PacketCodec<RegistryByteBuf, UpdateDisabledChunkloaderCoordsPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.oldChunkX());
            buf.writeInt(payload.oldChunkZ());
            buf.writeString(payload.oldDimension() != null ? payload.oldDimension() : "minecraft:overworld", 256);
            buf.writeInt(payload.newChunkX());
            buf.writeInt(payload.newChunkZ());
            buf.writeInt(payload.newBlockX());
            buf.writeInt(payload.newBlockY());
            buf.writeInt(payload.newBlockZ());
        }, buf -> {
            int oldChunkX = buf.readInt();
            int oldChunkZ = buf.readInt();
            String oldDimension = buf.readString(256);
            int newChunkX = buf.readInt();
            int newChunkZ = buf.readInt();
            int newBlockX = buf.readInt();
            int newBlockY = buf.readInt();
            int newBlockZ = buf.readInt();
            return new UpdateDisabledChunkloaderCoordsPayload(
                oldChunkX, oldChunkZ, oldDimension,
                newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ
            );
        });

    @Override
    public CustomPayload.Id<UpdateDisabledChunkloaderCoordsPayload> getId() {
        return ID;
    }
}
