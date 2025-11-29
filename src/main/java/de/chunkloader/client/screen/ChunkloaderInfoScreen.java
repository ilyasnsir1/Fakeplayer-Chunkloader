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
public class ChunkloaderInfoScreen extends Screen {

    private final Screen parent;
    private int scrollOffset = 0;
    private int contentTop;
    private int contentBottom;
    private int totalContentHeight;

    public ChunkloaderInfoScreen(Screen parent) {
        super(Text.literal("Chunkloader Information"));
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
        
        Text title = Text.literal("Chunkloader Information").formatted(Formatting.BOLD);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += 30;

        Text fakePlayerTitle = Text.literal("Fakeplayer Mode").formatted(Formatting.BOLD, Formatting.GREEN);
        String[] fakePlayerLines = {
            "Simulates a real player to keep mobs and farms ticking.",
            "SD (Simulation Distance) can be set from 0 to 3.",
            "SD 0 = 1 chunk, SD 1 = 3x3, SD 2 = 5x5, SD 3 = 7x7 chunks.",
            "Great for redstone, mob, and plant farms that need entity ticking.",
            "SD controls both chunk loading and simulation area."
        };
        y = drawInfoSection(context, renderer, y, fakePlayerTitle, fakePlayerLines, lineHeight, sectionSpacing, false);

        Text chunkplayerTitle = Text.literal("Chunkplayer Mode").formatted(Formatting.BOLD, Formatting.BLUE);
        String[] chunkplayerLines = {
            "Keeps chunks loaded without entity ticking or mob spawning.",
            "Ideal for portals, passive storage, and plant farms.",
            "Radius can be set from 0 to 3.",
            "Radius 0 = 1 chunk, Radius 1 = 3x3, Radius 2 = 5x5, Radius 3 = 7x7 chunks.",
            "Only the central chunk receives random ticks (crop growth)."
        };
        y = drawInfoSection(context, renderer, y, chunkplayerTitle, chunkplayerLines, lineHeight, sectionSpacing, true);

        Text simDistTitle = Text.literal("Simulation Distance (SD)").formatted(Formatting.BOLD, Formatting.YELLOW);
        String[] simDistLines = {
            "For Fakeplayer: Controls how far mobs and entities are simulated.",
            "SD can be adjusted from 0 to 3 using SD +1 / SD -1 buttons.",
            "New Fakeplayers default to SD 0.",
            "The mod respects the server-defined simulation distance."
        };
        y = drawInfoSection(context, renderer, y, simDistTitle, simDistLines, lineHeight, sectionSpacing, true);

        Text statusTitle = Text.literal("Status Monitoring").formatted(Formatting.BOLD, Formatting.YELLOW);
        String[] statusLines = {
            "F6/F7/F8: Toggle HUDs for simulated or loaded chunk status, or open disabled list.",
            "HUDs refresh every two seconds and can run simultaneously.",
            "Cached data fills in if the server delays its reply.",
            "Keybinds can be changed in Controls settings under Miscellaneous."
        };
        y = drawInfoSection(context, renderer, y, statusTitle, statusLines, lineHeight, sectionSpacing, true);

        Text chunkMapTitle = Text.literal("Chunk Map").formatted(Formatting.BOLD, Formatting.YELLOW);
        String[] chunkMapLines = {
            "Visual map showing loaded chunks and chunkloader positions.",
            "Green overlay: Loaded chunks (Fakeplayer), Blue overlay: Loaded chunks (Chunkplayer).",
            "Dark gray: Occupied by another chunkloader.",
            "Red dot: This chunkloader position.",
            "Green dot: Other Fakeplayer, Blue dot: Other Chunkplayer.",
            "Hover over chunks to see tooltips with status information.",
            "SD/Radius buttons adjust the loaded area in real-time."
        };
        y = drawInfoSection(context, renderer, y, chunkMapTitle, chunkMapLines, lineHeight, sectionSpacing, true);

        Text permissionsTitle = Text.literal("Permissions").formatted(Formatting.BOLD, Formatting.YELLOW);
        String[] permissionsLines = {
            "Uses chunkloader_permissions.json with OP fallback when needed.",
            "Grant/revoke access via /fakeplayer permission grant|revoke."
        };
        y = drawInfoSection(context, renderer, y, permissionsTitle, permissionsLines, lineHeight, sectionSpacing, true);

        Text configTitle = Text.literal("Configuration").formatted(Formatting.BOLD, Formatting.YELLOW);
        String[] configLines = {
            "Per-world chunkloader_config.json stores block offsets and modes.",
            "Up to five timestamped backups plus a latest alias can auto-restore.",
            "ChunkloaderConfig enforces limits and rejects duplicate names."
        };
        y = drawInfoSection(context, renderer, y, configTitle, configLines, lineHeight, sectionSpacing, true);

        totalContentHeight = y - contentTop - 20 + scrollOffset;
    }

    private int drawInfoSection(DrawContext context, TextRenderer renderer, int y, Text title, String[] lines, int lineHeight, int sectionSpacing, boolean drawSeparatorBefore) {
        if (drawSeparatorBefore) {
            int separatorY = y - sectionSpacing / 2;
            int separatorWidth = Math.min(200, this.width - 100);
            int separatorX = (this.width - separatorWidth) / 2;
            drawSeparator(context, separatorX, separatorY, separatorWidth);
        }
        
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += lineHeight + 4;
        for (String line : lines) {
            int lineWidth = renderer.getWidth(Text.literal(line));
            context.drawText(renderer, Text.literal(line), (this.width - lineWidth) / 2, y, 0xFFCCCCCC, false);
            y += lineHeight;
        }
        y += sectionSpacing;
        return y;
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
