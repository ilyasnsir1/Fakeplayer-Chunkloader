package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CloseChunkMapPayload() implements CustomPayload {

    public static final CustomPayload.Id<CloseChunkMapPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "close_chunk_map"));

    public static final PacketCodec<RegistryByteBuf, CloseChunkMapPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
        }, buf -> {
            return new CloseChunkMapPayload();
        });

    @Override
    public CustomPayload.Id<CloseChunkMapPayload> getId() {
        return ID;
    }
}

