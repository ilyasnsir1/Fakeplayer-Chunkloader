package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
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
                    if (this.minecraft.gui.screen() == this) {
                        this.minecraft.gui.setScreen(parent);
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
                    this.minecraft.gui.setScreen(parent);
                })
            .bounds(buttonX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        Font renderer = this.font;

        int titleWidth = renderer.width(title);
        Component titleFormatted = title.copy().withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.RED);
        context.text(renderer, titleFormatted,
            (this.width - titleWidth) / 2, this.height / 2 - 40, 0xFFFFFFFF, false);

        String[] lines = message.getString().split("\n");
        int lineHeight = 12;
        int startY = this.height / 2 - 10;
        for (int i = 0; i < lines.length; i++) {
            int lineWidth = renderer.width(Component.literal(lines[i]));
            context.text(renderer, Component.literal(lines[i]),
                (this.width - lineWidth) / 2, startY + i * lineHeight, 0xFFCCCCCC, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawDimBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

