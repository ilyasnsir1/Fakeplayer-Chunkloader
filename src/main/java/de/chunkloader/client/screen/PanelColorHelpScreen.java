package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class PanelColorHelpScreen extends Screen {

    private static final int CONTENT_TEXT_RGB = 0xCCCCCC;

    private static Text content(String text) {
        return Text.literal(text).styled(style -> style.withColor(TextColor.fromRgb(CONTENT_TEXT_RGB)));
    }

    private final Screen parent;
    private final List<Text> helpLines = new ArrayList<>();
    private int scrollOffset = 0;
    private int contentTop;
    private int contentBottom;
    private int totalContentHeight;

    private boolean scrollbarDragging = false;
    private int scrollbarDragOffsetY = 0;

    private static final class ScrollbarMetrics {
        private final int x;
        private final int width;
        private final int trackTop;
        private final int trackHeight;
        private final int thumbY;
        private final int thumbHeight;
        private final int maxScroll;

        private ScrollbarMetrics(int x, int width, int trackTop, int trackHeight, int thumbY, int thumbHeight, int maxScroll) {
            this.x = x;
            this.width = width;
            this.trackTop = trackTop;
            this.trackHeight = trackHeight;
            this.thumbY = thumbY;
            this.thumbHeight = thumbHeight;
            this.maxScroll = maxScroll;
        }
    }

    private ScrollbarMetrics getScrollbarMetrics() {
        int availableHeight = contentBottom - contentTop;
        int totalHeightWithPadding = totalContentHeight + 40;
        if (availableHeight <= 0 || totalHeightWithPadding <= availableHeight) {
            return null;
        }

        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = (int) ((double) availableHeight / totalHeightWithPadding * availableHeight);
        int maxScroll = totalHeightWithPadding - availableHeight;
        if (scrollbarHeight <= 0 || maxScroll <= 0) {
            return null;
        }

        int scrollbarY = contentTop + (int) ((double) scrollOffset / maxScroll * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, contentTop, availableHeight, scrollbarY, scrollbarHeight, maxScroll);
    }

    public PanelColorHelpScreen(Screen parent) {
        super(Text.literal("Panel Color Help"));
        this.parent = parent;

        helpLines.add(Text.literal("Panel Color Help").formatted(Formatting.BOLD));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Overview:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Customize UI colors for the menu and the skin preview."));
        helpLines.add(Text.literal("Changes are previewed live. Save keeps them, Back discards them."));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Pages:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("< / >:").formatted(Formatting.YELLOW)
            .append(content(" Switch between Map UI colors and Skin screen colors")));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Select a target:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Click a UI element on the map (or skin preview) to edit its color."));
        helpLines.add(Text.literal("On the skin page, the layer menu (∨) is shown open so you can color chevron, menu, active and inactive rows."));
        helpLines.add(Text.literal("Double-click the status line to cycle sample messages (success / warning / error / info)."));
        helpLines.add(Text.literal("Hovered targets are outlined so you can see what you will change."));
        helpLines.add(Text.literal("The label above the palette shows the current target and alpha."));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Color controls:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Palette:").formatted(Formatting.YELLOW)
            .append(content(" Click a color swatch to set RGB")));
        helpLines.add(Text.literal("Opacity slider:").formatted(Formatting.YELLOW)
            .append(content(" Drag to change transparency (A)")));
        helpLines.add(Text.literal("Hex field:").formatted(Formatting.YELLOW)
            .append(content(" Type #RRGGBB for an exact color")));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Buttons:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Save:").formatted(Formatting.YELLOW)
            .append(content(" Write colors to config and close the editor")));
        helpLines.add(Text.literal("Reset:").formatted(Formatting.YELLOW)
            .append(content(" Reset only the current target to its default")));
        helpLines.add(Text.literal("Back:").formatted(Formatting.YELLOW)
            .append(content(" Close without saving")));
    }
    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        contentTop = 20;
        contentBottom = this.height - 60;
        totalContentHeight = 0;

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 30;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.client.setScreen(parent))
            .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        context.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(context);
        context.disableScissor();

        drawScrollbar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderText(DrawContext context) {
        TextRenderer renderer = this.textRenderer;
        int lineHeight = 12;
        int padding = 20;
        int maxWidth = this.width - padding * 2 - 10;
        int y = contentTop + 20 - scrollOffset;

        boolean isFirstSection = true;
        boolean hasRenderedFirstHeader = false;
        int lastSectionEndY = y;
        for (int i = 0; i < helpLines.size(); i++) {
            Text line = helpLines.get(i);
            boolean isBoldHeader = !line.getString().isEmpty() && line.getStyle().isBold() && !line.getStyle().isUnderlined();

            if (line.getString().isEmpty()) {
                if (!isFirstSection && hasRenderedFirstHeader) {
                    int separatorY = lastSectionEndY + lineHeight / 3;
                    int separatorWidth = Math.min(200, this.width - 100);
                    int separatorX = (this.width - separatorWidth) / 2;
                    drawSeparator(context, separatorX, separatorY, separatorWidth);
                }
                y += lineHeight;
                continue;
            }

            if (isBoldHeader) {
                if (hasRenderedFirstHeader) {
                    isFirstSection = false;
                }
                hasRenderedFirstHeader = true;
            }

            int textWidth = renderer.getWidth(line);
            int textColor = isBoldHeader ? 0xFFFFFFFF : 0xFFCCCCCC;

            if (textWidth > maxWidth) {
                var wrappedLines = renderer.wrapLines(line, maxWidth);
                for (var wrappedLine : wrappedLines) {
                    int wrappedWidth = renderer.getWidth(wrappedLine);
                    int x = (this.width - wrappedWidth) / 2;
                    context.drawText(renderer, wrappedLine, x, y, textColor, false);
                    y += lineHeight;
                }
            } else {
                int x = (this.width - textWidth) / 2;
                context.drawText(renderer, line, x, y, textColor, false);
                y += lineHeight;
            }
            lastSectionEndY = y;
        }

        totalContentHeight = Math.max(0, y + scrollOffset - contentTop - 20);
    }

    private void drawScrollbar(DrawContext context) {
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null) {
            return;
        }
        context.fill(metrics.x, metrics.trackTop, metrics.x + metrics.width, metrics.trackTop + metrics.trackHeight, 0x66000000);
        context.fill(metrics.x, metrics.thumbY, metrics.x + metrics.width, metrics.thumbY + metrics.thumbHeight, 0xAAFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int availableHeight = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalContentHeight + 40 - availableHeight);

        scrollOffset = (int) Math.max(0, Math.min(maxScroll,
            scrollOffset - (int) (verticalAmount * 20)));
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();
            ScrollbarMetrics metrics = getScrollbarMetrics();
            if (metrics != null
                && mouseX >= metrics.x && mouseX < metrics.x + metrics.width
                && mouseY >= metrics.thumbY && mouseY < metrics.thumbY + metrics.thumbHeight) {
                scrollbarDragging = true;
                scrollbarDragOffsetY = (int) (mouseY - metrics.thumbY);
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        if (scrollbarDragging) {
            double mouseY = click.y();
            ScrollbarMetrics metrics = getScrollbarMetrics();
            if (metrics == null) {
                scrollbarDragging = false;
                return false;
            }

            int trackRange = metrics.trackHeight - metrics.thumbHeight;
            if (trackRange <= 0) {
                return true;
            }

            int newThumbY = (int) mouseY - scrollbarDragOffsetY;
            newThumbY = Math.max(metrics.trackTop, Math.min(metrics.trackTop + trackRange, newThumbY));

            int newScroll = (int) Math.round(((double) (newThumbY - metrics.trackTop) / trackRange) * metrics.maxScroll);
            scrollOffset = (int) Math.max(0, Math.min(metrics.maxScroll, newScroll));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
