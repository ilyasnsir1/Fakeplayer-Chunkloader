package de.chunkloader.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class ChunkloaderConfirmationScreen extends Screen {

    private final Screen parent;
    private final Component title;
    private final Component message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ChunkloaderConfirmationScreen(Screen parent, Component title, Component message, Runnable onConfirm, Runnable onCancel) {
        super(title);
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonSpacing = 20;
        int buttonY = this.height / 2 + 30;
        int buttonX = (this.width - buttonWidth * 2 - buttonSpacing) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("Confirm").withStyle(ChatFormatting.RED),
                btn -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                    if (this.minecraft.screen == this) {
                        this.minecraft.setScreen(parent);
                    }
                })
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build()
        );

        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                    this.minecraft.setScreen(parent);
                })
            .bounds(buttonX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);

        var font = this.font;

        int titleWidth = font.width(title);
        Component titleFormatted = title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
        graphics.drawString(font, titleFormatted,
            (this.width - titleWidth) / 2, this.height / 2 - 40, 0xFFFFFFFF, false);

        String[] lines = message.getString().split("\n");
        int lineHeight = 12;
        int startY = this.height / 2 - 10;
        for (int i = 0; i < lines.length; i++) {
            int lineWidth = font.width(Component.literal(lines[i]));
            graphics.drawString(font, Component.literal(lines[i]),
                (this.width - lineWidth) / 2, startY + i * lineHeight, 0xFFCCCCCC, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

