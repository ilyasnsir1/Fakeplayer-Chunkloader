package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteDisabledChunkloaderPayload(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<DeleteDisabledChunkloaderPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "delete_disabled_chunkloader"));
    
    public static final StreamCodec<FriendlyByteBuf, DeleteDisabledChunkloaderPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                return new DeleteDisabledChunkloaderPayload(chunkX, chunkZ);
            }
        );

    @Override
    public Type<DeleteDisabledChunkloaderPayload> type() {
        return TYPE;
    }
}

