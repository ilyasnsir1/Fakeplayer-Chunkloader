package de.chunkloader.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkCache.class)
public interface ServerChunkManagerAccessor {
    @Accessor("distanceManager")
    DistanceManager chunkloader$getDistanceManager();

    @Accessor("chunkMap")
    net.minecraft.server.level.ChunkMap chunkloader$getChunkMap();
}
