package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChunkplayerStatusResponsePayload(
    boolean inLoadedChunk,
    String chunkplayerName,
    int chunkX,
    int chunkZ,
    int radius,
    int distance
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChunkplayerStatusResponsePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "chunkplayer_status_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkplayerStatusResponsePayload> CODEC =
        StreamCodec.of(ChunkplayerStatusResponsePayload::write, ChunkplayerStatusResponsePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ChunkplayerStatusResponsePayload payload) {
        buf.writeBoolean(payload.inLoadedChunk());
        buf.writeUtf(payload.chunkplayerName() != null ? payload.chunkplayerName() : "");
        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
        buf.writeInt(payload.radius());
        buf.writeInt(payload.distance());
    }

    private static ChunkplayerStatusResponsePayload read(RegistryFriendlyByteBuf buf) {
        boolean inLoadedChunk = buf.readBoolean();
        String chunkplayerName = buf.readUtf();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int radius = buf.readInt();
        int distance = buf.readInt();
        return new ChunkplayerStatusResponsePayload(inLoadedChunk, chunkplayerName, chunkX, chunkZ, radius, distance);
    }

    @Override
    public Type<ChunkplayerStatusResponsePayload> type() {
        return TYPE;
    }
}

