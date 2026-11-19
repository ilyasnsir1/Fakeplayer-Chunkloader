package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenHelpPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenHelpPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "open_help"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHelpPayload> CODEC =
        StreamCodec.unit(new OpenHelpPayload());

    @Override
    public Type<OpenHelpPayload> type() {
        return TYPE;
    }
}
