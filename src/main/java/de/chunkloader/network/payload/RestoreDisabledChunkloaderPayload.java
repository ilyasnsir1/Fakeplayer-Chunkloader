package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RestoreDisabledChunkloaderPayload(int chunkX, int chunkZ, String dimension) implements CustomPacketPayload {

    public static final Type<RestoreDisabledChunkloaderPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "restore_disabled_chunkloader"));

    public static final StreamCodec<FriendlyByteBuf, RestoreDisabledChunkloaderPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                String dimension = buf.readUtf(256);
                return new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension);
            }
        );

    @Override
    public Type<RestoreDisabledChunkloaderPayload> type() {
        return TYPE;
    }
}
