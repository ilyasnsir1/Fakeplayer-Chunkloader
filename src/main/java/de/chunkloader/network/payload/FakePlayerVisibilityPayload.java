package de.chunkloader.network.payload;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FakePlayerVisibilityPayload(
    String fakePlayerName,
    boolean visible
) implements CustomPayload {
    
    public static final CustomPayload.Id<FakePlayerVisibilityPayload> ID = 
        new CustomPayload.Id<>(Identifier.of(ChunkloaderMod.MOD_ID, "fakeplayer_visibility"));
    
    public static final PacketCodec<RegistryByteBuf, FakePlayerVisibilityPayload> CODEC = 
        PacketCodec.of(FakePlayerVisibilityPayload::write, FakePlayerVisibilityPayload::read);
    
    private static void write(FakePlayerVisibilityPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.fakePlayerName());
        buf.writeBoolean(payload.visible());
    }
    
    private static FakePlayerVisibilityPayload read(RegistryByteBuf buf) {
        return new FakePlayerVisibilityPayload(buf.readString(), buf.readBoolean());
    }
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

