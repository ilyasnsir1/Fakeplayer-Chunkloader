package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SimulationStatusRequestPayload(boolean forceResponse) implements CustomPacketPayload {

    public SimulationStatusRequestPayload() {
        this(false);
    }

    public static final Type<SimulationStatusRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "simulation_status_request"));
    public static final StreamCodec<FriendlyByteBuf, SimulationStatusRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.forceResponse()),
            buf -> new SimulationStatusRequestPayload(buf.readBoolean())
        );

    @Override
    public Type<SimulationStatusRequestPayload> type() {
        return TYPE;
    }
}

