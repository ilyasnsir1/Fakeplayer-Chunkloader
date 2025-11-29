package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RestoreDisabledChunkloaderPayload(int chunkX, int chunkZ) implements CustomPayload {

    public static final CustomPayload.Id<RestoreDisabledChunkloaderPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "restore_disabled_chunkloader"));
    
    public static final PacketCodec<RegistryByteBuf, RestoreDisabledChunkloaderPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            return new RestoreDisabledChunkloaderPayload(chunkX, chunkZ);
        });

    @Override
    public CustomPayload.Id<RestoreDisabledChunkloaderPayload> getId() {
        return ID;
    }
}

