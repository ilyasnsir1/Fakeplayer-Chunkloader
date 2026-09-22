package de.chunkloader.client.network;

import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.ChunkplayerStatusRequestPayload;
import de.chunkloader.network.payload.CloseChunkMapRequestPayload;
import de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload;
import de.chunkloader.network.payload.RenameChunkloaderPayload;
import de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload;
import de.chunkloader.network.payload.SimulationStatusRequestPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload;
import de.chunkloader.network.payload.ApplyCustomSkinPayload;
import de.chunkloader.network.payload.ClearCustomSkinPayload;
import net.minecraftforge.network.PacketDistributor;

public final class ChunkloaderClientNetworking {
    private ChunkloaderClientNetworking() {}

    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        ChunkloaderNetworking.CHANNEL.send(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value), PacketDistributor.SERVER.noArg());
    }

    public static void requestSimulationStatus() {
        ChunkloaderNetworking.CHANNEL.send(new SimulationStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }

    public static void requestChunkplayerStatus() {
        ChunkloaderNetworking.CHANNEL.send(new ChunkplayerStatusRequestPayload(false), PacketDistributor.SERVER.noArg());
    }

    public static void requestDisabledChunkloadersList() {
        ChunkloaderNetworking.CHANNEL.send(new DisabledChunkloadersListRequestPayload(), PacketDistributor.SERVER.noArg());
    }

    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderNetworking.CHANNEL.send(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension), PacketDistributor.SERVER.noArg());
    }

    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ChunkloaderNetworking.CHANNEL.send(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension), PacketDistributor.SERVER.noArg());
    }

    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ChunkloaderNetworking.CHANNEL.send(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ), PacketDistributor.SERVER.noArg());
    }

    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        ChunkloaderNetworking.CHANNEL.send(new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName), PacketDistributor.SERVER.noArg());
    }

    public static void sendCloseChunkMapToServer() {
        ChunkloaderNetworking.CHANNEL.send(new CloseChunkMapRequestPayload(), PacketDistributor.SERVER.noArg());
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
        ChunkloaderNetworking.CHANNEL.send(new ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes), PacketDistributor.SERVER.noArg());
    }

    public static void sendClearCustomSkin(String playerName) {
        ChunkloaderNetworking.CHANNEL.send(new ClearCustomSkinPayload(playerName), PacketDistributor.SERVER.noArg());
    }
}