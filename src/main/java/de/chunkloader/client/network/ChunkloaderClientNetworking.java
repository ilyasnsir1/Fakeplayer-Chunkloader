package de.chunkloader.client.network;

import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.payload.ChunkplayerStatusRequestPayload;
import de.chunkloader.network.payload.CloseChunkMapRequestPayload;
import de.chunkloader.network.payload.DeleteDisabledChunkloaderPayload;
import de.chunkloader.network.payload.DisabledChunkloadersListRequestPayload;
import de.chunkloader.network.payload.RenameChunkloaderPayload;
import de.chunkloader.network.payload.RestoreDisabledChunkloaderPayload;
import de.chunkloader.network.payload.SimulationStatusRequestPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ChunkloaderClientNetworking {
    private ChunkloaderClientNetworking() {}

    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        ClientPacketDistributor.sendToServer(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value));
    }

    public static void requestSimulationStatus() {
        ClientPacketDistributor.sendToServer(new SimulationStatusRequestPayload(false));
    }

    public static void requestChunkplayerStatus() {
        ClientPacketDistributor.sendToServer(new ChunkplayerStatusRequestPayload(false));
    }

    public static void requestDisabledChunkloadersList() {
        ClientPacketDistributor.sendToServer(new DisabledChunkloadersListRequestPayload());
    }

    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ClientPacketDistributor.sendToServer(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        ClientPacketDistributor.sendToServer(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ) {
        ClientPacketDistributor.sendToServer(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ));
    }

    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        ClientPacketDistributor.sendToServer(new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName));
    }

    public static void sendCloseChunkMapToServer() {
        ClientPacketDistributor.sendToServer(new CloseChunkMapRequestPayload());
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
        ClientPacketDistributor.sendToServer(
            new de.chunkloader.network.payload.ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes));
    }

    public static void sendClearCustomSkin(String playerName) {
        ClientPacketDistributor.sendToServer(new de.chunkloader.network.payload.ClearCustomSkinPayload(playerName));
    }
}
