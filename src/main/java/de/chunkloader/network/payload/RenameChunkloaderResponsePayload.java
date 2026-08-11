package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RenameChunkloaderResponsePayload(
    boolean success,
    String message
) implements CustomPayload {

    public static final CustomPayload.Id<RenameChunkloaderResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "rename_chunkloader_response"));

    public static final PacketCodec<RegistryByteBuf, RenameChunkloaderResponsePayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.success());
            buf.writeString(payload.message() != null ? payload.message() : "");
        }, buf -> {
            boolean success = buf.readBoolean();
            String message = buf.readString();
            return new RenameChunkloaderResponsePayload(success, message.isEmpty() ? null : message);
        });

    @Override
    public CustomPayload.Id<RenameChunkloaderResponsePayload> getId() {
        return ID;
    }
}

