package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SkinChangeResultPayload(String playerName, int requestId, boolean removed, boolean success, String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "skin_change_result");
    public static final Type<SkinChangeResultPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, SkinChangeResultPayload> STREAM_CODEC = StreamCodec.of(SkinChangeResultPayload::write, SkinChangeResultPayload::read);

    private static void write(FriendlyByteBuf buf, SkinChangeResultPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
        buf.writeBoolean(payload.removed());
        buf.writeBoolean(payload.success());
        buf.writeUtf(payload.message() != null ? payload.message() : "", 256);
    }
    private static SkinChangeResultPayload read(FriendlyByteBuf buf) {
        return new SkinChangeResultPayload(buf.readUtf(16), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256));
    }
    @Override public Type<SkinChangeResultPayload> type() { return TYPE; }
}
