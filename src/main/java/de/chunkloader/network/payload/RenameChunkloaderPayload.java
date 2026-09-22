package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Objects;

public record RenameChunkloaderPayload(
    int chunkX,
    int chunkZ,
    @NonNull String dimension,
    @NonNull String newName
) implements CustomPacketPayload {

    public static final Type<RenameChunkloaderPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "rename_chunkloader"));

    public static final StreamCodec<FriendlyByteBuf, RenameChunkloaderPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
                buf.writeUtf(payload.newName() != null ? payload.newName() : "", 16);
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                String dimension = Objects.requireNonNull(buf.readUtf(256), "dimension");
                String newName = Objects.requireNonNull(buf.readUtf(16), "newName");
                return new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName);
            }
        );

    @Override
    public Type<RenameChunkloaderPayload> type() {
        return TYPE;
    }
}
