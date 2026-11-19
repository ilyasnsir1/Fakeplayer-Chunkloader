package de.chunkloader.config;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class SkinPngValidator {
    public static final int MAX_BYTES = 128 * 1024;
    private static final byte[] SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10};

    private SkinPngValidator() {}

    public static byte[] readFile(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Skin file too large (max 128 KiB).");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        validate(bytes);
        return bytes;
    }

    public static boolean isValid(byte[] bytes) {
        try {
            validate(bytes);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 45) throw invalid();
        if (bytes.length > MAX_BYTES) throw new IOException("Skin file too large (max 128 KiB).");
        for (int i = 0; i < SIGNATURE.length; i++) if (bytes[i] != SIGNATURE[i]) throw invalid();

        boolean header = false, pixels = false, pixelsEnded = false, end = false;
        int expectedPixelBytes = 0;
        ByteArrayOutputStream compressedPixels = new ByteArrayOutputStream();
        int offset = 8;
        while (offset < bytes.length) {
            if (bytes.length - offset < 12) throw invalid();
            int length = readInt(bytes, offset);
            long next = (long) offset + length + 12;
            if (length < 0 || next > bytes.length) throw invalid();
            int type = readInt(bytes, offset + 4);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, length + 4);
            if (crc.getValue() != Integer.toUnsignedLong(readInt(bytes, offset + 8 + length))) throw invalid();
            if (!header) {
                if (type != 0x49484452 || length != 13) throw invalid();
                int width = readInt(bytes, offset + 8), height = readInt(bytes, offset + 12);
                if (width != 64 || (height != 64 && height != 32)) throw invalid();
                int depth = bytes[offset + 16] & 255, color = bytes[offset + 17] & 255;
                int interlace = bytes[offset + 20] & 255;
                if (bytes[offset + 18] != 0 || bytes[offset + 19] != 0 || interlace > 1) throw invalid();
                int channels = switch (color) {
                    case 0, 3 -> 1;
                    case 2 -> 3;
                    case 4 -> 2;
                    case 6 -> 4;
                    default -> throw invalid();
                };
                boolean validDepth = switch (color) {
                    case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
                    case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
                    default -> depth == 8 || depth == 16;
                };
                if (!validDepth) throw invalid();
                expectedPixelBytes = pixelBytes(width, height, channels * depth, interlace);
                header = true;
            } else if (type == 0x49484452) {
                throw invalid();
            }
            if (type == 0x49444154) {
                if (pixelsEnded) throw invalid();
                pixels = true;
                compressedPixels.write(bytes, offset + 8, length);
            } else if (pixels) {
                pixelsEnded = true;
            }
            offset = (int) next;
            if (type == 0x49454e44) {
                if (length != 0 || !pixels || offset != bytes.length) throw invalid();
                end = true;
                break;
            }
        }
        if (!end) throw invalid();
        validatePixelStream(compressedPixels.toByteArray(), expectedPixelBytes);

        var readers = ImageIO.getImageReadersByFormatName("PNG");
        if (!readers.hasNext()) throw new IOException("PNG reader unavailable.");
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            if (width != 64 || (height != 64 && height != 32)) throw invalid();
            BufferedImage image = reader.read(0);
            if (image == null) throw invalid();
            try {
                if (image.getWidth() != width || image.getHeight() != height) throw invalid();
            } finally {
                image.flush();
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid skin PNG.", e);
        } finally {
            reader.dispose();
        }
    }

    private static int pixelBytes(int width, int height, int bitsPerPixel, int interlace) {
        if (interlace == 0) return height * (1 + (width * bitsPerPixel + 7) / 8);
        int[] startX = {0, 4, 0, 2, 0, 1, 0}, startY = {0, 0, 4, 0, 2, 0, 1};
        int[] stepX = {8, 8, 4, 4, 2, 2, 1}, stepY = {8, 8, 8, 4, 4, 2, 2};
        int total = 0;
        for (int pass = 0; pass < 7; pass++) {
            int columns = (width - startX[pass] + stepX[pass] - 1) / stepX[pass];
            int rows = (height - startY[pass] + stepY[pass] - 1) / stepY[pass];
            if (columns > 0 && rows > 0) total += rows * (1 + (columns * bitsPerPixel + 7) / 8);
        }
        return total;
    }

    private static void validatePixelStream(byte[] compressed, int expected) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] buffer = new byte[4096];
            int total = 0;
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                total += count;
                if (total > expected || (count == 0 && !inflater.finished())) throw invalid();
            }
            if (total != expected || inflater.getRemaining() != 0) throw invalid();
        } catch (DataFormatException e) {
            throw new IOException("Invalid skin PNG.", e);
        } finally {
            inflater.end();
        }
    }

    public static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
            | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private static IOException invalid() {
        return new IOException("Skin must be a valid 64x64 or 64x32 PNG.");
    }
}
