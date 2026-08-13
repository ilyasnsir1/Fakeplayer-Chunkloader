package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChunkloaderActionPayload(int chunkX, int chunkZ, String dimension, Action action, int value) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChunkloaderActionPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "chunkloader_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkloaderActionPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeUtf(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
            buf.writeEnum(payload.action());
            buf.writeInt(payload.value());
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readUtf(256);
            Action action = buf.readEnum(Action.class);
            int value = buf.readInt();
            return new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value);
        });

    @Override
    public CustomPacketPayload.Type<ChunkloaderActionPayload> type() {
        return TYPE;
    }

    public enum Action {
        TOGGLE_ENABLED,
        TOGGLE_MOB_SPAWNING,
        RADIUS_INCREMENT,
        RADIUS_DECREMENT,
        TOGGLE_NAME_VISIBLE,
        TOGGLE_MOB_TARGET,
        TOGGLE_VISUALIZE,
        TOGGLE_VISUALIZE3D,
        TOGGLE_HIDE_OTHER_DOTS,
        RESET_TO_DEFAULTS,
        DELETE
    }
}
