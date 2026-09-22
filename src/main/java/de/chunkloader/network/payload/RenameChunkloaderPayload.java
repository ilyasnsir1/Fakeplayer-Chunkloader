package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RenameChunkloaderPayload(
    int chunkX,
    int chunkZ,
    String dimension,
    String newName
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameChunkloaderPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "rename_chunkloader"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameChunkloaderPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
            buf.writeUtf(payload.newName() != null ? payload.newName() : "", 16);
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readUtf(256);
            String newName = buf.readUtf(16);
            return new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName);
        });

    @Override
    public CustomPacketPayload.Type<RenameChunkloaderPayload> type() {
        return TYPE;
    }
}
