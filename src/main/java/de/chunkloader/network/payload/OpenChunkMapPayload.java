package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.ChunkMapData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenChunkMapPayload(ChunkMapData data, boolean canModify, boolean includeAccess, boolean shared) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenChunkMapPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "open_chunk_map"));
    public static final CustomPacketPayload.Type<OpenChunkMapPayload> WITH_ACCESS =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "open_chunk_map_with_access"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChunkMapPayload> CODEC = codec(false);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChunkMapPayload> ACCESS_CODEC = codec(true);

    private static StreamCodec<RegistryFriendlyByteBuf, OpenChunkMapPayload> codec(boolean includeAccess) {
        return StreamCodec.of((buf, payload) -> {
            payload.data().write(buf);
            buf.writeBoolean(payload.canModify());
            if (includeAccess) {
                buf.writeBoolean(payload.shared());
            }
        }, buf -> new OpenChunkMapPayload(ChunkMapData.read(buf), buf.readBoolean(),
                includeAccess, includeAccess && buf.readBoolean()));
    }

    @Override
    public Type<OpenChunkMapPayload> type() {
        return includeAccess ? WITH_ACCESS : TYPE;
    }
}

