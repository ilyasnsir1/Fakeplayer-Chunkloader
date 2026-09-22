package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateDisabledChunkloaderCoordsResponsePayload(
    boolean success,
    String message
) implements CustomPayload {

    public static final CustomPayload.Id<UpdateDisabledChunkloaderCoordsResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "update_disabled_chunkloader_coords_response"));

    public static final PacketCodec<RegistryByteBuf, UpdateDisabledChunkloaderCoordsResponsePayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.success());
            buf.writeString(payload.message() != null ? payload.message() : "");
        }, buf -> {
            boolean success = buf.readBoolean();
            String message = buf.readString(32767);
            return new UpdateDisabledChunkloaderCoordsResponsePayload(success, message.isEmpty() ? null : message);
        });

    @Override
    public CustomPayload.Id<UpdateDisabledChunkloaderCoordsResponsePayload> getId() {
        return ID;
    }
}

