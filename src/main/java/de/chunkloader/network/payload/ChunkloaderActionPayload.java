package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChunkloaderActionPayload(int chunkX, int chunkZ, String dimension, Action action, int value) implements CustomPayload {

    public static final CustomPayload.Id<ChunkloaderActionPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "chunkloader_action"));
    public static final PacketCodec<RegistryByteBuf, ChunkloaderActionPayload> CODEC =
        PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeString(payload.dimension() != null ? payload.dimension() : "minecraft:overworld", 256);
            buf.writeEnumConstant(payload.action());
            buf.writeInt(payload.value());
        }, buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            String dimension = buf.readString(256);
            Action action = buf.readEnumConstant(Action.class);
            int value = buf.readInt();
            return new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value);
        });

    @Override
    public CustomPayload.Id<ChunkloaderActionPayload> getId() {
        return ID;
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
