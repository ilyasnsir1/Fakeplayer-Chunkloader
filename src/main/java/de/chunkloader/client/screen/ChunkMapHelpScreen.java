package de.chunkloader.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class ChunkMapHelpScreen extends Screen {

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

    public ChunkMapHelpScreen(Screen parent) {
        super(Component.literal("Chunk Map Help"));
        this.parent = parent;
        
        helpLines.add(Component.literal("Chunk Map Help").withStyle(ChatFormatting.BOLD));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Overview:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("This screen shows what your chunkloader is keeping loaded."));
        helpLines.add(Component.literal("Fakeplayer:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Keeps chunks loaded and acts like a player is there.").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Chunkplayer:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Keeps chunks loaded only (no player simulation).").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("UI Layout Presets:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Press the UI button (UI..UI 8) until you like the layout."));
        helpLines.add(Component.literal("Layouts can move the header buttons and swap the panels."));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Info Panel:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Quick info about your chunkloader and where it is."));
        helpLines.add(Component.literal("Your player head and name"));
        helpLines.add(Component.literal("Status:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" active/inactive").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Dimension:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" O (Overworld), N (Nether), E (End)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Chunk and block coordinates"));
        helpLines.add(Component.literal("SD (Fakeplayer):").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" how far simulation runs (0-3)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Radius (Chunkplayer):").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" how many chunks stay loaded (0-3)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Chunk Map Colors:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Green overlay:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Loaded chunks (Fakeplayer)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Blue overlay:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Loaded chunks (Chunkplayer)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Dark gray:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Occupied by another chunkloader").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Red dot:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" This chunkloader position").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Green dot:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Other Fakeplayer").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Blue dot:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Other Chunkplayer").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Actions Panel:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Search:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Type here to quickly find actions").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Enable/Disable:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Turns it on/off (loads chunks or stops)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Mob spawning:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Toggle whether mobs can spawn or not through your chunkloader").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("SD/Radius -1 / +1:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Change your SD/radius (0-3)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Rename:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Change the display name (letters/numbers only)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Show name / Hide name:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Toggle the name of your chunkloader").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Show other dots / Hide other dots:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Show/hide other chunkloaders on the map").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Visualization / 3D:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Turn visualization on/off").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Panel color:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Pick your UI colors").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Keybinds:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Change your F6/F7/F8 keys").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Reset to defaults:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Puts everything back to default (asks first)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Header Buttons:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Info:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Opens detailed info screen").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Help:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Shows this help screen").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("List:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Opens disabled chunkloaders list").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("UI:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Cycles the screen layout preset (UI..UI 8)").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.literal("Delete:").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" Deletes this chunkloader").withStyle(ChatFormatting.WHITE)));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Tooltips:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Hover over chunks to see what they mean."));
        helpLines.add(Component.literal("Loaded by this chunkloader"));
        helpLines.add(Component.literal("Outside of this chunkloader"));
        helpLines.add(Component.literal("Inside radius/SD (enable to load)"));
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        
        graphics.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(graphics);
        graphics.disableScissor();
        
        drawScrollbar(graphics);
        
        super.render(graphics, mouseX, mouseY, delta);
    }
    
    private void renderText(GuiGraphics graphics) {
        var font = this.font;
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
                    drawSeparator(graphics, separatorX, separatorY, separatorWidth);
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
            
            int textWidth = font.width(line);
            
            int textColor = isBoldHeader ? 0xFFFFFFFF : 0xFFCCCCCC;
            
            if (textWidth > maxWidth) {
                var wrappedLines = font.split(line, maxWidth);
                for (var wrappedLine : wrappedLines) {
                    int wrappedWidth = font.width(wrappedLine);
                    int x = (this.width - wrappedWidth) / 2;
                    graphics.drawString(font, wrappedLine, x, y, textColor, false);
                    y += lineHeight;
                }
            } else {
                int x = (this.width - textWidth) / 2;
                graphics.drawString(font, line, x, y, textColor, false);
                y += lineHeight;
            }
            
            if (!isBoldHeader) {
                lastSectionEndY = y;
            }
        }
        
        totalContentHeight = y - contentTop - 20 + scrollOffset;
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
    
    private void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

