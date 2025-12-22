package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.network.ChunkMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenChunkMapPayload(ChunkMapData data) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "open_chunk_map");
    public static final Type<OpenChunkMapPayload> TYPE = new Type<>(ID);
    
    public static final StreamCodec<FriendlyByteBuf, OpenChunkMapPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> payload.data().write(buf),
        buf -> new OpenChunkMapPayload(ChunkMapData.read(buf))
    );
    
    @Override
    public Type<OpenChunkMapPayload> type() {
        return TYPE;
    }
}

