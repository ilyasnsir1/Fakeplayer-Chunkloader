package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
public class ChunkloaderInfoScreen extends Screen {

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

    public ChunkloaderInfoScreen(Screen parent) {
        super(Component.literal("Mod Information"));
        this.parent = parent;
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        context.enableScissor(0, contentTop, this.width, contentBottom);
        renderText(context);
        context.disableScissor();

        drawScrollbar(context);

        super.extractRenderState(context, mouseX, mouseY, delta);
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

    private void drawScrollbar(GuiGraphicsExtractor context) {
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

    private void renderText(GuiGraphicsExtractor context) {
        Font renderer = this.font;
        int lineHeight = 12;
        int sectionSpacing = 20;
        int y = contentTop + 20 - scrollOffset;

        Component title = Component.literal("Mod Information").withStyle(ChatFormatting.BOLD);
        int titleWidth = renderer.width(title);
        context.text(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += 30;

        Component fakePlayerTitle = Component.literal("Fakeplayer Mode").withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN);
        String[] fakePlayerLines = {
            "Simulates a real player to keep mobs and farms ticking.",
            "SD (Simulation Distance) can be set from 0 to 3.",
            "SD 0 = 1 chunk, SD 1 = 3x3, SD 2 = 5x5, SD 3 = 7x7 chunks.",
            "Great for redstone, mob, and plant farms that need entity ticking.",
            "SD controls chunk loading and simulation area (0-3).",
            "Mob spawning works independently with SD 5 (24-128 blocks away).",
            "Optional Mob Target: allow mobs to aggro/pathfind to this Fakeplayer (AFK farms). Default off; no inventory, armor, or combat.",
            "Ideal for farms with mobs (hostile, passive, villagers, golems, etc.)."
        };
        y = drawInfoSection(context, renderer, y, fakePlayerTitle, fakePlayerLines, lineHeight, sectionSpacing, false);

        Component chunkplayerTitle = Component.literal("Chunkplayer Mode").withStyle(ChatFormatting.BOLD, ChatFormatting.BLUE);
        String[] chunkplayerLines = {
            "Keeps chunks loaded without entity ticking or mob spawning.",
            "Ideal for portals, passive storage, and non-mob farms (e.g., minecarts).",
            "Radius can be set from 0 to 3.",
            "Radius 0 = 1 chunk, Radius 1 = 3x3, Radius 2 = 5x5, Radius 3 = 7x7 chunks.",
            "Only the central chunk receives random ticks (crop growth).",
            "Mobs in chunkplayer areas will despawn if no real player is nearby."
        };
        y = drawInfoSection(context, renderer, y, chunkplayerTitle, chunkplayerLines, lineHeight, sectionSpacing, true);

        Component simDistTitle = Component.literal("Simulation Distance (SD)").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] simDistLines = {
            "For Fakeplayer: Controls how far entities are simulated (0-3).",
            "SD can be adjusted from 0 to 3 using SD +1 / SD -1 buttons.",
            "New Fakeplayers default to SD 0.",
            "Mob spawning works independently with SD 5 (24-128 blocks away).",
            "The mod respects the server-defined simulation distance."
        };
        y = drawInfoSection(context, renderer, y, simDistTitle, simDistLines, lineHeight, sectionSpacing, true);

        Component statusTitle = Component.literal("Status Monitoring").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] statusLines = {
            "F6/F7/F8: Toggle HUDs for simulated or loaded chunk status, or open disabled list.",
            "HUDs refresh every two seconds and can run simultaneously.",
            "Cached data fills in if the server delays its reply.",
            "Keybinds can be changed in Controls settings or via the Keybinds button in the chunk map."
        };
        y = drawInfoSection(context, renderer, y, statusTitle, statusLines, lineHeight, sectionSpacing, true);

        Component permissionsTitle = Component.literal("Permissions").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] permissionsLines = {
            "Uses the permissions config (OP fallback when needed).",
            "Grant/revoke access via /fakeplayer permission grant|revoke."
        };
        y = drawInfoSection(context, renderer, y, permissionsTitle, permissionsLines, lineHeight, sectionSpacing, true);

        Component configTitle = Component.literal("Configuration").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] configLines = {
            "Per-world config stores block offsets and modes.",
            "Up to two timestamped backups in chunkloader/backups/ plus a latest alias can auto-restore.",
            "Config enforces limits and rejects duplicate names.",
            "Fakeplayers cannot be renamed to real player names.",
            "If a real player joins with the same name, the fakeplayer/chunkplayer",
            "will be automatically renamed (e.g., 'Player_Fakeplayer' or 'Player_Chunkplayer')."
        };
        y = drawInfoSection(context, renderer, y, configTitle, configLines, lineHeight, sectionSpacing, true);

        Component tablistTitle = Component.literal("Tab List Visibility").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] tablistLines = {
            "Players can be hidden from the tab list.",
            "Use /fp tablist <true/false> to show/hide all players.",
            "New players respect the current tablist visibility setting."
        };
        y = drawInfoSection(context, renderer, y, tablistTitle, tablistLines, lineHeight, sectionSpacing, true);

        Component chunkMapTitle = Component.literal("Chunk Map").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        String[] chunkMapLines = {
            "Spawn Direction: when you run /fp add, the fakeplayer",
            "spawns facing the cardinal direction (N/S/E/W) you were looking.",
            "Map Rotation: click on the map grid to rotate the view 90° clockwise.",
            "The compass (N/W/S/E) above the map updates as you rotate.",
            "Opening Direction: clicking a fake/chunkplayer opens the map",
            "already oriented toward the cardinal direction you are facing.",
            "Live markers: other player dots and occupied areas update on",
            "create/delete/radius/disable while maps stay open; terrain is not live."
        };
        y = drawInfoSection(context, renderer, y, chunkMapTitle, chunkMapLines, lineHeight, sectionSpacing, true);

        totalContentHeight = y - contentTop - 20 + scrollOffset;
    }

    private int drawInfoSection(GuiGraphicsExtractor context, Font renderer, int y, Component title, String[] lines, int lineHeight, int sectionSpacing, boolean drawSeparatorBefore) {
        if (drawSeparatorBefore) {
            int separatorY = y - sectionSpacing / 2;
            int separatorWidth = Math.min(200, this.width - 100);
            int separatorX = (this.width - separatorWidth) / 2;
            drawSeparator(context, separatorX, separatorY, separatorWidth);
        }

        int titleWidth = renderer.width(title);
        context.text(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += lineHeight + 4;
        for (String line : lines) {
            int lineWidth = renderer.width(Component.literal(line));
            context.text(renderer, Component.literal(line), (this.width - lineWidth) / 2, y, 0xFFCCCCCC, false);
            y += lineHeight;
        }
        y += sectionSpacing;
        return y;
    }

    private void drawSeparator(GuiGraphicsExtractor context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
    }

    private void drawDimBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
