package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
final class ScreenNavigation {
    static final int MIN_DESCRIPTION_HEIGHT = 220;
    private static final double DESCRIPTION_ASPECT_RATIO = 1.9;

    record DescriptionLayout(int top, int bottom) {
        int height() {
            return bottom - top;
        }
    }

    static DescriptionLayout descriptionLayout(Screen screen) {
        int preferredHeight = (int) Math.round(screen.width / DESCRIPTION_ASPECT_RATIO);
        int height = Math.min(screen.height, Math.max(MIN_DESCRIPTION_HEIGHT, preferredHeight));
        int top = (screen.height - height) / 2;
        return new DescriptionLayout(top, top + height);
    }

    private ScreenNavigation() {
    }

    static void addBackAndCloseButtons(Screen screen, Consumer<Button> addButton) {
        addBackAndCloseButtons(screen, addButton, screen.height);
    }

    static void addBackAndCloseButtons(Screen screen, Consumer<Button> addButton, int layoutBottom) {
        int buttonWidth = 100;
        int gap = 10;
        int left = (screen.width - buttonWidth * 2 - gap) / 2;
        int top = layoutBottom - 30;
        addButton.accept(Button.builder(Component.literal("Back"), button -> screen.onClose())
                .bounds(left, top, buttonWidth, 20).build());
        addButton.accept(Button.builder(Component.literal("Close"),
                button -> ChunkMapSessionScreens.closeToGame(Minecraft.getInstance()))
                .bounds(left + buttonWidth + gap, top, buttonWidth, 20).build());
    }
}
