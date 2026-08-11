package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ChunkloaderConfirmationScreen extends Screen {

    private final Screen parent;
    private final Text title;
    private final Text message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ChunkloaderConfirmationScreen(Screen parent, Text title, Text message, Runnable onConfirm, Runnable onCancel) {
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

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Confirm").formatted(Formatting.RED),
                btn -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                    if (this.client.currentScreen == this) {
                        this.client.setScreen(parent);
                    }
                })
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                    this.client.setScreen(parent);
                })
            .dimensions(buttonX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        TextRenderer renderer = this.textRenderer;

        int titleWidth = renderer.getWidth(title);
        Text titleFormatted = title.copy().formatted(Formatting.BOLD).formatted(Formatting.RED);
        context.drawText(renderer, titleFormatted,
            (this.width - titleWidth) / 2, this.height / 2 - 40, 0xFFFFFFFF, false);

        String[] lines = message.getString().split("\n");
        int lineHeight = 12;
        int startY = this.height / 2 - 10;
        for (int i = 0; i < lines.length; i++) {
            int lineWidth = renderer.getWidth(Text.literal(lines[i]));
            context.drawText(renderer, Text.literal(lines[i]),
                (this.width - lineWidth) / 2, startY + i * lineHeight, 0xFFCCCCCC, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

