package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActiveChunkloadersListRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActiveChunkloadersListRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "active_chunkloaders_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveChunkloadersListRequestPayload> CODEC =
        StreamCodec.unit(new ActiveChunkloadersListRequestPayload());

    @Override
    public Type<ActiveChunkloadersListRequestPayload> type() {
        return TYPE;
    }
}
