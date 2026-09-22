package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InvalidateCachePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InvalidateCachePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "invalidate_cache"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvalidateCachePayload> CODEC =
        StreamCodec.of((buf, payload) -> {
        }, buf -> {
            return new InvalidateCachePayload();
        });

    @Override
    public CustomPacketPayload.Type<InvalidateCachePayload> type() {
        return TYPE;
    }
}

