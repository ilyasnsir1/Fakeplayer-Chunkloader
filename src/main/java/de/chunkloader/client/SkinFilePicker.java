package de.chunkloader.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SkinFilePicker {
    private static final long WINDOWS_DIALOG_TIMEOUT_SECONDS = 300L;

    private SkinFilePicker() {
    }

    public static String openPngDialog(String preferredPath) {
        String initialDirectory = resolveInitialDirectory(preferredPath);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String windowsResult = openWindowsFormsDialog(initialDirectory);
            if (windowsResult != null) {
                return windowsResult;
            }
        }
        return openTinyFdDialog(initialDirectory);
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
            } catch (Exception ignored) {
            }
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            Path documents = Path.of(userHome, "Documents");
            if (Files.isDirectory(documents)) {
                return documents.toString();
            }
            Path desktop = Path.of(userHome, "Desktop");
            if (Files.isDirectory(desktop)) {
                return desktop.toString();
            }
            return userHome;
        }
        return System.getProperty("user.dir", ".");
    }

    private static String openWindowsFormsDialog(String initialDirectory) {
        String safeDirectory = escapeForSingleQuotedPowerShell(initialDirectory);
        String script =
            "Add-Type -AssemblyName System.Windows.Forms; "
                + "$owner = New-Object System.Windows.Forms.Form; "
                + "$owner.TopMost = $true; "
                + "$owner.ShowInTaskbar = $false; "
                + "$owner.Opacity = 0; "
                + "$owner.Width = 1; "
                + "$owner.Height = 1; "
                + "$owner.StartPosition = 'Manual'; "
                + "$owner.Location = New-Object System.Drawing.Point(-32000, -32000); "
                + "$dialog = New-Object System.Windows.Forms.OpenFileDialog; "
                + "$dialog.Title = 'Select Skin PNG'; "
                + "$dialog.Filter = 'PNG Files (*.png)|*.png|All Files (*.*)|*.*'; "
                + "$dialog.FilterIndex = 1; "
                + "$dialog.Multiselect = $false; "
                + "$dialog.CheckFileExists = $true; "
                + "$dialog.RestoreDirectory = $true; "
                + "try { $dialog.InitialDirectory = '" + safeDirectory + "' } catch {} "
                + "try { "
                + "  $owner.Show(); "
                + "  $owner.Activate(); "
                + "  $result = $dialog.ShowDialog($owner); "
                + "} finally { "
                + "  $owner.Close(); "
                + "  $owner.Dispose(); "
                + "} "
                + "if ($result -eq [System.Windows.Forms.DialogResult]::OK) { "
                + "  [Console]::Out.Write($dialog.FileName) "
                + "}";

        ProcessBuilder builder = new ProcessBuilder(
            "powershell.exe",
            "-STA",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            script
        );
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) {
                        output.append(System.lineSeparator());
                    }
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(WINDOWS_DIALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                org.slf4j.LoggerFactory.getLogger("chunkloader").warn("Windows skin file dialog timed out");
                return null;
            }
            if (process.exitValue() != 0) {
                org.slf4j.LoggerFactory.getLogger("chunkloader").warn(
                    "Windows skin file dialog exited with code {}: {}",
                    process.exitValue(),
                    output
                );
                return null;
            }

            String selected = output.toString().trim();
            if (selected.isEmpty()) {
                return null;
            }
            Path path = Path.of(selected).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return path.toString();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("chunkloader").warn("Windows skin file dialog failed", e);
            return null;
        }
    }

    private static String openTinyFdDialog(String initialDirectory) {
        String defaultPath = initialDirectory;
        if (defaultPath != null && !defaultPath.endsWith("\\") && !defaultPath.endsWith("/")) {
            defaultPath = defaultPath + System.getProperty("file.separator");
        }
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

    private static String escapeForSingleQuotedPowerShell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }
}
