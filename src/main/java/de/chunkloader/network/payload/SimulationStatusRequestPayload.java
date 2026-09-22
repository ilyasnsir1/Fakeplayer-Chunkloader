package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SimulationStatusRequestPayload(boolean forceResponse) implements CustomPayload {

    public SimulationStatusRequestPayload() {
        this(false);
    }

    public static final CustomPayload.Id<SimulationStatusRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "simulation_status_request"));
    public static final PacketCodec<RegistryByteBuf, SimulationStatusRequestPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new SimulationStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Id<SimulationStatusRequestPayload> getId() {
        return ID;
    }
}

