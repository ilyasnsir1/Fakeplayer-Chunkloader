package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChunkplayerStatusResponsePayload(
    boolean inLoadedChunk,
    String chunkplayerName,
    int chunkX,
    int chunkZ,
    int radius,
    int distance
) implements CustomPayload {

    public static final CustomPayload.Id<ChunkplayerStatusResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "chunkplayer_status_response"));
    public static final PacketCodec<RegistryByteBuf, ChunkplayerStatusResponsePayload> CODEC =
        PacketCodec.of(ChunkplayerStatusResponsePayload::write, ChunkplayerStatusResponsePayload::read);

    private static void write(ChunkplayerStatusResponsePayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.inLoadedChunk());
        buf.writeString(payload.chunkplayerName() != null ? payload.chunkplayerName() : "");
        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
        buf.writeInt(payload.radius());
        buf.writeInt(payload.distance());
    }

    private static ChunkplayerStatusResponsePayload read(RegistryByteBuf buf) {
        boolean inLoadedChunk = buf.readBoolean();
        String chunkplayerName = buf.readString();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int radius = buf.readInt();
        int distance = buf.readInt();
        return new ChunkplayerStatusResponsePayload(inLoadedChunk, chunkplayerName, chunkX, chunkZ, radius, distance);
    }

    @Override
    public Id<ChunkplayerStatusResponsePayload> getId() {
        return ID;
    }
}

