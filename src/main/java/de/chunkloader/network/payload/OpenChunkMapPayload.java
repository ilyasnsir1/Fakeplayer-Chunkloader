package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.network.ChunkMapData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenChunkMapPayload(ChunkMapData data, boolean canModify, boolean includeAccess, boolean shared) implements CustomPayload {

    public static final CustomPayload.Id<OpenChunkMapPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "open_chunk_map"));
    public static final CustomPayload.Id<OpenChunkMapPayload> WITH_ACCESS =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "open_chunk_map_with_access"));
    public static final PacketCodec<RegistryByteBuf, OpenChunkMapPayload> CODEC = codec(false);
    public static final PacketCodec<RegistryByteBuf, OpenChunkMapPayload> ACCESS_CODEC = codec(true);

    private static PacketCodec<RegistryByteBuf, OpenChunkMapPayload> codec(boolean includeAccess) {
        return PacketCodec.of((payload, buf) -> {
            payload.data().write(buf);
            buf.writeBoolean(payload.canModify());
            if (includeAccess) {
                buf.writeBoolean(payload.shared());
            }
        }, buf -> new OpenChunkMapPayload(ChunkMapData.read(buf), buf.readBoolean(),
                includeAccess, includeAccess && buf.readBoolean()));
    }

    @Override
    public Id<OpenChunkMapPayload> getId() {
        return includeAccess ? WITH_ACCESS : ID;
    }
}

