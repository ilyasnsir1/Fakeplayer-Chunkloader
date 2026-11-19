package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenChunkMapRequestPayload(int chunkX, int chunkZ, String dimension) implements CustomPayload {
    public static final CustomPayload.Id<OpenChunkMapRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "open_chunk_map_request"));
    public static final PacketCodec<RegistryByteBuf, OpenChunkMapRequestPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeString(payload.dimension() != null ? payload.dimension() : "minecraft:overworld");
        }, buf -> new OpenChunkMapRequestPayload(buf.readInt(), buf.readInt(), buf.readString()));

    @Override
    public CustomPayload.Id<OpenChunkMapRequestPayload> getId() {
        return ID;
    }
}
