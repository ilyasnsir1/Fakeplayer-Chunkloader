package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChunkplayerStatusResponsePayload(
    boolean inLoadedChunk,
    String chunkplayerName,
    int chunkX,
    int chunkZ,
    int radius,
    int distance
) implements CustomPacketPayload {

    public static final Type<ChunkplayerStatusResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "chunkplayer_status_response"));
    public static final StreamCodec<FriendlyByteBuf, ChunkplayerStatusResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.inLoadedChunk());
                buf.writeUtf(payload.chunkplayerName() != null ? payload.chunkplayerName() : "");
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeInt(payload.radius());
                buf.writeInt(payload.distance());
            },
            buf -> {
                boolean inLoadedChunk = buf.readBoolean();
                String chunkplayerName = buf.readUtf();
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                int radius = buf.readInt();
                int distance = buf.readInt();
                return new ChunkplayerStatusResponsePayload(inLoadedChunk, chunkplayerName, chunkX, chunkZ, radius, distance);
            }
        );

    @Override
    public Type<ChunkplayerStatusResponsePayload> type() {
        return TYPE;
    }
}

