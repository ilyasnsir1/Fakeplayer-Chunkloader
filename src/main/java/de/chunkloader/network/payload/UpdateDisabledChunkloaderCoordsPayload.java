package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateDisabledChunkloaderCoordsPayload(
    int oldChunkX,
    int oldChunkZ,
    int newChunkX,
    int newChunkZ,
    int newBlockX,
    int newBlockY,
    int newBlockZ
) implements CustomPacketPayload {

    public static final Type<UpdateDisabledChunkloaderCoordsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "update_disabled_chunkloader_coords"));
    
    public static final StreamCodec<FriendlyByteBuf, UpdateDisabledChunkloaderCoordsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.oldChunkX());
                buf.writeInt(payload.oldChunkZ());
                buf.writeInt(payload.newChunkX());
                buf.writeInt(payload.newChunkZ());
                buf.writeInt(payload.newBlockX());
                buf.writeInt(payload.newBlockY());
                buf.writeInt(payload.newBlockZ());
            },
            buf -> {
                int oldChunkX = buf.readInt();
                int oldChunkZ = buf.readInt();
                int newChunkX = buf.readInt();
                int newChunkZ = buf.readInt();
                int newBlockX = buf.readInt();
                int newBlockY = buf.readInt();
                int newBlockZ = buf.readInt();
                return new UpdateDisabledChunkloaderCoordsPayload(
                    oldChunkX, oldChunkZ,
                    newChunkX, newChunkZ,
                    newBlockX, newBlockY, newBlockZ
                );
            }
        );

    @Override
    public Type<UpdateDisabledChunkloaderCoordsPayload> type() {
        return TYPE;
    }
}

