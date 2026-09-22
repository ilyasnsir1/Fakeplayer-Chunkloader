package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Objects;

public record FakePlayerVisibilityPayload(@NonNull String fakePlayerName, boolean visible) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "fakeplayer_visibility");
    public static final Type<FakePlayerVisibilityPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, FakePlayerVisibilityPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.fakePlayerName());
            buf.writeBoolean(payload.visible());
        },
        buf -> new FakePlayerVisibilityPayload(Objects.requireNonNull(buf.readUtf(), "fakePlayerName"), buf.readBoolean())
    );

    @Override
    public Type<FakePlayerVisibilityPayload> type() {
        return TYPE;
    }
}

