package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenHelpPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenHelpPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "open_help"));
    public static final PacketCodec<RegistryByteBuf, OpenHelpPayload> CODEC =
        PacketCodec.unit(new OpenHelpPayload());

    @Override
    public CustomPayload.Id<OpenHelpPayload> getId() {
        return ID;
    }
}
