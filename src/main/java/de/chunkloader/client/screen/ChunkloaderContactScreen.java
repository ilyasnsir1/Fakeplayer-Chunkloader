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
public class ChunkloaderContactScreen extends Screen {

    private final Screen parent;
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

    public ChunkloaderContactScreen(Screen parent) {
        super(Text.literal("Contact"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        contentTop = 20;
        contentBottom = this.height - 60;
        
        totalContentHeight = 0;
        
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 30;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.client.setScreen(parent))
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);
        
        context.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(context, mouseX, mouseY);
        context.disableScissor();
        
        drawScrollbar(context);
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int availableHeight = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalContentHeight + 40 - availableHeight);
        
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, 
            scrollOffset - (int)(verticalAmount * 20)));
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
    
    private void drawScrollbar(DrawContext context) {
        int availableHeight = contentBottom - contentTop;
        int totalHeightWithPadding = totalContentHeight + 40;
        
        if (totalHeightWithPadding <= availableHeight) {
            return;
        }
        
        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = (int)((double)availableHeight / totalHeightWithPadding * availableHeight);
        int maxScroll = totalHeightWithPadding - availableHeight;
        if (maxScroll > 0) {
            int scrollbarY = contentTop + (int)((double)scrollOffset / maxScroll * (availableHeight - scrollbarHeight));
            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarWidth, contentBottom, 0x33000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
        }
    }
    
    private void renderText(DrawContext context, int mouseX, int mouseY) {
        TextRenderer renderer = this.textRenderer;
        int lineHeight = 12;
        int sectionSpacing = 20;
        int y = contentTop + 20 - scrollOffset;
        
        Text title = Text.literal("Contact & Support").formatted(Formatting.BOLD);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += 30;
        
        String[][] contactInfo = {
            {"Community", ""},
            {"Discord", "Join our Discord server for support, ideas, and issues"},
            {"Link", "https://discord.gg/MATwH4ekAd"},
            {"Channels", "Support, Ideas, and Issues channels available"},
            {"", ""},
            {"Feedback & Issues", ""},
            {"Report bugs", "Use Discord #issues channel or GitHub Issues"},
            {"Suggest features", "Share ideas in Discord #ideas channel"},
            {"", ""},
            {"Support", ""},
            {"Discord Support", "Get help in the Discord #support channel"},
            {"GitHub Issues", "Alternative support channel for technical issues"},
            {"", ""},
            {"Note", ""},
            {"This mod is open source; contributions are currently not being sought"}
        };
        
        boolean isFirstSection = true;
        int lastSectionEndY = y;
        for (String[] info : contactInfo) {
            String header = info.length > 0 ? info[0] : "";
            String description = info.length > 1 ? info[1] : "";

            if (header.isEmpty() && description.isEmpty()) {
                y += sectionSpacing;
                continue;
            }
            
            if (description.isEmpty()) {
                if (!isFirstSection) {
                    int separatorY = lastSectionEndY + sectionSpacing / 2;
                    int separatorWidth = Math.min(200, this.width - 100);
                    int separatorX = (this.width - separatorWidth) / 2;
                    drawSeparator(context, separatorX, separatorY, separatorWidth);
                }
                isFirstSection = false;
                
                Text sectionHeader = Text.literal(header).formatted(Formatting.BOLD, Formatting.YELLOW);
                int headerWidth = renderer.getWidth(sectionHeader);
                context.drawText(renderer, sectionHeader, (this.width - headerWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight + 4;
            } else {
                int infoWidth = renderer.getWidth(Text.literal(header));
                context.drawText(renderer, Text.literal(header).formatted(Formatting.GREEN),
                    (this.width - infoWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight;
                
                int descWidth = renderer.getWidth(Text.literal(description));
                int descX = (this.width - descWidth) / 2;
                int descY = y;
                
                boolean isLink = header.equals("Link");
                int linkColor = isLink ? 0xFF4A9EFF : 0xFFCCCCCC;
                
                context.drawText(renderer, Text.literal(description),
                    descX, descY, linkColor, false);
                
                if (isLink) {
                    int underlineY = descY + renderer.fontHeight;
                    context.fill(descX, underlineY, descX + descWidth, underlineY + 1, linkColor);
                }
                
                y += lineHeight + 4;
                lastSectionEndY = y;
            }
        }
        
        totalContentHeight = y - contentTop - 20 + scrollOffset;
    }
    
    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }
    
    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

