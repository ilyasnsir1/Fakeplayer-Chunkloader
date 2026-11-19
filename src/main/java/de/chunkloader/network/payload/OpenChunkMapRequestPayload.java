package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenChunkMapRequestPayload(int chunkX, int chunkZ, String dimension) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenChunkMapRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "open_chunk_map_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChunkMapRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
        }, buf -> new OpenChunkMapRequestPayload(buf.readInt(), buf.readInt(), buf.readUtf(256)));

    @Override
    public Type<OpenChunkMapRequestPayload> type() {
        return TYPE;
    }
}
