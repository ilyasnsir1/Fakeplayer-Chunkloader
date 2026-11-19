package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActiveChunkloadersListPayload(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries, boolean includeShares) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActiveChunkloadersListPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "active_chunkloaders_list"));
    public static final CustomPacketPayload.Type<ActiveChunkloadersListPayload> WITH_SHARES =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "active_chunkloaders_list_with_shares"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveChunkloadersListPayload> CODEC = codec(false);
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveChunkloadersListPayload> SHARES_CODEC = codec(true);

    private static StreamCodec<RegistryFriendlyByteBuf, ActiveChunkloadersListPayload> codec(boolean includeShares) {
        return StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.entries().size());
            for (DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry : payload.entries()) {
                entry.write(buf, includeShares);
            }
        }, buf -> {
            int count = buf.readVarInt();
            List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(DisabledChunkloadersListPayload.DisabledChunkloaderEntry.read(buf, includeShares));
            }
            return new ActiveChunkloadersListPayload(Collections.unmodifiableList(entries), includeShares);
        });
    }

    @Override
    public Type<ActiveChunkloadersListPayload> type() {
        return includeShares ? WITH_SHARES : TYPE;
    }
}
