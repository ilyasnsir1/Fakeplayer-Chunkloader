package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record EasterEggEmotePayload(
    UUID playerUuid,
    long startGameTime
) implements CustomPayload {

    public static final CustomPayload.Id<EasterEggEmotePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "easter_egg_emote"));

    public static final PacketCodec<RegistryByteBuf, EasterEggEmotePayload> CODEC =
        PacketCodec.of(EasterEggEmotePayload::write, EasterEggEmotePayload::read);

    private static void write(EasterEggEmotePayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.playerUuid());
        buf.writeLong(payload.startGameTime());
    }

    private static EasterEggEmotePayload read(RegistryByteBuf buf) {
        return new EasterEggEmotePayload(buf.readUuid(), buf.readLong());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
