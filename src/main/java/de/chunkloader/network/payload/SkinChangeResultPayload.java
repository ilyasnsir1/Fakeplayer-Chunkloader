package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SkinChangeResultPayload(String playerName, int requestId, boolean removed, boolean success, String message) implements CustomPacketPayload {


    public static final CustomPacketPayload.Type<SkinChangeResultPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "skin_change_result"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SkinChangeResultPayload> CODEC = StreamCodec.of(SkinChangeResultPayload::write, SkinChangeResultPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SkinChangeResultPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
        buf.writeBoolean(payload.removed());
        buf.writeBoolean(payload.success());
        buf.writeUtf(payload.message() != null ? payload.message() : "", 256);
    }
    private static SkinChangeResultPayload read(RegistryFriendlyByteBuf buf) {
        return new SkinChangeResultPayload(buf.readUtf(16), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256));
    }
    @Override public Type<SkinChangeResultPayload> type() { return TYPE; }
}
