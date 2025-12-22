package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RenameChunkloaderPayload(
    int chunkX,
    int chunkZ,
    String newName
) implements CustomPayload {

    public static final CustomPayload.Id<RenameChunkloaderPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "rename_chunkloader"));
    
    public static final PacketCodec<RegistryByteBuf, RenameChunkloaderPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeString(payload.newName(), 32767);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String newName = buf.readString();
            return new RenameChunkloaderPayload(chunkX, chunkZ, newName);
        });

    @Override
    public CustomPayload.Id<RenameChunkloaderPayload> getId() {
        return ID;
    }
}

