package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ChunkloaderMenuScreen extends Screen {

    private final Screen parent;

    public ChunkloaderMenuScreen(Screen parent) {
        super(Text.literal("Mod Menu"));
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

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Infos about the mod"),
                btn -> this.client.setScreen(new ChunkloaderInfoScreen(this)))
            .dimensions(buttonX, startY, buttonWidth, buttonHeight)
            .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Commands"),
                btn -> this.client.setScreen(new ChunkloaderCommandsScreen(this)))
            .dimensions(buttonX, startY + buttonSpacing, buttonWidth, buttonHeight)
            .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Contact"),
                btn -> this.client.setScreen(new ChunkloaderContactScreen(this)))
            .dimensions(buttonX, startY + buttonSpacing * 2, buttonWidth, buttonHeight)
            .build()
        );

        int backButtonWidgetWidth = 100;
        int backButtonWidgetX = (this.width - backButtonWidgetWidth) / 2;
        int backButtonWidgetY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.client.setScreen(parent))
            .dimensions(backButtonWidgetX, backButtonWidgetY, backButtonWidgetWidth, buttonHeight)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);
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

