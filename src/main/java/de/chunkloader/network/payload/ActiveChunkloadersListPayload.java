package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ActiveChunkloadersListPayload(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries, boolean includeShares) implements CustomPayload {
    public static final CustomPayload.Id<ActiveChunkloadersListPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "active_chunkloaders_list"));
    public static final CustomPayload.Id<ActiveChunkloadersListPayload> WITH_SHARES =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "active_chunkloaders_list_with_shares"));

    public static final PacketCodec<RegistryByteBuf, ActiveChunkloadersListPayload> CODEC = codec(false);
    public static final PacketCodec<RegistryByteBuf, ActiveChunkloadersListPayload> SHARES_CODEC = codec(true);

    private static PacketCodec<RegistryByteBuf, ActiveChunkloadersListPayload> codec(boolean includeShares) {
        return PacketCodec.of((payload, buf) -> {
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
    public Id<ActiveChunkloadersListPayload> getId() {
        return includeShares ? WITH_SHARES : ID;
    }
}
