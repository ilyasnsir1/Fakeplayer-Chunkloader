package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChunkplayerStatusRequestPayload(boolean forceResponse) implements CustomPacketPayload {

    public ChunkplayerStatusRequestPayload() {
        this(false);
    }

    public static final CustomPacketPayload.Type<ChunkplayerStatusRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "chunkplayer_status_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkplayerStatusRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new ChunkplayerStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Type<ChunkplayerStatusRequestPayload> type() {
        return TYPE;
    }
}

