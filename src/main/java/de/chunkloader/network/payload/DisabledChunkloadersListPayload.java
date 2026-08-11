package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DisabledChunkloadersListPayload(List<DisabledChunkloaderEntry> disabledChunkloaders) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisabledChunkloadersListPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisabledChunkloadersListPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
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
    public CustomPacketPayload.Type<DisabledChunkloadersListPayload> type() {
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
        boolean hasWarning,
        int easterEggSkinIndex
    ) {
        public void write(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            buf.writeInt(blockX);
            buf.writeInt(blockY);
            buf.writeInt(blockZ);
            buf.writeUtf(name != null ? name : "");
            buf.writeBoolean(allowMobSpawning);
            buf.writeUtf(dimension != null ? dimension : "");
            buf.writeBoolean(hasWarning);
            buf.writeInt(easterEggSkinIndex);
        }

        public static DisabledChunkloaderEntry read(net.minecraft.network.FriendlyByteBuf buf) {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int blockX = buf.readInt();
            int blockY = buf.readInt();
            int blockZ = buf.readInt();
            String name = buf.readUtf(32767);
            boolean allowMobSpawning = buf.readBoolean();
            String dimension = buf.readUtf(32767);
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

