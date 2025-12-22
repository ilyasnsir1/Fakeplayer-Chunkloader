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
public class ChunkloaderCommandsScreen extends Screen {

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

    public ChunkloaderCommandsScreen(Screen parent) {
        super(Text.literal("Commands"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        contentTop = 20;
        contentBottom = this.height - 60;
        
        totalContentHeight = 500;
        
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
        renderText(context);
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
    
    private void renderText(DrawContext context) {
        TextRenderer renderer = this.textRenderer;
        int lineHeight = 12;
        int sectionSpacing = 20;
        int y = contentTop + 20 - scrollOffset;
        
        Text title = Text.literal("Commands").formatted(Formatting.BOLD);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += 30;
        
        String[][][] commands = {
            {{"Basic Commands", ""}},
            {{"/fakeplayer add", "Creates a fake player at your current position", "Example: /fakeplayer add"}},
            {{"/fakeplayer remove <name>", "Removes a fake player by name", "Example: /fakeplayer remove Fakeplayer1"}},
            {{"/fakeplayer list", "Lists all fake players", "Example: /fakeplayer list"}},
            {{"/fakeplayer info <name>", "Shows detailed information about a fake player", "Example: /fakeplayer info Fakeplayer1"}},
            {{"/fakeplayer reload", "Reloads the configuration file", "Example: /fakeplayer reload"}},
            {{"", ""}},
            {{"Toggle Commands", ""}},
            {{"/fakeplayer toggle <name>", "Toggles a fake player on/off", "Example: /fakeplayer toggle Fakeplayer1"}},
            {{"/fakeplayer enableall", "Enables all fake players", "Example: /fakeplayer enableall"}},
            {{"/fakeplayer disableall", "Disables all fake players", "Example: /fakeplayer disableall"}},
            {{"/fakeplayer removeall disabled", "Removes all disabled fake players", "Example: /fakeplayer removeall disabled"}},
            {{"", ""}},
            {{"Configuration Commands", ""}},
            {{"/fakeplayer rename <name> <newName>", "Renames a chunkloader (alphanumeric only)", "Example: /fakeplayer rename Fakeplayer1 MyChunkloader"}},
            {{"/fakeplayer setradius <name> <0-3>", "Sets chunk loading radius (0=1x1, 1=3x3, 2=5x5, 3=7x7)", "Example: /fakeplayer setradius Fakeplayer1 2"}},
            {{"/fakeplayer setmobspawning <name> <true/false>", "Controls mob spawning (true=FakePlayer, false=Chunkplayer)", "Example: /fakeplayer setmobspawning Fakeplayer1 false"}},
            {{"/fakeplayer namevisible <name> <true/false>", "Shows/hides the fake player name", "Example: /fakeplayer namevisible Fakeplayer1 false"}},
            {{"", ""}},
            {{"Visualization Commands", ""}},
            {{"/fakeplayer visualize <name>", "Toggles chunk border visualization", "Example: /fakeplayer visualize Fakeplayer1"}},
            {{"/fakeplayer visualize3d <name>", "Toggles 3D chunk visualization", "Example: /fakeplayer visualize3d Fakeplayer1"}},
            {{"/fakeplayer visualize3d <name> <minY> <maxY>", "Toggles 3D visualization with height range", "Example: /fakeplayer visualize3d Fakeplayer1 0 64"}},
            {{"", ""}},
            {{"Statistics Commands", ""}},
            {{"/fakeplayer stats", "Shows statistics and performance data (total, active, inactive, loaded chunks, memory usage)", "Example: /fakeplayer stats"}},
            {{"", ""}},
            {{"Alias", ""}},
            {{"/fp", "Short alias for /fakeplayer (all commands work)", "Example: /fp add"}},
            {{"", ""}},
            {{"Permission Commands", ""}},
            {{"/fakeplayer permission grant <player>", "Grants full chunkloader access to the player", "Example: /fakeplayer permission grant Steve"}},
            {{"/fakeplayer permission revoke <player>", "Revokes chunkloader access", "Example: /fakeplayer permission revoke Steve"}},
            {{"", ""}},
            {{"Client-Side Keybinds", ""}},
            {{"F6", "Toggle Simulation Status HUD", "Shows live status if you're within simulation distance of a fakeplayer"}},
            {{"F7", "Toggle Chunkplayer Status HUD", "Shows live status if you're in a chunk loaded by a chunkplayer"}},
            {{"F8", "Open Disabled Chunkloaders List", "Shows all disabled chunkloaders for management"}},
            {{"Note:", "Keybinds can be changed in Controls settings or via the Keybinds button in the chunk map", "HUDs update automatically every 2 seconds"}}
        };
        
        boolean isFirstSection = true;
        int lastSectionEndY = y;
        for (String[][] cmdGroup : commands) {
            String[] cmd = cmdGroup[0];
            if (cmd[0].isEmpty() && cmd[1].isEmpty()) {
                y += sectionSpacing;
                continue;
            }
            
            if (cmd[1].isEmpty()) {
                if (!isFirstSection) {
                    int separatorY = lastSectionEndY + sectionSpacing / 2;
                    int separatorWidth = Math.min(200, this.width - 100);
                    int separatorX = (this.width - separatorWidth) / 2;
                    drawSeparator(context, separatorX, separatorY, separatorWidth);
                }
                isFirstSection = false;
                
                Text header = Text.literal(cmd[0]).formatted(Formatting.BOLD, Formatting.YELLOW);
                int headerWidth = renderer.getWidth(header);
                context.drawText(renderer, header, (this.width - headerWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight + 4;
            } else {
                int cmdWidth = renderer.getWidth(Text.literal(cmd[0]));
                context.drawText(renderer, Text.literal(cmd[0]).formatted(Formatting.GREEN), 
                    (this.width - cmdWidth) / 2, y, 0xFFFFFFFF, false);
                y += lineHeight;
                
                int descWidth = renderer.getWidth(Text.literal(cmd[1]));
                context.drawText(renderer, Text.literal(cmd[1]), 
                    (this.width - descWidth) / 2, y, 0xFFCCCCCC, false);
                y += lineHeight;
                
                if (cmd.length > 2 && !cmd[2].isEmpty()) {
                    int exampleWidth = renderer.getWidth(Text.literal(cmd[2]));
                    context.drawText(renderer, Text.literal(cmd[2]).formatted(Formatting.GRAY, Formatting.ITALIC), 
                        (this.width - exampleWidth) / 2, y, 0xFF999999, false);
                    y += lineHeight;
                }
                y += 10;
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

