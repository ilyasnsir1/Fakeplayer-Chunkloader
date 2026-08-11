package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FakePlayerVisibilityPayload(
    String fakePlayerName,
    boolean visible
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FakePlayerVisibilityPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "fakeplayer_visibility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerVisibilityPayload> CODEC =
        StreamCodec.of(FakePlayerVisibilityPayload::write, FakePlayerVisibilityPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, FakePlayerVisibilityPayload payload) {
        buf.writeUtf(payload.fakePlayerName());
        buf.writeBoolean(payload.visible());
    }

    private static FakePlayerVisibilityPayload read(RegistryFriendlyByteBuf buf) {
        return new FakePlayerVisibilityPayload(buf.readUtf(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

