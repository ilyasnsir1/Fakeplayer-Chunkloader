package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.ChunkMapData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenChunkMapPayload(ChunkMapData data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenChunkMapPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "open_chunk_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChunkMapPayload> CODEC =
        StreamCodec.of(OpenChunkMapPayload::write, OpenChunkMapPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenChunkMapPayload payload) {
        payload.data().write(buf);
    }

    private static OpenChunkMapPayload read(RegistryFriendlyByteBuf buf) {
        return new OpenChunkMapPayload(ChunkMapData.read(buf));
    }

    @Override
    public Type<OpenChunkMapPayload> type() {
        return TYPE;
    }
}

