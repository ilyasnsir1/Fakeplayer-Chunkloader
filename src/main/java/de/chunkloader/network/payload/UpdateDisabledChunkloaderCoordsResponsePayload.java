package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateDisabledChunkloaderCoordsResponsePayload(
    boolean success,
    String message
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateDisabledChunkloaderCoordsResponsePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "update_disabled_chunkloader_coords_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDisabledChunkloaderCoordsResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeBoolean(payload.success());
            buf.writeUtf(payload.message() != null ? payload.message() : "");
        }, buf -> {
            boolean success = buf.readBoolean();
            String message = buf.readUtf(32767);
            return new UpdateDisabledChunkloaderCoordsResponsePayload(success, message.isEmpty() ? null : message);
        });

    @Override
    public CustomPacketPayload.Type<UpdateDisabledChunkloaderCoordsResponsePayload> type() {
        return TYPE;
    }
}

