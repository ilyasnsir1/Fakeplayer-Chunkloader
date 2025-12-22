package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChunkloaderActionPayload(int chunkX, int chunkZ, Action action, int value) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "chunkloader_action");
    public static final Type<ChunkloaderActionPayload> TYPE = new Type<>(ID);
    
    public static final StreamCodec<FriendlyByteBuf, ChunkloaderActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeEnum(payload.action());
            buf.writeInt(payload.value());
        },
        buf -> new ChunkloaderActionPayload(
            buf.readInt(),
            buf.readInt(),
            buf.readEnum(Action.class),
            buf.readInt()
        )
    );
    
    @Override
    public Type<ChunkloaderActionPayload> type() {
        return TYPE;
    }
    
    public enum Action {
        TOGGLE_ENABLED,
        TOGGLE_MOB_SPAWNING,
        RADIUS_INCREMENT,
        RADIUS_DECREMENT,
        TOGGLE_NAME_VISIBLE,
        TOGGLE_VISUALIZE,
        TOGGLE_VISUALIZE3D,
        TOGGLE_HIDE_OTHER_DOTS,
        RESET_TO_DEFAULTS,
        DELETE
    }
}

