package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DisabledChunkloadersListPayload(List<DisabledChunkloaderEntry> disabledChunkloaders, boolean includeShares) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisabledChunkloadersListPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list"));

    public static final CustomPacketPayload.Type<DisabledChunkloadersListPayload> WITH_SHARES =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "disabled_chunkloaders_list_with_shares"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisabledChunkloadersListPayload> CODEC = codec(false);
    public static final StreamCodec<RegistryFriendlyByteBuf, DisabledChunkloadersListPayload> SHARES_CODEC = codec(true);

    private static StreamCodec<RegistryFriendlyByteBuf, DisabledChunkloadersListPayload> codec(boolean includeShares) {
        return StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.disabledChunkloaders().size());
            for (DisabledChunkloaderEntry entry : payload.disabledChunkloaders()) {
                entry.write(buf, includeShares);
            }
        }, buf -> {
            int count = buf.readVarInt();
            List<DisabledChunkloaderEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(DisabledChunkloaderEntry.read(buf, includeShares));
            }
            return new DisabledChunkloadersListPayload(Collections.unmodifiableList(entries), includeShares);
        });
    }

    @Override
    public CustomPacketPayload.Type<DisabledChunkloadersListPayload> type() {
        return includeShares ? WITH_SHARES : TYPE;
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
        int easterEggSkinIndex,
        String sharedBy
    ) {
        public void write(net.minecraft.network.FriendlyByteBuf buf, boolean includeShares) {
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
            if (includeShares) {
                buf.writeUtf(sharedBy != null ? sharedBy : "");
            }
        }

        public static DisabledChunkloaderEntry read(net.minecraft.network.FriendlyByteBuf buf, boolean includeShares) {
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
            String sharedBy = includeShares ? buf.readUtf() : "";
            return new DisabledChunkloaderEntry(
                chunkX, chunkZ, blockX, blockY, blockZ,
                name.isEmpty() ? null : name,
                allowMobSpawning,
                dimension.isEmpty() ? null : dimension,
                hasWarning,
                easterEggSkinIndex,
                sharedBy.isEmpty() ? null : sharedBy
            );
        }
    }
}

