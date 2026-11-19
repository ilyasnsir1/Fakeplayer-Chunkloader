package de.chunkloader.client.screen;

import org.lwjgl.glfw.GLFW;
import de.chunkloader.client.ChunkloaderClient;
import net.minecraft.client.option.KeyBinding;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ChunkMapHelpScreen extends Screen {

    private static final int CONTENT_TEXT_RGB = 0xCCCCCC;

    private static Text content(String text) {
        return Text.literal(text).styled(style -> style.withColor(TextColor.fromRgb(CONTENT_TEXT_RGB)));
    }

    private enum HelpTopic {
        BASICS("Basics"), MODES("Modes"), MAP("Map"), OPTIONS("Options");

        private final String label;

        HelpTopic(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private HelpTopic selectedTopic = HelpTopic.BASICS;
    private final List<Text> helpLines = new ArrayList<>();
    private final SectionedText textPanel = new SectionedText();
    private int scrollOffset = 0;
    private int layoutTop;
    private int contentTop;
    private int contentBottom;
    private int contentLeft;
    private int contentRight;
    private int navigationLeft;
    private int navigationDividerX;
    private boolean sideNavigation;
    private int maxScroll;

    private boolean scrollbarDragging = false;
    private int scrollbarDragOffsetY = 0;

    private final class TopicLink extends ButtonWidget {
        private final HelpTopic topic;

        private TopicLink(HelpTopic topic, int x, int y, int width) {
            super(x, y, width, 20, Text.literal(topic.label),
                    ignored -> ChunkMapHelpScreen.this.selectTopic(topic), DEFAULT_NARRATION_SUPPLIER);
            this.topic = topic;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TextRenderer renderer = ChunkMapHelpScreen.this.textRenderer;
            boolean selected = topic == selectedTopic;
            int color = selected ? 0xFFFFFF55 : (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            int labelWidth = renderer.getWidth(getMessage());
            int labelX = sideNavigation ? getX() + 12 : getX() + (getWidth() - labelWidth + 12) / 2;
            int labelY = getY() + (getHeight() - 8) / 2;
            var label = getMessage().copy();
            if (isFocused()) {
                label.formatted(Formatting.UNDERLINE);
            }
            if (selected) {
                context.drawText(renderer, ">", labelX - 12, labelY, color, false);
            }
            context.drawText(renderer, label, labelX, labelY, color, false);
        }
    }

    private static final class ScrollbarMetrics {
        private final int x;
        private final int width;
        private final int trackTop;
        private final int trackHeight;
        private final int thumbY;
        private final int thumbHeight;

        private ScrollbarMetrics(int x, int width, int trackTop, int trackHeight, int thumbY, int thumbHeight) {
            this.x = x;
            this.width = width;
            this.trackTop = trackTop;
            this.trackHeight = trackHeight;
            this.thumbY = thumbY;
            this.thumbHeight = thumbHeight;
        }
    }

    private ScrollbarMetrics getScrollbarMetrics() {
        if (maxScroll == 0) {
            return null;
        }

        int availableHeight = contentBottom - contentTop;
        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int contentHeight = availableHeight + maxScroll;
        int scrollbarHeight = Math.max(1, (int) ((double) availableHeight / contentHeight * availableHeight));
        int scrollbarY = contentTop + (int) ((double) Math.min(scrollOffset, maxScroll) / maxScroll * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, contentTop, availableHeight, scrollbarY, scrollbarHeight);
    }

    public ChunkMapHelpScreen(Screen parent) {
        super(Text.literal("Chunk Map Help"));
        this.parent = parent;
        rebuildHelpLines();
    }

    private void rebuildHelpLines() {
        helpLines.clear();
        switch (selectedTopic) {
            case BASICS -> {
                if (parent == null) {
                    addSection("Getting started",
                            "Use /fp add to create a Fakeplayer where you're standing. Add X, Y and Z to place it at a specific spot. You can add a dimension after the coordinates.",
                            "Right-click the Fakeplayer to open its map and settings.");
                }
                addSection("Open a player's map from anywhere",
                        "Open \"List\" on the map or press " + keyName(ChunkloaderClient.activeChunkloadersKey, "F9")
                                + " in the world. Pick \"Open map\" to manage an active player without walking back.",
                        "Looking for one you turned off? Pick \"Disabled\" at the bottom of the list or press "
                                + keyName(ChunkloaderClient.disabledChunkloadersKey, "F8") + " in the world.");
                addSection("Move a player or turn it off",
                        "\"Disable Fakeplayer\" and \"Disable Chunkplayer\" stop loading chunks but keep the player's position and settings. Pick \"Restore\" in the disabled list to turn it back on.",
                        "Pick \"Move\" in either list. Enter the new X, Y and Z block coordinates, choose the dimension with the arrows and click \"Save\". Open \"Chat\" to look up coordinates you wrote earlier.",
                        "Already have the coordinates? Use /fp move followed by the player's name, X, Y and Z. Add a dimension if you're moving it to another one.");
                addSection("Who can change or remove a player?",
                        "With mod access, you can right-click another person's Fakeplayer or Chunkplayer to open its map. If they share it with you, you can change its settings, move it, or turn it on and off.",
                        "A normal hit disables it if you can manage it. Only the owner or an admin can remove it for good: use \"Delete\", sneak and hit it, or sneak and right-click it.");
            }
            case MODES -> {
                addSection("Running a mob farm",
                        "Choose \"Enable mob spawning\" for Fakeplayer mode. Start with SD 2 or 3 and keep both the spawning spots and the mobs' path inside the green area.",
                        "If your farm needs mobs to go after the Fakeplayer, choose \"Enable mob target\". Use \"Disable mob target\" to turn that off.");
                addSection("Keeping crops and machines running",
                        "Choose \"Disable mob spawning\" for Chunkplayer mode. It keeps the blue area active without causing natural spawns on its own.",
                        "\"Pause mobs\" stops villagers and other mobs from ticking where only this Chunkplayer keeps them active. \"Resume mobs\" lets them tick again.");
                addSection("Choosing a size",
                        "Use the SD (Simulation Distance) buttons for a Fakeplayer or the Radius buttons for a Chunkplayer. The player's own chunk is part of the square:",
                        "0 = 1 chunk   |   1 = 3 x 3   |   2 = 5 x 5   |   3 = 7 x 7");
            }
            case MAP -> {
                addSection("What the colors mean",
                        "Green: this Fakeplayer's selected area. Blue: this Chunkplayer's selected area.",
                        "Dark gray: another active player's area. Red squares: chunks reserved for a disabled player you own or administer.");
                addSection("Check a spot on the map",
                        "The red dot is the player you're viewing. Hover over a square for its chunk coordinates or a dot for the player's name. The info panel shows block coordinates and dimension.",
                        "Left-click the grid to rotate it and use the compass to stay oriented. \"Hide other dots\" and \"Show other dots\" change which player markers you see.");
                addSection("Why can't I make the area bigger?",
                        "Hover over \"SD +1\" or \"Radius +1\" when it's grayed out to see why. A disabled player still reserves its chunks, so it can block room for another player.");
            }
            case OPTIONS -> {
                addSection("Change how the player looks",
                        "\"Rename\" takes an unused name with 1 to 16 letters or digits. \"Hide name\" and \"Show name\" only change the label above the player.",
                        "Under \"Skin\", use \"Choose PNG\" to preview a skin, then \"Apply\" to use it. \"Remove\" goes back to the default skin.");
                addSection("Set up your own view",
                        "Use \"UI\" to change the map's layout and \"Panel color\" to change the colors of the Chunk Map and Select Skin screens.",
                        "Building a farm? \"Enable visualization\" or \"Enable 3D visualization\" shows chunk outlines in the world. Other players won't see them.",
                        "Walk around with the Fakeplayer HUD (" + keyName(ChunkloaderClient.simulationStatusHUDToggleKey, "F6")
                                + ") or the Chunkplayer HUD (" + keyName(ChunkloaderClient.chunkplayerStatusHUDToggleKey, "F7")
                                + ") to check whether you're inside a selected area. \"Keybinds\" lets you change these shortcuts and the ones for the player lists.");
                addSection("Start over with the defaults",
                        "\"Reset to defaults\" is for the owner or an admin. It resets the player's settings, including its name and skin, and switches it to Fakeplayer mode at SD 0.",
                        "Your map options return to their defaults. So do the colors in Chunk Map and Select Skin.");
            }
        }
    }

    private void addSection(String heading, String... lines) {
        if (!helpLines.isEmpty()) {
            helpLines.add(Text.empty());
        }
        helpLines.add(Text.literal(heading).formatted(Formatting.BOLD, Formatting.YELLOW));
        for (String line : lines) {
            helpLines.add(content(line));
        }
    }

    private static String keyName(KeyBinding key, String fallback) {
        return key == null ? fallback : key.getBoundKeyLocalizedText().getString();
    }

    private void selectTopic(HelpTopic topic) {
        if (selectedTopic == topic) {
            return;
        }
        selectedTopic = topic;
        scrollOffset = 0;
        scrollbarDragging = false;
        rebuildHelpLines();
        layoutHelpLines();
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        var layout = ScreenNavigation.descriptionLayout(this);
        layoutTop = layout.top();
        sideNavigation = this.width >= 460 && layout.height() >= ScreenNavigation.MIN_DESCRIPTION_HEIGHT;
        contentTop = layoutTop + (sideNavigation ? SectionedText.TOP_WITH_SIDEBAR : SectionedText.TOP_BELOW_TABS);
        contentBottom = layout.bottom() - SectionedText.BOTTOM_INSET;
        contentLeft = 20;
        contentRight = this.width - 20;
        scrollbarDragging = false;

        HelpTopic[] topics = HelpTopic.values();
        if (sideNavigation) {
            navigationLeft = 20;
            int navigationWidth = 80;
            navigationDividerX = navigationLeft + navigationWidth + 8;
            contentLeft = navigationDividerX + 16;
            for (int i = 0; i < topics.length; i++) {
                this.addDrawableChild(new TopicLink(topics[i], navigationLeft,
                        contentTop + i * 24, navigationWidth));
            }
        } else {
            int gap = 4;
            int linkWidth = Math.min(96, (this.width - 40 - gap * (topics.length - 1)) / topics.length);
            int rowWidth = linkWidth * topics.length + gap * (topics.length - 1);
            int startX = (this.width - rowWidth) / 2;
            for (int i = 0; i < topics.length; i++) {
                this.addDrawableChild(new TopicLink(topics[i], startX + i * (linkWidth + gap), layoutTop + 40, linkWidth));
            }
        }
        layoutHelpLines();

        if (parent == null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                    .dimensions((width - 100) / 2, layout.bottom() - 30, 100, 20).build());
        } else {
            ScreenNavigation.addBackAndCloseButtons(this, this::addDrawableChild, layout.bottom());
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        Text heading = Text.literal("Chunk Map Help").formatted(Formatting.BOLD);
        context.drawText(this.textRenderer, heading, (this.width - this.textRenderer.getWidth(heading)) / 2, layoutTop + 20, 0xFFFFFFFF, false);

        if (sideNavigation) {
            context.fill(navigationDividerX, contentTop, navigationDividerX + 1, contentBottom, 0x44FFFFFF);
        }

        textPanel.render(context, this.textRenderer, contentLeft, contentTop, contentRight, contentBottom, Math.min(scrollOffset, maxScroll));

        drawScrollbar(context);

    }

    private void layoutHelpLines() {
        int availableHeight = contentBottom - contentTop;
        int contentHeight = textPanel.layout(this.textRenderer, helpLines, contentRight - contentLeft, availableHeight);
        maxScroll = availableHeight > 0 ? Math.max(0, contentHeight - availableHeight) : 0;
    }

    private void drawScrollbar(DrawContext context) {
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics != null) {
            context.fill(metrics.x, metrics.trackTop, metrics.x + metrics.width,
                    metrics.trackTop + metrics.trackHeight, 0x33000000);
            context.fill(metrics.x, metrics.thumbY, metrics.x + metrics.width,
                    metrics.thumbY + metrics.thumbHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        scrollOffset = Math.clamp(Math.min(scrollOffset, maxScroll) - (int) (verticalAmount * SectionedText.LINE_SPACING), 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ScrollbarMetrics metrics = getScrollbarMetrics();
            if (metrics != null
                && mouseX >= metrics.x && mouseX < metrics.x + metrics.width
                && mouseY >= metrics.thumbY && mouseY < metrics.thumbY + metrics.thumbHeight) {
                scrollbarDragging = true;
                scrollbarDragOffsetY = (int) (mouseY - metrics.thumbY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollbarDragging) {
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

            scrollOffset = (int) Math.round(((double) (newThumbY - metrics.trackTop) / trackRange) * maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
