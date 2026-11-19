package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ActiveChunkloadersListRequestPayload() implements CustomPayload {
    public static final CustomPayload.Id<ActiveChunkloadersListRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "active_chunkloaders_list_request"));
    public static final PacketCodec<RegistryByteBuf, ActiveChunkloadersListRequestPayload> CODEC =
        PacketCodec.unit(new ActiveChunkloadersListRequestPayload());

    @Override
    public CustomPayload.Id<ActiveChunkloadersListRequestPayload> getId() {
        return ID;
    }
}
