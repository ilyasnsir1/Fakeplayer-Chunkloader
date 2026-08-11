package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class ChunkMapSessionScreens {
    private ChunkMapSessionScreens() {
    }

    public static boolean isInChunkMapSession(Screen screen) {
        return findChunkMapScreen(screen) != null;
    }

    public static ChunkMapScreen findChunkMapScreen(Screen screen) {
        Screen current = screen;
        while (current != null) {
            if (current instanceof ChunkMapScreen chunkMapScreen) {
                return chunkMapScreen;
            }
            current = getParentIfKnown(current);
        }
        return null;
    }

    public static void closeIfOpen(Minecraft client) {
        if (client == null) {
            return;
        }
        if (isInChunkMapSession(client.gui.screen())) {
            client.gui.setScreen(null);
        }
    }

    private static Screen getParentIfKnown(Screen screen) {
        if (screen instanceof RenameChunkloaderScreen rename) {
            return rename.getParentScreen();
        }
        if (screen instanceof ChunkplayerSkinScreen skin) {
            return skin.getParentScreen();
        }
        if (screen instanceof ChunkloaderConfirmationScreen confirm) {
            return confirm.getParentScreen();
        }
        if (screen instanceof ChunkMapHelpScreen help) {
            return help.getParentScreen();
        }
        if (screen instanceof PanelColorHelpScreen panelHelp) {
            return panelHelp.getParentScreen();
        }
        if (screen instanceof ChunkloaderMenuScreen menu) {
            return menu.getParentScreen();
        }
        if (screen instanceof KeybindConfigScreen keybind) {
            return keybind.getParentScreen();
        }
        if (screen instanceof ChunkloaderInfoScreen info) {
            return info.getParentScreen();
        }
        if (screen instanceof ChunkloaderCommandsScreen commands) {
            return commands.getParentScreen();
        }
        if (screen instanceof ChunkloaderContactScreen contact) {
            return contact.getParentScreen();
        }
        if (screen instanceof DisabledChunkloadersScreen disabled) {
            return disabled.getParentScreen();
        }
        if (screen instanceof EditDisabledChunkloaderCoordsScreen editCoords) {
            return editCoords.getParent();
        }
        return null;
    }
}
