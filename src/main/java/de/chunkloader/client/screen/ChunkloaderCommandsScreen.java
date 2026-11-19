package de.chunkloader.client.screen;

import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class ChunkloaderCommandsScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int ENTRY_GAP = 10;
    private static final int MIN_ENTRY_GAP = 5;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int TEXT_OVERHANG = 2;

    private record CommandEntry(String command, String description, String example) {
        private CommandEntry(String command, String description) {
            this(command, description, "");
        }
    }

    private enum Topic {
        BASICS("Basics", "Use /fp as a shortcut for /fakeplayer. Press Tab while typing the name of a Fakeplayer or Chunkplayer to see suggestions.",
            new CommandEntry("/fp help", "Opens Chunk Map Help. You can use it before placing your first Fakeplayer."),
            new CommandEntry("/fp add", "Creates a Fakeplayer where you're standing."),
            new CommandEntry("/fp add <x> <y> <z> [dimension]", "Places a Fakeplayer at those coordinates. Leave out the dimension to use your current one.", "/fp add 100 64 -20 minecraft:the_nether"),
            new CommandEntry("/fp list", "Lists active and disabled players you can manage, with their dimension and block coordinates."),
            new CommandEntry("/fp info <name>", "Shows a player's status, mode, location, selected area and mob settings."),
            new CommandEntry("/fp stats", "Shows player counts, selected chunk totals and server memory use.")),
        MANAGE("Manage", "You can turn players you own or have access to through sharing on or off. Owners and admins can also delete them.",
            new CommandEntry("/fp disable <name>", "Turns a player off and keeps its settings and position saved.", "/fp disable Fakeplayer1"),
            new CommandEntry("/fp restore <name>", "Turns a disabled player back on."),
            new CommandEntry("/fp move <name> <x> <y> <z> [dimension]", "Moves an active or disabled player. Leave out the dimension to keep it in the same one.", "/fp move Fakeplayer1 100 64 -20 minecraft:overworld"),
            new CommandEntry("/fp remove <name>", "Permanently deletes a saved player, whether it's enabled or disabled.")),
        SETTINGS("Settings", "These commands change one Fakeplayer or Chunkplayer. Use the name shown on its map.",
            new CommandEntry("/fp setradius <name> <0-3>", "Sets the selected area. A value of 0 selects one chunk, 1 selects 3 by 3, 2 selects 5 by 5, and 3 selects 7 by 7.", "/fp setradius Fakeplayer1 2"),
            new CommandEntry("/fp toggle <name>", "Switches between Fakeplayer and Chunkplayer mode."),
            new CommandEntry("/fp setmobtarget <name> <true/false>", "Use true to let mobs target a Fakeplayer."),
            new CommandEntry("/fp pausemobs <name>", "Pauses mobs in chunks kept active only by that Chunkplayer."),
            new CommandEntry("/fp resumemobs <name>", "Lets mobs tick again in that Chunkplayer's area."),
            new CommandEntry("/fp rename <name> <newName>", "Pick a free name with 1 to 16 letters or digits. Names of online players are reserved.", "/fp rename Fakeplayer1 CropFarm"),
            new CommandEntry("/fp namevisible <name> <true/false>", "Use false to hide the name above a player, or true to show it.")),
        VISUALS("Visuals", "Run these in-game for an active player you can use. Only you see the outlines.",
            new CommandEntry("/fp visualize <name>", "Toggles a flat outline of the selected area."),
            new CommandEntry("/fp visualize3d <name>", "Toggles the 3D outline of the selected area."),
            new CommandEntry("/fp visualize3d <name> <minY> <maxY>", "Toggles the 3D outline with a custom height range. Use two Y values from -64 to 320, lower one first.", "/fp visualize3d Fakeplayer1 0 64")),
        ACCESS("Access", "Owners and admins can manage shares. Admins can also grant mod access and assign owners.",
            new CommandEntry("/fp share <name> <player>", "Lets someone edit your Fakeplayer or Chunkplayer and switch it on or off. They must be online and already have mod access.", "/fp share CropFarm Steve"),
            new CommandEntry("/fp shares <name>", "Lists who has access through sharing."),
            new CommandEntry("/fp unshare <name> <player>", "Removes a player's share. They can be offline."),
            new CommandEntry("/fp permission grant <player>", "Gives an online player mod access so they can create and manage their own Fakeplayers and Chunkplayers.", "/fp permission grant Steve"),
            new CommandEntry("/fp permission revoke <player>", "Clears the grants for an online player."),
            new CommandEntry("/fp owner <name> <player>", "Gives ownership to an online player. Ownership is tied to their account.", "/fp owner CropFarm Steve")),
        LIMITS("Limits", "Admins can limit how many Fakeplayers and Chunkplayers are active. After a player's name, press Tab to see the available options.",
            new CommandEntry("/fp limits", "Shows the server's enabled count, server limit and default per-owner limit."),
            new CommandEntry("/fp limits server <limit>", "Caps enabled Fakeplayers and Chunkplayers across the server. Use 0 for unlimited.", "/fp limits server 20"),
            new CommandEntry("/fp limits default <limit>", "Sets the limit for owners without a personal override. Use 0 for unlimited.", "/fp limits default 4"),
            new CommandEntry("/fp limits player <player> <limit>", "Sets a personal limit for an online player. Use 0 to block adding or reactivating their own players. Active players keep running.", "/fp limits player Steve 2"),
            new CommandEntry("/fp limits player <player> status", "Shows how many active players they own and the limit that applies."),
            new CommandEntry("/fp limits player <player> unlimited", "Gives this player an unlimited personal limit. The server-wide cap still applies."),
            new CommandEntry("/fp limits player <player> reset", "Uses the default per-owner limit for this player again.")),
        SERVER("Server", "These commands require admin access. For disableall and removeall, leave off confirm to preview how many players are affected.",
            new CommandEntry("/fp disableall confirm", "Turns off every enabled player and keeps it saved."),
            new CommandEntry("/fp restoreall", "Restores disabled players up to the configured limits."),
            new CommandEntry("/fp removeall confirm", "Permanently deletes every saved player."),
            new CommandEntry("/fp removeall enabled confirm", "Permanently deletes every enabled player."),
            new CommandEntry("/fp removeall disabled confirm", "Permanently deletes every disabled player."),
            new CommandEntry("/fp tablist <true/false>", "Shows or hides Fakeplayers and Chunkplayers in the Tab list."),
            new CommandEntry("/fp reload", "Reloads the player configuration from disk.")),
        KEYBINDS("Keybinds", "These are the default keys. Change them in Minecraft's Controls or the map's Keybinds screen.",
            new CommandEntry("F6", "Toggles the Simulation Status HUD for Fakeplayer areas."),
            new CommandEntry("F7", "Toggles the Chunkplayer Status HUD."),
            new CommandEntry("F8", "Lists disabled players you can manage. Use Restore to turn one back on."),
            new CommandEntry("F9", "Lists active players you can manage. Use Open map to view their area."));

        private final String label;
        private final String introduction;
        private final CommandEntry[] entries;

        Topic(String label, String introduction, CommandEntry... entries) {
            this.label = label;
            this.introduction = introduction;
            this.entries = entries;
        }
    }

    private record TextLine(OrderedText text, int y, int color) {}
    private record Scrollbar(int x, int thumbY, int thumbHeight) {}

    private final Screen parent;
    private final List<TextLine> lines = new ArrayList<>();
    private Topic selectedTopic = Topic.BASICS;
    private boolean sideNavigation;
    private int navigationDividerX;
    private int contentLeft;
    private int contentRight;
    private int layoutTop;
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarDragOffsetY;

    private final class TopicLink extends ButtonWidget {
        private final Topic topic;

        private TopicLink(Topic topic, int x, int y, int width, int height) {
            super(x, y, width, height, Text.literal(topic.label),
                    ignored -> selectTopic(topic), DEFAULT_NARRATION_SUPPLIER);
            this.topic = topic;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            var renderer = ChunkloaderCommandsScreen.this.textRenderer;
            boolean selected = topic == selectedTopic;
            int color = selected ? 0xFFFFFF55 : (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            int labelX = sideNavigation ? getX() + 12 : getX() + (getWidth() - renderer.getWidth(getMessage())) / 2;
            int labelY = getY() + (getHeight() - 8) / 2;
            var label = getMessage().copy();
            if (isFocused()) {
                label.formatted(Formatting.UNDERLINE);
            }
            if (selected) {
                context.drawText(renderer, ">", labelX - 10, labelY, color, false);
            }
            context.drawText(renderer, label, labelX, labelY, color, false);
        }
    }

    public ChunkloaderCommandsScreen(Screen parent) {
        super(Text.literal("Commands"));
        this.parent = parent;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();
        var layout = ScreenNavigation.descriptionLayout(this);
        layoutTop = layout.top();
        sideNavigation = width >= 360 && layout.height() >= ScreenNavigation.MIN_DESCRIPTION_HEIGHT;
        contentLeft = 20;
        contentRight = width - 20;
        contentBottom = layout.bottom() - SectionedText.BOTTOM_INSET;

        Topic[] topics = Topic.values();
        if (sideNavigation) {
            int navigationLeft = 20;
            int navigationWidth = 80;
            navigationDividerX = navigationLeft + navigationWidth + 8;
            contentLeft = navigationDividerX + 16;
            contentTop = layoutTop + SectionedText.TOP_WITH_SIDEBAR;
            int firstLinkY = contentTop;
            int spacing = Math.min(24, (contentBottom - firstLinkY) / topics.length);
            for (int i = 0; i < topics.length; i++) {
                addDrawableChild(new TopicLink(topics[i], navigationLeft,
                        firstLinkY + i * spacing, navigationWidth, Math.min(20, spacing)));
            }
        } else {
            int columns = Math.min(4, Math.max(1, (width - 24) / 58));
            int rows = (topics.length + columns - 1) / columns;
            int rowHeight = 18;
            int linkWidth = Math.min(80, (width - 24) / columns);
            for (int i = 0; i < topics.length; i++) {
                int row = i / columns;
                int rowSize = Math.min(columns, topics.length - row * columns);
                int startX = (width - rowSize * linkWidth) / 2;
                addDrawableChild(new TopicLink(topics[i], startX + (i % columns) * linkWidth,
                        layoutTop + 34 + row * rowHeight, linkWidth, rowHeight));
            }
            contentTop = layoutTop + 46 + rows * rowHeight;
        }

        scrollbarDragging = false;
        layoutText();
        ScreenNavigation.addBackAndCloseButtons(this, this::addDrawableChild, layout.bottom());
    }

    private void selectTopic(Topic topic) {
        if (selectedTopic != topic) {
            selectedTopic = topic;
            scrollOffset = 0;
            scrollbarDragging = false;
            layoutText();
        }
    }

    private void layoutText() {
        layoutText(ENTRY_GAP);
        int overflow = getMaxScroll();
        int gaps = selectedTopic.entries.length;
        if (overflow > 0 && gaps > 0 && overflow <= gaps * (ENTRY_GAP - MIN_ENTRY_GAP)) {
            layoutText(ENTRY_GAP - Math.ceilDiv(overflow, gaps));
        }
    }

    private void layoutText(int entryGap) {
        lines.clear();
        int y = addWrappedLines(Text.literal(selectedTopic.introduction), 0, 0xFFCCCCCC) + entryGap;
        for (CommandEntry entry : selectedTopic.entries) {
            y = addWrappedLines(Text.literal(entry.command()), y, 0xFF55FF55);
            y = addWrappedLines(Text.literal(entry.description()), y, 0xFFCCCCCC);
            if (!entry.example().isEmpty()) {
                y = addWrappedLines(Text.literal(entry.example()).formatted(Formatting.ITALIC), y, 0xFF999999);
            }
            y += entryGap;
        }
        contentHeight = y - entryGap - LINE_HEIGHT + textRenderer.fontHeight;
    }

    private int addWrappedLines(Text text, int y, int color) {
        int maxWidth = Math.max(1, contentRight - contentLeft - 10);
        for (OrderedText line : textRenderer.wrapLines(text, maxWidth)) {
            lines.add(new TextLine(line, y, color));
            y += LINE_HEIGHT;
        }
        return y;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        Text heading = getTitle().copy().formatted(Formatting.BOLD);
        context.drawText(textRenderer, heading, (width - textRenderer.getWidth(heading)) / 2, layoutTop + 20, 0xFFFFFFFF, false);

        if (sideNavigation) {
            context.fill(navigationDividerX, contentTop, navigationDividerX + 1, contentBottom, 0x44FFFFFF);
        }

        if (contentBottom > contentTop) {
            context.enableScissor(contentLeft - TEXT_OVERHANG, contentTop, contentRight, contentBottom);
            int originY = contentTop - Math.min(scrollOffset, getMaxScroll());
            for (TextLine line : lines) {
                int y = originY + line.y();
                if (y + textRenderer.fontHeight > contentTop && y < contentBottom) {
                    context.drawText(textRenderer, line.text(), contentLeft, y, line.color(), false);
                }
            }
            context.disableScissor();

            Scrollbar scrollbar = getScrollbar();
            if (scrollbar != null) {
                int right = scrollbar.x() + SCROLLBAR_WIDTH;
                context.fill(scrollbar.x(), contentTop, right, contentBottom, 0x33000000);
                context.fill(scrollbar.x(), scrollbar.thumbY(), right, scrollbar.thumbY() + scrollbar.thumbHeight(), 0xFFAAAAAA);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int getMaxScroll() {
        int availableHeight = contentBottom - contentTop;
        return availableHeight > 0 ? Math.max(0, contentHeight - availableHeight) : 0;
    }

    private Scrollbar getScrollbar() {
        int maxScroll = getMaxScroll();
        int height = contentBottom - contentTop;
        if (maxScroll == 0 || height < 2) {
            return null;
        }
        int thumbHeight = MathHelper.clamp((int) ((double) height * height / contentHeight), Math.min(12, height - 1), height - 1);
        int thumbY = contentTop + (int) Math.round((double) Math.min(scrollOffset, maxScroll) / maxScroll * (height - thumbHeight));
        return new Scrollbar(contentRight + 6, thumbY, thumbHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0 && mouseX >= contentLeft && mouseX < width && mouseY >= contentTop && mouseY < contentBottom) {
            scrollOffset = MathHelper.clamp(Math.min(scrollOffset, maxScroll) - (int) (verticalAmount * 20), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Scrollbar scrollbar = getScrollbar();
            if (scrollbar != null && click.x() >= scrollbar.x() && click.x() < scrollbar.x() + SCROLLBAR_WIDTH
                    && click.y() >= scrollbar.thumbY() && click.y() < scrollbar.thumbY() + scrollbar.thumbHeight()) {
                scrollbarDragging = true;
                scrollbarDragOffsetY = (int) (click.y() - scrollbar.thumbY());
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (scrollbarDragging) {
            Scrollbar scrollbar = getScrollbar();
            if (scrollbar != null) {
                int trackRange = contentBottom - contentTop - scrollbar.thumbHeight();
                int thumbOffset = MathHelper.clamp((int) click.y() - scrollbarDragOffsetY - contentTop, 0, trackRange);
                scrollOffset = (int) Math.round((double) thumbOffset / trackRange * getMaxScroll());
                return true;
            }
            scrollbarDragging = false;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(click);
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
