package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloseChunkMapPayload() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "close_chunk_map");
    public static final Type<CloseChunkMapPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, CloseChunkMapPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
        },
        buf -> new CloseChunkMapPayload()
    );

    @Override
    public Type<CloseChunkMapPayload> type() {
        return TYPE;
    }
}

