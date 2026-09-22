package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.CustomFakePlayerSkinStore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ApplyCustomSkinPayload(
    String playerName,
    int layerMask,
    String model,
    byte[] pngBytes
) implements CustomPayload {

    public static final CustomPayload.Id<ApplyCustomSkinPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "apply_custom_skin"));

    public static final PacketCodec<RegistryByteBuf, ApplyCustomSkinPayload> CODEC =
        PacketCodec.of(ApplyCustomSkinPayload::write, ApplyCustomSkinPayload::read);

    private static void write(ApplyCustomSkinPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.layerMask());
        buf.writeString(payload.model() != null ? payload.model() : "", 16);
        byte[] bytes = payload.pngBytes() != null ? payload.pngBytes() : new byte[0];
        if (bytes.length > CustomFakePlayerSkinStore.MAX_PNG_BYTES) {
            bytes = new byte[0];
        }
        buf.writeByteArray(bytes);
    }

    private static ApplyCustomSkinPayload read(RegistryByteBuf buf) {
        String playerName = buf.readString(16);
        int layerMask = buf.readVarInt();
        String model = buf.readString(16);
        byte[] pngBytes = buf.readByteArray(CustomFakePlayerSkinStore.MAX_PNG_BYTES);
        return new ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
