package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RenameChunkloaderResponsePayload(
    boolean success,
    String message
) implements CustomPacketPayload {

    public static final Type<RenameChunkloaderResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "rename_chunkloader_response"));

    public static final StreamCodec<FriendlyByteBuf, RenameChunkloaderResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.success());
                buf.writeUtf(payload.message() != null ? payload.message() : "");
            },
            buf -> {
                boolean success = buf.readBoolean();
                String message = buf.readUtf();
                return new RenameChunkloaderResponsePayload(success, message.isEmpty() ? null : message);
            }
        );

    @Override
    public Type<RenameChunkloaderResponsePayload> type() {
        return TYPE;
    }
}

