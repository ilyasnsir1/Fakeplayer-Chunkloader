package de.chunkloader.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.collection.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkTicketManager.class)
public interface ChunkTicketManagerAccessor {
    @Accessor("ticketsByPosition")
    Long2ObjectOpenHashMap<SortedArraySet<ChunkTicket<?>>> chunkloader$getTickets();
}
