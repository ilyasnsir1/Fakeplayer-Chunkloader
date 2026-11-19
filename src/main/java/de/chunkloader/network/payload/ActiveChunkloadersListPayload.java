package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActiveChunkloadersListPayload(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries) implements CustomPacketPayload {
    public static final Type<ActiveChunkloadersListPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "active_chunkloaders_list"));
    public static final StreamCodec<FriendlyByteBuf, ActiveChunkloadersListPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.entries().size());
            for (DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry : payload.entries()) {
                DisabledChunkloadersListPayload.ENTRY_STREAM_CODEC.encode(buf, entry);
            }
        }, buf -> {
            int count = buf.readVarInt();
            List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(DisabledChunkloadersListPayload.ENTRY_STREAM_CODEC.decode(buf));
            }
            return new ActiveChunkloadersListPayload(Collections.unmodifiableList(entries));
        });

    @Override
    public Type<ActiveChunkloadersListPayload> type() {
        return TYPE;
    }
}
