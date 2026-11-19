package de.chunkloader.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TicketStorage.class)
public interface TicketStorageAccessor {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<List<Ticket>> chunkloader$getTickets();
}
