package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestClearCustomSkinPayload(String playerName) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "request_clear_custom_skin");
    public static final Type<RequestClearCustomSkinPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RequestClearCustomSkinPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.playerName() != null ? payload.playerName() : ""),
        buf -> new RequestClearCustomSkinPayload(buf.readUtf())
    );

    @Override
    public Type<RequestClearCustomSkinPayload> type() {
        return TYPE;
    }
}
