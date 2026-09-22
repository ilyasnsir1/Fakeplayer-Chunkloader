package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.ChunkMapData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenChunkMapPayload(ChunkMapData data) implements CustomPayload {

    public static final CustomPayload.Id<OpenChunkMapPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "open_chunk_map"));
    public static final PacketCodec<RegistryByteBuf, OpenChunkMapPayload> CODEC =
        PacketCodec.of(OpenChunkMapPayload::write, OpenChunkMapPayload::read);

    private static void write(OpenChunkMapPayload payload, RegistryByteBuf buf) {
        payload.data().write(buf);
    }

    private static OpenChunkMapPayload read(RegistryByteBuf buf) {
        return new OpenChunkMapPayload(ChunkMapData.read(buf));
    }

    @Override
    public Id<OpenChunkMapPayload> getId() {
        return ID;
    }
}

