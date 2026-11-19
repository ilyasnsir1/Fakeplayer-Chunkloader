package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestClearCustomSkinPayload(String playerName, int requestId) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "request_clear_custom_skin");
    public static final Type<RequestClearCustomSkinPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, RequestClearCustomSkinPayload> STREAM_CODEC = StreamCodec.of(RequestClearCustomSkinPayload::write, RequestClearCustomSkinPayload::read);
    public RequestClearCustomSkinPayload(String playerName) { this(playerName, 0); }
    private static void write(FriendlyByteBuf buf, RequestClearCustomSkinPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
    }
    private static RequestClearCustomSkinPayload read(FriendlyByteBuf buf) {
        return new RequestClearCustomSkinPayload(buf.readUtf(16), buf.readVarInt());
    }
    @Override public Type<RequestClearCustomSkinPayload> type() { return TYPE; }
}
