package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EasterEggSkinPayload(UUID playerUuid, int skinIndex) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "easter_egg_skin");
    public static final Type<EasterEggSkinPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, EasterEggSkinPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.playerUuid());
            buf.writeInt(payload.skinIndex());
        },
        buf -> new EasterEggSkinPayload(buf.readUUID(), buf.readInt())
    );

    @Override
    public Type<EasterEggSkinPayload> type() {
        return TYPE;
    }
}
