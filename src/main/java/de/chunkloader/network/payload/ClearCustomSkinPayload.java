package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClearCustomSkinPayload(String playerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClearCustomSkinPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "clear_custom_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearCustomSkinPayload> CODEC =
        StreamCodec.of(ClearCustomSkinPayload::write, ClearCustomSkinPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ClearCustomSkinPayload payload) {
        buf.writeUtf(payload.playerName() != null ? payload.playerName() : "");
    }

    private static ClearCustomSkinPayload read(RegistryFriendlyByteBuf buf) {
        return new ClearCustomSkinPayload(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
