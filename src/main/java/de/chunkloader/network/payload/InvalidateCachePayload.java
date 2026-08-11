package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InvalidateCachePayload() implements CustomPacketPayload {

    public static final Type<InvalidateCachePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "invalidate_cache"));

    public static final StreamCodec<FriendlyByteBuf, InvalidateCachePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            buf -> {
                return new InvalidateCachePayload();
            });

    @Override
    public Type<InvalidateCachePayload> type() {
        return TYPE;
    }
}
