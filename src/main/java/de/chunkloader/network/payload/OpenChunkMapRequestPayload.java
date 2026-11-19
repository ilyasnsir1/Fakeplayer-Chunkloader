package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenChunkMapRequestPayload(int chunkX, int chunkZ, String dimension) implements CustomPacketPayload {
    public static final Type<OpenChunkMapRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "open_chunk_map_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenChunkMapRequestPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld");
        }, buf -> new OpenChunkMapRequestPayload(buf.readInt(), buf.readInt(), buf.readUtf()));
    @Override public Type<OpenChunkMapRequestPayload> type() { return TYPE; }
}
