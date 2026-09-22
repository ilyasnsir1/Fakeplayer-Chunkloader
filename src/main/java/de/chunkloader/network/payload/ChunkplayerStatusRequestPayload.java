package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChunkplayerStatusRequestPayload(boolean forceResponse) implements CustomPacketPayload {

    public ChunkplayerStatusRequestPayload() {
        this(false);
    }

    public static final Type<ChunkplayerStatusRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "chunkplayer_status_request"));
    public static final StreamCodec<FriendlyByteBuf, ChunkplayerStatusRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new ChunkplayerStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Type<ChunkplayerStatusRequestPayload> type() {
        return TYPE;
    }
}

