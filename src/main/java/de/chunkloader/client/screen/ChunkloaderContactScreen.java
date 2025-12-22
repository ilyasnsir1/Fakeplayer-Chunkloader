package de.chunkloader.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
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
        super(Component.literal("Contact"));
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

        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(parent))
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);
        
        graphics.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(graphics, mouseX, mouseY);
        graphics.disableScissor();
        
        drawScrollbar(graphics);
        super.render(graphics, mouseX, mouseY, delta);
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            ScrollbarMetrics metrics = getScrollbarMetrics();
            if (metrics != null
                && mouseX >= metrics.x && mouseX < metrics.x + metrics.width
                && mouseY >= metrics.thumbY && mouseY < metrics.thumbY + metrics.thumbHeight) {
                scrollbarDragging = true;
                scrollbarDragOffsetY = (int) (mouseY - metrics.thumbY);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (super.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }

        if (scrollbarDragging) {
            double mouseY = event.y();
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

        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }

        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }

        return false;
    }
    
    private void drawScrollbar(GuiGraphics graphics) {
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
            graphics.fill(scrollbarX, contentTop, scrollbarX + scrollbarWidth, contentBottom, 0x33000000);
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
        }
    }
    
    private void renderText(GuiGraphics graphics, int mouseX, int mouseY) {
        var font = this.font;
        int lineHeight = 12;
        int sectionSpacing = 20;
        int y = contentTop + 20 - scrollOffset;
        
        Component title = Component.literal("Contact & Support").withStyle(ChatFormatting.BOLD);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
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
                    drawSeparator(graphics, separatorX, separatorY, separatorWidth);
                }
                isFirstSection = false;
                
                Component sectionHeader = Component.literal(header).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
                int headerWidth = font.width(sectionHeader);
                graphics.drawString(font, sectionHeader, (this.width - headerWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight + 4;
            } else {
                int infoWidth = font.width(Component.literal(header));
                graphics.drawString(font, Component.literal(header).withStyle(ChatFormatting.GREEN),
                    (this.width - infoWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight;
                
                int descWidth = font.width(Component.literal(description));
                int descX = (this.width - descWidth) / 2;
                int descY = y;
                
                boolean isLink = header.equals("Link");
                int linkColor = isLink ? 0xFF4A9EFF : 0xFFCCCCCC;
                
                graphics.drawString(font, Component.literal(description),
                    descX, descY, linkColor, false);
                
                if (isLink) {
                    int underlineY = descY + font.lineHeight;
                    graphics.fill(descX, underlineY, descX + descWidth, underlineY + 1, linkColor);
                }
                
                y += lineHeight + 4;
                lastSectionEndY = y;
            }
        }
        
        totalContentHeight = y - contentTop - 20 + scrollOffset;
    }
    
    private void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }
    
    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
    
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

