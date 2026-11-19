package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActiveChunkloadersListRequestPayload() implements CustomPacketPayload {
    public static final Type<ActiveChunkloadersListRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "active_chunkloaders_list_request"));
    public static final StreamCodec<FriendlyByteBuf, ActiveChunkloadersListRequestPayload> STREAM_CODEC =
        StreamCodec.of((payload, buf) -> { }, buf -> new ActiveChunkloadersListRequestPayload());

    @Override
    public Type<ActiveChunkloadersListRequestPayload> type() {
        return TYPE;
    }
}
