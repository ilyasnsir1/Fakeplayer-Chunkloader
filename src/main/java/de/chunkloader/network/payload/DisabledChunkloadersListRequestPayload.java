package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DisabledChunkloadersListRequestPayload() implements CustomPayload {

    public static final CustomPayload.Id<DisabledChunkloadersListRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list_request"));

    public static final PacketCodec<RegistryByteBuf, DisabledChunkloadersListRequestPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
        }, buf -> {
            return new DisabledChunkloadersListRequestPayload();
        });

    @Override
    public CustomPayload.Id<DisabledChunkloadersListRequestPayload> getId() {
        return ID;
    }
}

