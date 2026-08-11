package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record EasterEggSkinPayload(
    UUID playerUuid,
    int skinIndex
) implements CustomPayload {

    public static final CustomPayload.Id<EasterEggSkinPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "easter_egg_skin"));

    public static final PacketCodec<RegistryByteBuf, EasterEggSkinPayload> CODEC =
        PacketCodec.of(EasterEggSkinPayload::write, EasterEggSkinPayload::read);

    private static void write(EasterEggSkinPayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.playerUuid());
        buf.writeInt(payload.skinIndex());
    }

    private static EasterEggSkinPayload read(RegistryByteBuf buf) {
        return new EasterEggSkinPayload(buf.readUuid(), buf.readInt());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

