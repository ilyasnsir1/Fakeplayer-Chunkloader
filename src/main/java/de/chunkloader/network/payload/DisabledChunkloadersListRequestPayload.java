package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DisabledChunkloadersListRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisabledChunkloadersListRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisabledChunkloadersListRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
        }, buf -> {
            return new DisabledChunkloadersListRequestPayload();
        });

    @Override
    public CustomPacketPayload.Type<DisabledChunkloadersListRequestPayload> type() {
        return TYPE;
    }
}

