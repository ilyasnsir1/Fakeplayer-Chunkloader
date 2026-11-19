package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ChunkMapLoaderDetailsPayload(int chunkX, int chunkZ, String dimension,
        int activeBlockers, int disabledBlockers, List<DisabledArea> disabledAreas)
        implements CustomPayload {

    public static final Id<ChunkMapLoaderDetailsPayload> ID =
            new Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "chunk_map_loader_areas"));
    public static final PacketCodec<RegistryByteBuf, ChunkMapLoaderDetailsPayload> CODEC =
            PacketCodec.of((payload, buf) -> {
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeString(payload.dimension());
                buf.writeVarInt(payload.activeBlockers());
                buf.writeVarInt(payload.disabledBlockers());
                buf.writeVarInt(payload.disabledAreas().size());
                for (DisabledArea area : payload.disabledAreas()) {
                    buf.writeInt(area.chunkX());
                    buf.writeInt(area.chunkZ());
                    buf.writeVarInt(area.radius());
                    buf.writeString(area.name());
                }
            }, ChunkMapLoaderDetailsPayload::read);

    public ChunkMapLoaderDetailsPayload {
        disabledAreas = List.copyOf(disabledAreas);
    }

    private static ChunkMapLoaderDetailsPayload read(RegistryByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        String dimension = buf.readString();
        int activeBlockers = buf.readVarInt();
        int disabledBlockers = buf.readVarInt();
        int areaCount = buf.readVarInt();
        List<DisabledArea> disabledAreas = new ArrayList<>();
        for (int i = 0; i < areaCount; i++) {
            disabledAreas.add(new DisabledArea(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readString()));
        }
        return new ChunkMapLoaderDetailsPayload(chunkX, chunkZ, dimension, activeBlockers, disabledBlockers, disabledAreas);
    }

    public record DisabledArea(int chunkX, int chunkZ, int radius, String name) {
        public boolean contains(int x, int z) {
            return Math.abs((long) x - chunkX) <= radius && Math.abs((long) z - chunkZ) <= radius;
        }
    }

    @Override
    public Id<ChunkMapLoaderDetailsPayload> getId() {
        return ID;
    }
}
