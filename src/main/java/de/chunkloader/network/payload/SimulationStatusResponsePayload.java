package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SimulationStatusResponsePayload(
    boolean inSimulatedChunk,
    String fakeplayerName,
    int chunkX,
    int chunkZ,
    int simulationDistance,
    int distance
) implements CustomPayload {

    public static final CustomPayload.Id<SimulationStatusResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "simulation_status_response"));
    public static final PacketCodec<RegistryByteBuf, SimulationStatusResponsePayload> CODEC =
        PacketCodec.of(SimulationStatusResponsePayload::write, SimulationStatusResponsePayload::read);

    private static void write(SimulationStatusResponsePayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.inSimulatedChunk());
        buf.writeString(payload.fakeplayerName() != null ? payload.fakeplayerName() : "");
        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
        buf.writeInt(payload.simulationDistance());
        buf.writeInt(payload.distance());
    }

    private static SimulationStatusResponsePayload read(RegistryByteBuf buf) {
        boolean inSimulatedChunk = buf.readBoolean();
        String fakeplayerName = buf.readString();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int simulationDistance = buf.readInt();
        int distance = buf.readInt();
        return new SimulationStatusResponsePayload(inSimulatedChunk, fakeplayerName, chunkX, chunkZ, simulationDistance, distance);
    }

    @Override
    public Id<SimulationStatusResponsePayload> getId() {
        return ID;
    }
}

