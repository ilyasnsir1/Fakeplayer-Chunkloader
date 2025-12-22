package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RestoreDisabledChunkloaderPayload(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<RestoreDisabledChunkloaderPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "restore_disabled_chunkloader"));
    
    public static final StreamCodec<FriendlyByteBuf, RestoreDisabledChunkloaderPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                return new RestoreDisabledChunkloaderPayload(chunkX, chunkZ);
            }
        );

    @Override
    public Type<RestoreDisabledChunkloaderPayload> type() {
        return TYPE;
    }
}

