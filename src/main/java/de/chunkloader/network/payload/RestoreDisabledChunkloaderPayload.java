package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RestoreDisabledChunkloaderPayload(int chunkX, int chunkZ, String dimension) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RestoreDisabledChunkloaderPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "restore_disabled_chunkloader"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RestoreDisabledChunkloaderPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readUtf(256);
            return new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension);
        });

    @Override
    public CustomPacketPayload.Type<RestoreDisabledChunkloaderPayload> type() {
        return TYPE;
    }
}
