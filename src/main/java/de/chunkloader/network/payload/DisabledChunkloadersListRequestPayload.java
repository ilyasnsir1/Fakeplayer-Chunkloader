package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DisabledChunkloadersListRequestPayload() implements CustomPacketPayload {

    public static final Type<DisabledChunkloadersListRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "disabled_chunkloaders_list_request"));
    
    public static final StreamCodec<FriendlyByteBuf, DisabledChunkloadersListRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            (payload, buf) -> {
            },
            buf -> new DisabledChunkloadersListRequestPayload()
        );

    @Override
    public Type<DisabledChunkloadersListRequestPayload> type() {
        return TYPE;
    }
}

