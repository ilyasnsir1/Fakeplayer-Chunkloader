package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateDisabledChunkloaderCoordsResponsePayload(
    boolean success,
    String message
) implements CustomPacketPayload {

    public static final Type<UpdateDisabledChunkloaderCoordsResponsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "update_disabled_chunkloader_coords_response"));
    
    public static final StreamCodec<FriendlyByteBuf, UpdateDisabledChunkloaderCoordsResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.success());
                buf.writeUtf(payload.message() != null ? payload.message() : "");
            },
            buf -> {
                boolean success = buf.readBoolean();
                String message = buf.readUtf();
                return new UpdateDisabledChunkloaderCoordsResponsePayload(success, message.isEmpty() ? null : message);
            }
        );

    @Override
    public Type<UpdateDisabledChunkloaderCoordsResponsePayload> type() {
        return TYPE;
    }
}

