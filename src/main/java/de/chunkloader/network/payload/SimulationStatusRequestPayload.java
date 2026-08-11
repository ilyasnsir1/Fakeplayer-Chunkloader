package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SimulationStatusRequestPayload(boolean forceResponse) implements CustomPacketPayload {

    public SimulationStatusRequestPayload() {
        this(false);
    }

    public static final CustomPacketPayload.Type<SimulationStatusRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChunkloaderMod.MOD_ID, "simulation_status_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SimulationStatusRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new SimulationStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Type<SimulationStatusRequestPayload> type() {
        return TYPE;
    }
}

