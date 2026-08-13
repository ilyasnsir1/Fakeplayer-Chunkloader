package de.chunkloader.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;


public final class SkinFilePicker {
    private SkinFilePicker() {
    }

    public static String openPngDialog(String preferredPath) {
        if (preferredPath != null
                && !preferredPath.isBlank()
                && CustomFakePlayerSkinCache.isSyncedSkinPath(preferredPath)) {
            preferredPath = "";
        }
        return openTinyFdDialog(formatTinyFdInitialPath(resolveInitialDirectory(preferredPath)));
    }

    private static String resolveInitialDirectory(String preferredPath) {
        if (preferredPath != null && !preferredPath.isBlank()) {
            try {
                Path candidate = Path.of(preferredPath).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate)) {
                    Path parent = candidate.getParent();
                    if (parent != null && Files.isDirectory(parent)) {
                        return parent.toString();
                    }
                }
                if (Files.isDirectory(candidate)) {
                    return candidate.toString();
                }
                Path parent = candidate.getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    return parent.toString();
                }
            } catch (InvalidPathException ignored) {
            }
        }
        return picturesDirectory();
    }

    
    static String picturesDirectory() {
        String fromCache = CustomFakePlayerSkinCache.getUserPicturesDirectory();
        if (fromCache != null && !fromCache.isBlank()) {
            return fromCache;
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path pictures = Path.of(home, "Pictures");
            if (Files.isDirectory(pictures)) {
                return pictures.toAbsolutePath().normalize().toString();
            }
            return home;
        }
        return System.getProperty("user.dir", ".");
    }

    private static String openTinyFdDialog(String defaultPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pngFilter = stack.mallocPointer(1);
            pngFilter.put(stack.UTF8("*.png")).flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                "Select Skin PNG",
                defaultPath,
                pngFilter,
                "PNG Files",
                false
            );
        }
    }

    
    private static String formatTinyFdInitialPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        try {
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
            String directory = candidate.toString();
            if (!directory.endsWith("\\") && !directory.endsWith("/")) {
                directory = directory + System.getProperty("file.separator");
            }
            return directory;
        } catch (InvalidPathException e) {
            return path;
        }
    }
}
