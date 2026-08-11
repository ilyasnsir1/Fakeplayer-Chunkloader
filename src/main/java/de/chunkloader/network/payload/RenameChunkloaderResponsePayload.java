package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RenameChunkloaderResponsePayload(
    boolean success,
    String message
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameChunkloaderResponsePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "rename_chunkloader_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameChunkloaderResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeBoolean(payload.success());
            buf.writeUtf(payload.message() != null ? payload.message() : "");
        }, buf -> {
            boolean success = buf.readBoolean();
            String message = buf.readUtf();
            return new RenameChunkloaderResponsePayload(success, message.isEmpty() ? null : message);
        });

    @Override
    public CustomPacketPayload.Type<RenameChunkloaderResponsePayload> type() {
        return TYPE;
    }
}

