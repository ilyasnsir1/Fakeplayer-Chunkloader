package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EasterEggSkinPayload(
    UUID playerUuid,
    int skinIndex
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EasterEggSkinPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "easter_egg_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EasterEggSkinPayload> CODEC =
        StreamCodec.of(EasterEggSkinPayload::write, EasterEggSkinPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, EasterEggSkinPayload payload) {
        buf.writeUUID(payload.playerUuid());
        buf.writeInt(payload.skinIndex());
    }

    private static EasterEggSkinPayload read(RegistryFriendlyByteBuf buf) {
        return new EasterEggSkinPayload(buf.readUUID(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

