package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ApplyCustomSkinPayload(String playerName, int layerMask, String model, byte[] pngBytes, int requestId) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "apply_custom_skin");
    public static final Type<ApplyCustomSkinPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, ApplyCustomSkinPayload> STREAM_CODEC = StreamCodec.of(ApplyCustomSkinPayload::write, ApplyCustomSkinPayload::read);
    public ApplyCustomSkinPayload(String playerName, int layerMask, String model, byte[] pngBytes) {
        this(playerName, layerMask, model, pngBytes, 0);
    }
    private static void write(FriendlyByteBuf buf, ApplyCustomSkinPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.layerMask());
        buf.writeUtf(payload.model() != null ? payload.model() : "", 16);
        byte[] bytes = payload.pngBytes() != null ? payload.pngBytes() : new byte[0];
        if (bytes.length > de.chunkloader.config.CustomFakePlayerSkinStore.MAX_PNG_BYTES) bytes = new byte[0];
        buf.writeByteArray(bytes);
        buf.writeVarInt(payload.requestId());
    }
    private static ApplyCustomSkinPayload read(FriendlyByteBuf buf) {
        return new ApplyCustomSkinPayload(buf.readUtf(16), buf.readVarInt(), buf.readUtf(16), buf.readByteArray(de.chunkloader.config.CustomFakePlayerSkinStore.MAX_PNG_BYTES), buf.readVarInt());
    }
    @Override public Type<ApplyCustomSkinPayload> type() { return TYPE; }
}
