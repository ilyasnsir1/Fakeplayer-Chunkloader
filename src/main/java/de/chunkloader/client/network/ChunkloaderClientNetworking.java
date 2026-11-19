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
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ChunkloaderClientNetworking {
    private ChunkloaderClientNetworking() {}

    private static void sendToServer(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(payload);
        }
    }

    public static void sendAction(ChunkloaderActionPayload.Action action, int chunkX, int chunkZ, String dimension, int value) {
        sendToServer(new ChunkloaderActionPayload(chunkX, chunkZ, dimension, action, value));
    }

    public static void requestSimulationStatus() {
        sendToServer(new SimulationStatusRequestPayload(false));
    }

    public static void requestChunkplayerStatus() {
        sendToServer(new ChunkplayerStatusRequestPayload(false));
    }

    public static void requestActiveChunkloadersList() {
        sendToServer(new de.chunkloader.network.payload.ActiveChunkloadersListRequestPayload());
    }
    public static void requestOpenChunkMap(int chunkX, int chunkZ, String dimension) {
        sendToServer(new de.chunkloader.network.payload.OpenChunkMapRequestPayload(chunkX, chunkZ, dimension));
    }

    public static void requestDisabledChunkloadersList() {
        sendToServer(new DisabledChunkloadersListRequestPayload());
    }

    public static void sendDeleteDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        sendToServer(new DeleteDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    public static void sendRestoreDisabledChunkloader(int chunkX, int chunkZ, String dimension) {
        sendToServer(new RestoreDisabledChunkloaderPayload(chunkX, chunkZ, dimension));
    }

    public static void sendUpdateDisabledChunkloaderCoords(int oldChunkX, int oldChunkZ, String oldDimension, int newChunkX, int newChunkZ, int newBlockX, int newBlockY, int newBlockZ, String newDimension) {
        sendToServer(new UpdateDisabledChunkloaderCoordsPayload(oldChunkX, oldChunkZ, oldDimension, newChunkX, newChunkZ, newBlockX, newBlockY, newBlockZ, newDimension));
    }

    public static void sendRenameChunkloader(int chunkX, int chunkZ, String dimension, String newName) {
        sendToServer(new RenameChunkloaderPayload(chunkX, chunkZ, dimension, newName));
    }

    public static void sendCloseChunkMapToServer() {
        sendToServer(new CloseChunkMapRequestPayload());
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes) {
        sendApplyCustomSkin(playerName, layerMask, model, pngBytes, 0);
    }

    public static void sendApplyCustomSkin(String playerName, int layerMask, String model, byte[] pngBytes, int requestId) {
        sendToServer(
            new de.chunkloader.network.payload.ApplyCustomSkinPayload(playerName, layerMask, model, pngBytes, requestId));
    }

    public static void sendClearCustomSkin(String playerName) {
        sendClearCustomSkin(playerName, 0);
    }

    public static void sendClearCustomSkin(String playerName, int requestId) {
        sendToServer(new de.chunkloader.network.payload.ClearCustomSkinPayload(playerName, requestId));
    }
}
