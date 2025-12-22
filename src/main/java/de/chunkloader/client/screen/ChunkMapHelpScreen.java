package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ChunkMapHelpScreen extends Screen {

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

    public ChunkMapHelpScreen(Screen parent) {
        super(Text.literal("Chunk Map Help"));
        this.parent = parent;
        
        helpLines.add(Text.literal("Chunk Map Help").formatted(Formatting.BOLD));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Overview:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("This screen shows what your chunkloader is keeping loaded."));
        helpLines.add(Text.literal("Fakeplayer:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Keeps chunks loaded and acts like a player is there.").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Chunkplayer:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Keeps chunks loaded only (no player simulation).").formatted(Formatting.WHITE)));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("UI Layout Presets:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Press the UI button (UI..UI 8) until you like the layout."));
        helpLines.add(Text.literal("Layouts can move the header buttons and swap the panels."));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Info Panel:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Quick info about your chunkloader and where it is."));
        helpLines.add(Text.literal("Your player head and name"));
        helpLines.add(Text.literal("Status:").formatted(Formatting.YELLOW)
            .append(Text.literal(" active/inactive").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Dimension:").formatted(Formatting.YELLOW)
            .append(Text.literal(" O (Overworld), N (Nether), E (End)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Chunk and block coordinates"));
        helpLines.add(Text.literal("SD (Fakeplayer):").formatted(Formatting.YELLOW)
            .append(Text.literal(" how far simulation runs (0-3)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Radius (Chunkplayer):").formatted(Formatting.YELLOW)
            .append(Text.literal(" how many chunks stay loaded (0-3)").formatted(Formatting.WHITE)));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Chunk Map Colors:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Green overlay:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Loaded chunks (Fakeplayer)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Blue overlay:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Loaded chunks (Chunkplayer)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Dark gray:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Occupied by another chunkloader").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Red dot:").formatted(Formatting.YELLOW)
            .append(Text.literal(" This chunkloader position").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Green dot:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Other Fakeplayer").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Blue dot:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Other Chunkplayer").formatted(Formatting.WHITE)));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Actions Panel:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Search:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Type here to quickly find actions").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Enable/Disable:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Turns it on/off (loads chunks or stops)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Mob spawning:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Toggle whether mobs can spawn or not through your chunkloader").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("SD/Radius -1 / +1:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Change your SD/radius (0-3)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Rename:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Change the display name (letters/numbers only)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Show name / Hide name:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Toggle the name of your chunkloader").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Show other dots / Hide other dots:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Show/hide other chunkloaders on the map").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Visualization / 3D:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Turn visualization on/off").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Panel color:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Pick your UI colors").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Keybinds:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Change your F6/F7/F8 keys").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Reset to defaults:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Puts everything back to default (asks first)").formatted(Formatting.WHITE)));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Header Buttons:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Info:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Opens detailed info screen").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Help:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Shows this help screen").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("List:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Opens disabled chunkloaders list").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("UI:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Cycles the screen layout preset (UI..UI 8)").formatted(Formatting.WHITE)));
        helpLines.add(Text.literal("Delete:").formatted(Formatting.YELLOW)
            .append(Text.literal(" Deletes this chunkloader").formatted(Formatting.WHITE)));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Tooltips:").formatted(Formatting.BOLD, Formatting.YELLOW));
        helpLines.add(Text.literal("Hover over chunks to see what they mean."));
        helpLines.add(Text.literal("Loaded by this chunkloader"));
        helpLines.add(Text.literal("Outside of this chunkloader"));
        helpLines.add(Text.literal("Inside radius/SD (enable to load)"));
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
            
            if (!isBoldHeader) {
                lastSectionEndY = y;
            }
        }
        
        totalContentHeight = y - contentTop - 20 + scrollOffset;
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
    
    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

