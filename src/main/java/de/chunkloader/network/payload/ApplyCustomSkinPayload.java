package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ApplyCustomSkinPayload(String playerName, int layerMask, String model, byte[] pngBytes, int requestId) implements CustomPayload {


    public static final CustomPayload.Id<ApplyCustomSkinPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "apply_custom_skin"));

        public static final PacketCodec<RegistryByteBuf, ApplyCustomSkinPayload> CODEC = PacketCodec.of(ApplyCustomSkinPayload::write, ApplyCustomSkinPayload::read);
    public ApplyCustomSkinPayload(String playerName, int layerMask, String model, byte[] pngBytes) {
        this(playerName, layerMask, model, pngBytes, 0);
    }
    private static void write(ApplyCustomSkinPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.layerMask());
        buf.writeString(payload.model() != null ? payload.model() : "", 16);
        byte[] bytes = payload.pngBytes() != null ? payload.pngBytes() : new byte[0];
        if (bytes.length > de.chunkloader.config.CustomFakePlayerSkinStore.MAX_PNG_BYTES) bytes = new byte[0];
        buf.writeByteArray(bytes);
        buf.writeVarInt(payload.requestId());
    }
    private static ApplyCustomSkinPayload read(RegistryByteBuf buf) {
        return new ApplyCustomSkinPayload(buf.readString(16), buf.readVarInt(), buf.readString(16), buf.readByteArray(de.chunkloader.config.CustomFakePlayerSkinStore.MAX_PNG_BYTES), buf.readVarInt());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
