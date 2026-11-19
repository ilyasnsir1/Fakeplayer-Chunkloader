package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenHelpPayload() implements CustomPacketPayload {
    public static final Type<OpenHelpPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "open_help"));
    public static final StreamCodec<FriendlyByteBuf, OpenHelpPayload> STREAM_CODEC =
        StreamCodec.of((payload, buf) -> { }, buf -> new OpenHelpPayload());
    @Override public Type<OpenHelpPayload> type() { return TYPE; }
}
