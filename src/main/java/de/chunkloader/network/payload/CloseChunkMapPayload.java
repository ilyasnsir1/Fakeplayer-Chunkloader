package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CloseChunkMapPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseChunkMapPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "close_chunk_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseChunkMapPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
        }, buf -> {
            return new CloseChunkMapPayload();
        });

    @Override
    public CustomPacketPayload.Type<CloseChunkMapPayload> type() {
        return TYPE;
    }
}

