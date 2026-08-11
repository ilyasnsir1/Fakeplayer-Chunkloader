package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EasterEggEmotePayload(UUID playerUuid, long startGameTime) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "easter_egg_emote");
    public static final Type<EasterEggEmotePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, EasterEggEmotePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.playerUuid());
            buf.writeLong(payload.startGameTime());
        },
        buf -> new EasterEggEmotePayload(buf.readUUID(), buf.readLong())
    );

    @Override
    public Type<EasterEggEmotePayload> type() {
        return TYPE;
    }
}
