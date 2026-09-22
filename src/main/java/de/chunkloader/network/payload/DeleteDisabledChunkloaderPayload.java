package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeleteDisabledChunkloaderPayload(int chunkX, int chunkZ, String dimension) implements CustomPayload {

    public static final CustomPayload.Id<DeleteDisabledChunkloaderPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "delete_disabled_chunkloader"));

    public static final PacketCodec<RegistryByteBuf, DeleteDisabledChunkloaderPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeString(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readString(256);
            return new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension);
        });

    @Override
    public CustomPayload.Id<DeleteDisabledChunkloaderPayload> getId() {
        return ID;
    }
}
