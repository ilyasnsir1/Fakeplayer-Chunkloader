package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SimulationStatusResponsePayload(
    boolean inSimulatedChunk,
    String fakeplayerName,
    int chunkX,
    int chunkZ,
    int simulationDistance,
    int distance
) implements CustomPacketPayload {

    public static final Type<SimulationStatusResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "simulation_status_response"));
    public static final StreamCodec<FriendlyByteBuf, SimulationStatusResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.inSimulatedChunk());
                buf.writeUtf(payload.fakeplayerName() != null ? payload.fakeplayerName() : "");
                buf.writeInt(payload.chunkX());
                buf.writeInt(payload.chunkZ());
                buf.writeInt(payload.simulationDistance());
                buf.writeInt(payload.distance());
            },
            buf -> {
                boolean inSimulatedChunk = buf.readBoolean();
                String fakeplayerName = buf.readUtf();
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                int simulationDistance = buf.readInt();
                int distance = buf.readInt();
                return new SimulationStatusResponsePayload(inSimulatedChunk, fakeplayerName, chunkX, chunkZ, simulationDistance, distance);
            }
        );

    @Override
    public Type<SimulationStatusResponsePayload> type() {
        return TYPE;
    }
}

