package de.chunkloader.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

public class PanelColorHelpScreen extends Screen {

    private static final int CONTENT_TEXT_RGB = 0xCCCCCC;

    private static Component content(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(CONTENT_TEXT_RGB)));
    }

    private final Screen parent;
    private final List<Component> helpLines = new ArrayList<>();
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
        super(Component.literal("Panel Color Help"));
        this.parent = parent;

        helpLines.add(Component.literal("Panel Color Help").withStyle(ChatFormatting.BOLD));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Overview:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Customize UI colors for the menu and the skin preview."));
        helpLines.add(Component.literal("Changes are previewed live. Save keeps them, Back discards them."));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Pages:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("< / >:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Switch between Map UI colors and Skin screen colors")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Select a target:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Click a UI element on the map (or skin preview) to edit its color."));
        helpLines.add(Component.literal("On the skin page, the layer menu (∨) is shown open so you can color chevron, menu, active and inactive rows."));
        helpLines.add(Component.literal("Double-click the status line to cycle sample messages (success / warning / error / info)."));
        helpLines.add(Component.literal("Hovered targets are outlined so you can see what you will change."));
        helpLines.add(Component.literal("The label above the palette shows the current target and alpha."));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Color controls:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Palette:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Click a color swatch to set RGB")));
        helpLines.add(Component.literal("Opacity slider:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Drag to change transparency (A)")));
        helpLines.add(Component.literal("Hex field:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Type #RRGGBB for an exact color")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Buttons:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Save:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Write colors to config and close the editor")));
        helpLines.add(Component.literal("Reset:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Reset only the current target to its default")));
        helpLines.add(Component.literal("Back:").withStyle(ChatFormatting.YELLOW)
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

        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(parent))
            .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        context.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(context);
        context.disableScissor();

        drawScrollbar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderText(GuiGraphics context) {
        Font renderer = this.font;
        int lineHeight = 12;
        int padding = 20;
        int maxWidth = this.width - padding * 2 - 10;
        int y = contentTop + 20 - scrollOffset;

        boolean isFirstSection = true;
        boolean hasRenderedFirstHeader = false;
        int lastSectionEndY = y;
        for (int i = 0; i < helpLines.size(); i++) {
            Component line = helpLines.get(i);
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

            int textWidth = renderer.width(line);
            int textColor = isBoldHeader ? 0xFFFFFFFF : 0xFFCCCCCC;

            if (textWidth > maxWidth) {
                var wrappedLines = renderer.split(line, maxWidth);
                for (var wrappedLine : wrappedLines) {
                    int wrappedWidth = renderer.width(wrappedLine);
                    int x = (this.width - wrappedWidth) / 2;
                    context.drawString(renderer, wrappedLine, x, y, textColor, false);
                    y += lineHeight;
                }
            } else {
                int x = (this.width - textWidth) / 2;
                context.drawString(renderer, line, x, y, textColor, false);
                y += lineHeight;
            }
            lastSectionEndY = y;
        }

        totalContentHeight = Math.max(0, y + scrollOffset - contentTop - 20);
    }

    private void drawScrollbar(GuiGraphics context) {
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
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
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void drawSeparator(GuiGraphics context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
