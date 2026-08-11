package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.config.CustomFakePlayerSkinStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ApplyCustomSkinPayload(
    String playerName,
    int layerMask,
    String model,
    byte[] pngBytes
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "apply_custom_skin");
    public static final Type<ApplyCustomSkinPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ApplyCustomSkinPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
            buf.writeVarInt(payload.layerMask());
            buf.writeUtf(payload.model() != null ? payload.model() : "", 16);
            byte[] bytes = payload.pngBytes() != null ? payload.pngBytes() : new byte[0];
            if (bytes.length > CustomFakePlayerSkinStore.MAX_PNG_BYTES) {
                bytes = new byte[0];
            }
            buf.writeByteArray(bytes);
        },
        buf -> new ApplyCustomSkinPayload(
            buf.readUtf(16),
            buf.readVarInt(),
            buf.readUtf(16),
            buf.readByteArray(CustomFakePlayerSkinStore.MAX_PNG_BYTES)
        )
    );

    @Override
    public Type<ApplyCustomSkinPayload> type() {
        return TYPE;
    }
}
