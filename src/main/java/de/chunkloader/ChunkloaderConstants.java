package de.chunkloader;

public final class ChunkloaderConstants {
    private ChunkloaderConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_CENTER_OFFSET = 8;

    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 3;
    public static final int DEFAULT_RADIUS = 2;

    public static final int MIN_BLOCK_Y = -64;
    public static final int MAX_BLOCK_Y = 320;
    public static final int DEFAULT_BLOCK_Y = 64;

    public static final int MAX_CHUNKLOADERS = 20;

    public static final int VISUALIZATION_2D_PARTICLE_COUNT = 1;
    public static final double VISUALIZATION_2D_PARTICLE_OFFSET_Y = 0.1;
    public static final double VISUALIZATION_2D_PARTICLE_SPEED = 0.0;
    public static final int VISUALIZATION_2D_SPACING = 2;

    public static final int VISUALIZATION_3D_PARTICLE_COUNT = 1;
    public static final double VISUALIZATION_3D_PARTICLE_SPEED = 0.01;
    public static final int VISUALIZATION_3D_VERTICAL_SPACING = 4;
    public static final int VISUALIZATION_3D_HORIZONTAL_SPACING = 2;
}

