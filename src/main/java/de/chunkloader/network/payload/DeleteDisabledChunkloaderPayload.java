package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteDisabledChunkloaderPayload(int chunkX, int chunkZ, String dimension) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteDisabledChunkloaderPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "delete_disabled_chunkloader"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteDisabledChunkloaderPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readUtf(256);
            return new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension);
        });

    @Override
    public CustomPacketPayload.Type<DeleteDisabledChunkloaderPayload> type() {
        return TYPE;
    }
}
