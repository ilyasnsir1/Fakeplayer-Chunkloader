package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DisabledChunkloadersListPayload(List<DisabledChunkloaderEntry> disabledChunkloaders) implements CustomPayload {

    public static final CustomPayload.Id<DisabledChunkloadersListPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list"));

    public static final PacketCodec<RegistryByteBuf, DisabledChunkloadersListPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeVarInt(payload.disabledChunkloaders().size());
            for (DisabledChunkloaderEntry entry : payload.disabledChunkloaders()) {
                entry.write(buf);
            }
        }, buf -> {
            int count = buf.readVarInt();
            List<DisabledChunkloaderEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(DisabledChunkloaderEntry.read(buf));
            }
            return new DisabledChunkloadersListPayload(Collections.unmodifiableList(entries));
        });

    @Override
    public CustomPayload.Id<DisabledChunkloadersListPayload> getId() {
        return ID;
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
        boolean hasWarning,
        int easterEggSkinIndex
    ) {
        public void write(net.minecraft.network.PacketByteBuf buf) {
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            buf.writeInt(blockX);
            buf.writeInt(blockY);
            buf.writeInt(blockZ);
            buf.writeString(name != null ? name : "");
            buf.writeBoolean(allowMobSpawning);
            buf.writeString(dimension != null ? dimension : "");
            buf.writeBoolean(hasWarning);
            buf.writeInt(easterEggSkinIndex);
        }

        public static DisabledChunkloaderEntry read(net.minecraft.network.PacketByteBuf buf) {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int blockX = buf.readInt();
            int blockY = buf.readInt();
            int blockZ = buf.readInt();
            String name = buf.readString(32767);
            boolean allowMobSpawning = buf.readBoolean();
            String dimension = buf.readString(32767);
            boolean hasWarning = buf.readBoolean();
            int easterEggSkinIndex = buf.readInt();
            return new DisabledChunkloaderEntry(
                chunkX, chunkZ, blockX, blockY, blockZ,
                name.isEmpty() ? null : name,
                allowMobSpawning,
                dimension.isEmpty() ? null : dimension,
                hasWarning,
                easterEggSkinIndex
            );
        }
    }
}

