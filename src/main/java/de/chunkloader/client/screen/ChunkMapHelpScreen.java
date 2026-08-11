package de.chunkloader.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ChunkMapHelpScreen extends Screen {

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

    public ChunkMapHelpScreen(Screen parent) {
        super(Component.literal("Chunk Map Help"));
        this.parent = parent;

        helpLines.add(Component.literal("Chunk Map Help").withStyle(ChatFormatting.BOLD));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Overview:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("This screen shows what your player is keeping loaded."));
        helpLines.add(Component.literal("Fakeplayer:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Keeps chunks loaded and acts like a player is there.")));
        helpLines.add(Component.literal("Chunkplayer:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Keeps chunks loaded only (no player simulation).")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("UI Layout Presets:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Press the UI button (UI..UI 8) until you like the layout."));
        helpLines.add(Component.literal("Layouts can move the header buttons and swap the panels."));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Info Panel:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Quick info about your player and where it is."));
        helpLines.add(Component.literal("Your player head and name"));
        helpLines.add(Component.literal("Status:").withStyle(ChatFormatting.YELLOW)
            .append(content(" active/inactive")));
        helpLines.add(Component.literal("Dimension:").withStyle(ChatFormatting.YELLOW)
            .append(content(" O (Overworld), N (Nether), E (End)")));
        helpLines.add(Component.literal("Chunk and block coordinates"));
        helpLines.add(Component.literal("SD (Fakeplayer):").withStyle(ChatFormatting.YELLOW)
            .append(content(" how far simulation runs (0-3)")));
        helpLines.add(Component.literal("Radius (Chunkplayer):").withStyle(ChatFormatting.YELLOW)
            .append(content(" how many chunks stay loaded (0-3)")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Chunk Map Colors:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Green overlay:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Loaded chunks (Fakeplayer)")));
        helpLines.add(Component.literal("Blue overlay:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Loaded chunks (Chunkplayer)")));
        helpLines.add(Component.literal("Dark gray:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Occupied by another player")));
        helpLines.add(Component.literal("Red dot:").withStyle(ChatFormatting.YELLOW)
            .append(content(" This player position")));
        helpLines.add(Component.literal("Green dot:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Other Fakeplayer")));
        helpLines.add(Component.literal("Blue dot:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Other Chunkplayer")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Actions Panel:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Search:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Type here to quickly find actions")));
        helpLines.add(Component.literal("Enable/Disable:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Turns it on/off (loads chunks or stops)")));
        helpLines.add(Component.literal("Mob spawning:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Toggle whether mobs can spawn near this Fakeplayer (like a real player; not world-wide). Works independently from SD (SD 5 for mob spawning).")));
        helpLines.add(Component.literal("SD/Radius -1 / +1:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Change your SD/radius (0-3)")));
        helpLines.add(Component.literal("Rename:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Change the display name (letters/numbers only). Cannot rename to real player names. If a real player joins with the same name, the fakeplayer/chunkplayer will be automatically renamed.")));
        helpLines.add(Component.literal("Show name / Hide name:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Toggle the name of your player")));
        helpLines.add(Component.literal("Show other dots / Hide other dots:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Show/hide other chunkloaders on every chunk map (personal setting, only for you)")));
        helpLines.add(Component.literal("Live markers:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Other player dots and occupied areas update live on create/delete/radius/disable; terrain tiles do not")));
        helpLines.add(Component.literal("Visualization / 3D:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Turn visualization on/off")));
        helpLines.add(Component.literal("Panel color:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Pick your UI colors (panels, borders, scrollbars, text)")));
        helpLines.add(Component.literal("Skin:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Change this fakeplayer's custom skin from a PNG file. Not available for easter egg players.")));
        helpLines.add(Component.literal("Keybinds:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Change your F6/F7/F8 keys")));
        helpLines.add(Component.literal("Reset to defaults:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Puts everything back to default (asks first)")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Header Buttons:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Info:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Opens detailed info screen")));
        helpLines.add(Component.literal("Help:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Shows this help screen")));
        helpLines.add(Component.literal("List:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Opens disabled players list")));
        helpLines.add(Component.literal("UI:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Cycles the screen layout preset (UI..UI 8)")));
        helpLines.add(Component.literal("Delete:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Deletes this player")));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Tooltips:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Hover over chunks to see what they mean."));
        helpLines.add(Component.literal("Loaded by this player"));
        helpLines.add(Component.literal("Outside of this player"));
        helpLines.add(Component.literal("Inside radius/SD (enable to load)"));
        helpLines.add(Component.empty());
        helpLines.add(Component.literal("Map Navigation:").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        helpLines.add(Component.literal("Rotate map:").withStyle(ChatFormatting.YELLOW)
            .append(content(" Left-click the map grid to rotate 90° clockwise (N→W→S→E→N)")));
        helpLines.add(Component.literal("Compass:").withStyle(ChatFormatting.YELLOW)
            .append(content(" The label above the map shows the current top direction")));
        helpLines.add(Component.literal("Auto-orientation:").withStyle(ChatFormatting.YELLOW)
            .append(content(" The map opens facing the cardinal direction you are looking when clicking a player")));
        helpLines.add(Component.literal("Spawn direction:").withStyle(ChatFormatting.YELLOW)
            .append(content(" /fp add spawns the fakeplayer facing your current cardinal direction (N/S/E/W)")));
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

