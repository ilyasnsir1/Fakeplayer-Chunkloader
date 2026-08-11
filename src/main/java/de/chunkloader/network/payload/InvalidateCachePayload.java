package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record InvalidateCachePayload() implements CustomPayload {

    public static final CustomPayload.Id<InvalidateCachePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "invalidate_cache"));

    public static final PacketCodec<RegistryByteBuf, InvalidateCachePayload> CODEC =
        PacketCodec.of((payload, buf) -> {
        }, buf -> {
            return new InvalidateCachePayload();
        });

    @Override
    public CustomPayload.Id<InvalidateCachePayload> getId() {
        return ID;
    }
}

