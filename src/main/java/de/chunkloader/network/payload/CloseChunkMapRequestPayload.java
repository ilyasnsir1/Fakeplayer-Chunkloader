package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CloseChunkMapRequestPayload() implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "close_chunk_map_request");
    public static final Type<CloseChunkMapRequestPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, CloseChunkMapRequestPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
        },
        buf -> new CloseChunkMapRequestPayload()
    );

    @Override
    public Type<CloseChunkMapRequestPayload> type() {
        return TYPE;
    }
}
