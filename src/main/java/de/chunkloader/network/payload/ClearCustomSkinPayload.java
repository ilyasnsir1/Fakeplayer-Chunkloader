package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearCustomSkinPayload(String playerName, int requestId) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "clear_custom_skin");
    public static final Type<ClearCustomSkinPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, ClearCustomSkinPayload> STREAM_CODEC = StreamCodec.of(ClearCustomSkinPayload::write, ClearCustomSkinPayload::read);
    public ClearCustomSkinPayload(String playerName) { this(playerName, 0); }
    private static void write(FriendlyByteBuf buf, ClearCustomSkinPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", 16);
        buf.writeVarInt(payload.requestId());
    }
    private static ClearCustomSkinPayload read(FriendlyByteBuf buf) {
        return new ClearCustomSkinPayload(buf.readUtf(16), buf.readVarInt());
    }
    @Override public Type<ClearCustomSkinPayload> type() { return TYPE; }
}
