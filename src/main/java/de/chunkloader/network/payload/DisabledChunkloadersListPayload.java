package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DisabledChunkloadersListPayload(List<DisabledChunkloaderEntry> disabledChunkloaders) implements CustomPacketPayload {

    public static final Type<DisabledChunkloadersListPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "disabled_chunkloaders_list"));

    public static final StreamCodec<FriendlyByteBuf, DisabledChunkloaderEntry> ENTRY_STREAM_CODEC =
        StreamCodec.of(
            (buf, entry) -> {
                buf.writeInt(entry.chunkX());
                buf.writeInt(entry.chunkZ());
                buf.writeInt(entry.blockX());
                buf.writeInt(entry.blockY());
                buf.writeInt(entry.blockZ());
                buf.writeUtf(entry.name() != null ? entry.name() : "");
                buf.writeBoolean(entry.allowMobSpawning());
                buf.writeUtf(entry.dimension() != null ? entry.dimension() : "");
                buf.writeBoolean(entry.isFakeplayer());
                buf.writeInt(entry.easterEggSkinIndex());
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                int blockX = buf.readInt();
                int blockY = buf.readInt();
                int blockZ = buf.readInt();
                String name = buf.readUtf();
                boolean allowMobSpawning = buf.readBoolean();
                String dimension = buf.readUtf();
                boolean isFakeplayer = buf.readBoolean();
                int easterEggSkinIndex = buf.readInt();
                return new DisabledChunkloaderEntry(
                    chunkX, chunkZ, blockX, blockY, blockZ,
                    name.isEmpty() ? null : name,
                    allowMobSpawning,
                    dimension.isEmpty() ? null : dimension,
                    isFakeplayer,
                    easterEggSkinIndex
                );
            }
        );

    public static final StreamCodec<FriendlyByteBuf, DisabledChunkloadersListPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.disabledChunkloaders().size());
                for (DisabledChunkloaderEntry entry : payload.disabledChunkloaders()) {
                    ENTRY_STREAM_CODEC.encode(buf, entry);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<DisabledChunkloaderEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(ENTRY_STREAM_CODEC.decode(buf));
                }
                return new DisabledChunkloadersListPayload(Collections.unmodifiableList(entries));
            }
        );

    @Override
    public Type<DisabledChunkloadersListPayload> type() {
        return TYPE;
    }

    public record DisabledChunkloaderEntry(
        int chunkX,
        int chunkZ,
        int blockX,
        int blockY,
        int blockZ,
        String name,
        boolean allowMobSpawning,
        String dimension,
        boolean isFakeplayer,
        int easterEggSkinIndex
    ) {
        public boolean hasWarning() {
            return false;
        }
    }
}

