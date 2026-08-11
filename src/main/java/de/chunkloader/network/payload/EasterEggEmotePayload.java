package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EasterEggEmotePayload(
    UUID playerUuid,
    long startGameTime
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EasterEggEmotePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "easter_egg_emote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EasterEggEmotePayload> CODEC =
        StreamCodec.of(EasterEggEmotePayload::write, EasterEggEmotePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, EasterEggEmotePayload payload) {
        buf.writeUUID(payload.playerUuid());
        buf.writeLong(payload.startGameTime());
    }

    private static EasterEggEmotePayload read(RegistryFriendlyByteBuf buf) {
        return new EasterEggEmotePayload(buf.readUUID(), buf.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
