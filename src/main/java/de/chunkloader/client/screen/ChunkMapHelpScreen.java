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

    public ChunkMapHelpScreen(Screen parent) {
        super(Text.literal("Chunk Map Help"));
        this.parent = parent;
        
        helpLines.add(Text.literal("Chunk Map Help").formatted(Formatting.BOLD));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Overview:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("This screen shows the area loaded by your chunkloader."));
        helpLines.add(Text.literal("Fakeplayer: Loads chunks AND simulates player presence"));
        helpLines.add(Text.literal("Chunkplayer: Only loads chunks (no mob spawning)"));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Left Panel:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("Your player head and name"));
        helpLines.add(Text.literal("Status: active/inactive"));
        helpLines.add(Text.literal("Dimension: O (Overworld), N (Nether), E (End)"));
        helpLines.add(Text.literal("Chunk and block coordinates"));
        helpLines.add(Text.literal("SD (Simulation Distance) for Fakeplayer: 0-3"));
        helpLines.add(Text.literal("Radius for Chunkplayer: 0-3"));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Chunk Map Colors:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("Green overlay: Loaded chunks (Fakeplayer)"));
        helpLines.add(Text.literal("Blue overlay: Loaded chunks (Chunkplayer)"));
        helpLines.add(Text.literal("Dark gray: Occupied by another chunkloader"));
        helpLines.add(Text.literal("Red dot: This chunkloader position"));
        helpLines.add(Text.literal("Green dot: Other Fakeplayer"));
        helpLines.add(Text.literal("Blue dot: Other Chunkplayer"));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Right Panel:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("Chunkloader name and type"));
        helpLines.add(Text.literal("Dimension and coordinates"));
        helpLines.add(Text.literal("SD/Radius controls (SD +1 / SD -1)"));
        helpLines.add(Text.literal("Enable/Disable toggle"));
        helpLines.add(Text.literal("Toggle Mob Spawning (Fakeplayer/Chunkplayer)"));
        helpLines.add(Text.literal("Visualize/3D Visualize buttons"));
        helpLines.add(Text.literal("Legend (Chunkplayer only)"));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Top Buttons:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("Info: Opens detailed info screen"));
        helpLines.add(Text.literal("List: Opens disabled chunkloaders list"));
        helpLines.add(Text.literal("Help: Shows this help screen"));
        helpLines.add(Text.literal("Delete: Deletes this chunkloader"));
        helpLines.add(Text.empty());
        helpLines.add(Text.literal("Tooltips:").formatted(Formatting.BOLD));
        helpLines.add(Text.literal("Hover over chunks to see:"));
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
    
    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

