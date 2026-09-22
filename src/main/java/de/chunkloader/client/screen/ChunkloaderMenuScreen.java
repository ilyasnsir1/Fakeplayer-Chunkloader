package de.chunkloader.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.eclipse.jdt.annotation.NonNull;

public class ChunkloaderMenuScreen extends Screen {

    private final Screen parent;

    public ChunkloaderMenuScreen(Screen parent) {
        super(Component.literal("Mod Menu"));
        this.parent = parent;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonSpacing = 30;
        int startY = this.height / 2 - 40;
        int buttonX = (this.width - buttonWidth) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("Infos about the mod"),
                btn -> {
                    var mc = this.minecraft;
                    if (mc != null) {
                        mc.setScreen(new ChunkloaderInfoScreen(this));
                    }
                })
            .bounds(buttonX, startY, buttonWidth, buttonHeight)
            .build()
        );

        this.addRenderableWidget(Button.builder(
                Component.literal("Commands"),
                btn -> {
                    var mc = this.minecraft;
                    if (mc != null) {
                        mc.setScreen(new ChunkloaderCommandsScreen(this));
                    }
                })
            .bounds(buttonX, startY + buttonSpacing, buttonWidth, buttonHeight)
            .build()
        );

        this.addRenderableWidget(Button.builder(
                Component.literal("Contact"),
                btn -> {
                    var mc = this.minecraft;
                    if (mc != null) {
                        mc.setScreen(new ChunkloaderContactScreen(this));
                    }
                })
            .bounds(buttonX, startY + buttonSpacing * 2, buttonWidth, buttonHeight)
            .build()
        );

        int backButtonWidth = 100;
        int backButtonX = (this.width - backButtonWidth) / 2;
        int backButtonY = this.height - 30;
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> {
                    var mc = this.minecraft;
                    if (mc != null) {
                        mc.setScreen(parent);
                    }
                })
            .bounds(backButtonX, backButtonY, backButtonWidth, buttonHeight)
            .build()
        );
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawDimBackground(@NonNull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

