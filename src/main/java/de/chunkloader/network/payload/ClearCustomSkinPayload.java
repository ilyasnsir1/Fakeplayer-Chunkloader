package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClearCustomSkinPayload(String playerName, int requestId) implements CustomPayload {


    public static final CustomPayload.Id<ClearCustomSkinPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "clear_custom_skin"));

        public static final PacketCodec<RegistryByteBuf, ClearCustomSkinPayload> CODEC = PacketCodec.of(ClearCustomSkinPayload::write, ClearCustomSkinPayload::read);
    public ClearCustomSkinPayload(String playerName) { this(playerName, 0); }
    private static void write(ClearCustomSkinPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
    }
    private static ClearCustomSkinPayload read(RegistryByteBuf buf) {
        return new ClearCustomSkinPayload(buf.readString(16), buf.readVarInt());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
