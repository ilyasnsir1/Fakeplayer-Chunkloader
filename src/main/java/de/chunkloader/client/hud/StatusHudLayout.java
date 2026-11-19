package de.chunkloader.client.hud;

public final class StatusHudLayout {
    private static long nextOrder;

    private StatusHudLayout() {
    }

    static synchronized long nextActivationOrder() {
        return ++nextOrder;
    }

    public static int simulationOffset() {
        int otherHeight = ChunkplayerStatusHUD.getHeight();
        if (otherHeight == 0 || SimulationStatusHUD.getActivationOrder() < ChunkplayerStatusHUD.getActivationOrder()) {
            return 0;
        }
        return otherHeight + 5;
    }

    public static int chunkplayerOffset() {
        int otherHeight = SimulationStatusHUD.getHeight();
        if (otherHeight == 0 || ChunkplayerStatusHUD.getActivationOrder() < SimulationStatusHUD.getActivationOrder()) {
            return 0;
        }
        return otherHeight + 5;
    }

    public static String describeDistance(int distance) {
        int side = distance * 2 + 1;
        int chunks = side * side;
        return distance + " (" + chunks + (chunks == 1 ? " chunk)" : " chunks)");
    }
}
