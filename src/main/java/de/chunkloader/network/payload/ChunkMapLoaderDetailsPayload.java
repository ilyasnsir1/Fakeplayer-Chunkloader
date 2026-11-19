package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ChunkMapLoaderDetailsPayload(int chunkX, int chunkZ, String dimension,
        int activeBlockers, int disabledBlockers, List<DisabledArea> disabledAreas)
        implements CustomPacketPayload {

    public static final Type<ChunkMapLoaderDetailsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "chunk_map_loader_areas"));
    public static final StreamCodec<FriendlyByteBuf, ChunkMapLoaderDetailsPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeUtf(payload.dimension());
                buf.writeVarInt(payload.activeBlockers());
                buf.writeVarInt(payload.disabledBlockers());
                buf.writeVarInt(payload.disabledAreas().size());
                for (DisabledArea area : payload.disabledAreas()) {
                    buf.writeInt(area.chunkX());
                    buf.writeInt(area.chunkZ());
                    buf.writeVarInt(area.radius());
                    buf.writeUtf(area.name());
                }
            }, ChunkMapLoaderDetailsPayload::read);

    public ChunkMapLoaderDetailsPayload {
        disabledAreas = List.copyOf(disabledAreas);
    }

    private static ChunkMapLoaderDetailsPayload read(FriendlyByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        String dimension = buf.readUtf();
        int activeBlockers = buf.readVarInt();
        int disabledBlockers = buf.readVarInt();
        int areaCount = buf.readVarInt();
        List<DisabledArea> disabledAreas = new ArrayList<>();
        for (int i = 0; i < areaCount; i++) {
            disabledAreas.add(new DisabledArea(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readUtf()));
        }
        return new ChunkMapLoaderDetailsPayload(chunkX, chunkZ, dimension, activeBlockers, disabledBlockers, disabledAreas);
    }

    public record DisabledArea(int chunkX, int chunkZ, int radius, String name) {
        public boolean contains(int x, int z) {
            return Math.abs((long) x - chunkX) <= radius && Math.abs((long) z - chunkZ) <= radius;
        }
    }

    @Override
    public Type<ChunkMapLoaderDetailsPayload> type() {
        return TYPE;
    }
}
