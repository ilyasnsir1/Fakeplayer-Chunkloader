package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChunkplayerStatusRequestPayload(boolean forceResponse) implements CustomPayload {

    public ChunkplayerStatusRequestPayload() {
        this(false);
    }

    public static final CustomPayload.Id<ChunkplayerStatusRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "chunkplayer_status_request"));
    public static final PacketCodec<RegistryByteBuf, ChunkplayerStatusRequestPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new ChunkplayerStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Id<ChunkplayerStatusRequestPayload> getId() {
        return ID;
    }
}

