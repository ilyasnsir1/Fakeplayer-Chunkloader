package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearCustomSkinPayload(String playerName) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "clear_custom_skin");
    public static final Type<ClearCustomSkinPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ClearCustomSkinPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.playerName() != null ? payload.playerName() : ""),
        buf -> new ClearCustomSkinPayload(buf.readUtf())
    );

    @Override
    public Type<ClearCustomSkinPayload> type() {
        return TYPE;
    }
}
