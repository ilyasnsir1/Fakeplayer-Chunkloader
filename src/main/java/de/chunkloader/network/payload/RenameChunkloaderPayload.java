package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RenameChunkloaderPayload(
    int chunkX,
    int chunkZ,
    String dimension,
    String newName
) implements CustomPayload {

    public static final CustomPayload.Id<RenameChunkloaderPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "rename_chunkloader"));

    public static final PacketCodec<RegistryByteBuf, RenameChunkloaderPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeString(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
            buf.writeString(payload.newName() != null ? payload.newName() : "", 16);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readString(256);
            String newName = buf.readString(16);
            return new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName);
        });

    @Override
    public CustomPayload.Id<RenameChunkloaderPayload> getId() {
        return ID;
    }
}
