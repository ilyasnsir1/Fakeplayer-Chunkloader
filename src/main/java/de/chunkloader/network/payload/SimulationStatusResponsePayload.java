package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SimulationStatusResponsePayload(
    boolean inSimulatedChunk,
    String fakeplayerName,
    int chunkX,
    int chunkZ,
    int simulationDistance,
    int distance
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SimulationStatusResponsePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "simulation_status_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SimulationStatusResponsePayload> CODEC =
        StreamCodec.of(SimulationStatusResponsePayload::write, SimulationStatusResponsePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SimulationStatusResponsePayload payload) {
        buf.writeBoolean(payload.inSimulatedChunk());
        buf.writeUtf(payload.fakeplayerName() != null ? payload.fakeplayerName() : "");
        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
        buf.writeInt(payload.simulationDistance());
        buf.writeInt(payload.distance());
    }

    private static SimulationStatusResponsePayload read(RegistryFriendlyByteBuf buf) {
        boolean inSimulatedChunk = buf.readBoolean();
        String fakeplayerName = buf.readUtf();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int simulationDistance = buf.readInt();
        int distance = buf.readInt();
        return new SimulationStatusResponsePayload(inSimulatedChunk, fakeplayerName, chunkX, chunkZ, simulationDistance, distance);
    }

    @Override
    public Type<SimulationStatusResponsePayload> type() {
        return TYPE;
    }
}

