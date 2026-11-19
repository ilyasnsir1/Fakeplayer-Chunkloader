package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SkinChangeResultPayload(String playerName, int requestId, boolean removed, boolean success, String message) implements CustomPayload {


    public static final CustomPayload.Id<SkinChangeResultPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "skin_change_result"));

        public static final PacketCodec<RegistryByteBuf, SkinChangeResultPayload> CODEC = PacketCodec.of(SkinChangeResultPayload::write, SkinChangeResultPayload::read);

    private static void write(SkinChangeResultPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
        buf.writeBoolean(payload.removed());
        buf.writeBoolean(payload.success());
        buf.writeString(payload.message() != null ? payload.message() : "", 256);
    }
    private static SkinChangeResultPayload read(RegistryByteBuf buf) {
        return new SkinChangeResultPayload(buf.readString(16), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readString(256));
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
