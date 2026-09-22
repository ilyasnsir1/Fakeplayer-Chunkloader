package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.CustomFakePlayerSkinStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ApplyCustomSkinPayload(
    String playerName,
    int layerMask,
    String model,
    byte[] pngBytes
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ApplyCustomSkinPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "apply_custom_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyCustomSkinPayload> CODEC =
        StreamCodec.of(ApplyCustomSkinPayload::write, ApplyCustomSkinPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ApplyCustomSkinPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.layerMask());
        buf.writeUtf(payload.model() != null ? payload.model() : "", 16);
        byte[] bytes = payload.pngBytes() != null ? payload.pngBytes() : new byte[0];
        if (bytes.length > CustomFakePlayerSkinStore.MAX_PNG_BYTES) {
            bytes = new byte[0];
        }
        buf.writeByteArray(bytes);
    }

    private static ApplyCustomSkinPayload read(RegistryFriendlyByteBuf buf) {
        String playerName = buf.readUtf(16);
        int layerMask = buf.readVarInt();
        String model = buf.readUtf(16);
        byte[] pngBytes = buf.readByteArray(CustomFakePlayerSkinStore.MAX_PNG_BYTES);
        return new ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
