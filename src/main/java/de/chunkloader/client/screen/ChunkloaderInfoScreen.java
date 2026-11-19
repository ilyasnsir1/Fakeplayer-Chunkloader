package de.chunkloader.client.screen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class ChunkloaderInfoScreen extends Screen {

    private static final int CONTENT_TEXT_RGB = 0xCCCCCC;

    private static Component content(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(CONTENT_TEXT_RGB)));
    }

    private enum InfoTopic {
        OVERVIEW("Overview"), SIMULATION("Simulation"), WORLD_DATA("World Data"), SERVER("Server");

        private final String label;

        InfoTopic(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private InfoTopic selectedTopic = InfoTopic.OVERVIEW;
    private final List<Component> infoLines = new ArrayList<>();
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
    private int totalContentHeight;

    private boolean scrollbarDragging = false;
    private int scrollbarDragOffsetY = 0;

    private final class TopicLink extends Button {
        private final InfoTopic topic;

        private TopicLink(InfoTopic topic, int x, int y, int width) {
            super(x, y, width, 20, Component.literal(topic.label),
                    ignored -> ChunkloaderInfoScreen.this.selectTopic(topic), DEFAULT_NARRATION);
            this.topic = topic;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            Font renderer = ChunkloaderInfoScreen.this.font;
            boolean selected = topic == selectedTopic;
            int color = selected ? 0xFFFFFF55 : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC;
            int labelWidth = renderer.width(getMessage());
            int labelX = sideNavigation ? getX() + 12 : getX() + (getWidth() - labelWidth + 12) / 2;
            int labelY = getY() + (getHeight() - 8) / 2;
            var label = getMessage().copy();
            if (isFocused()) {
                label.withStyle(ChatFormatting.UNDERLINE);
            }
            if (selected) {
                context.drawString(renderer, ">", labelX - 12, labelY, color, false);
            }
            context.drawString(renderer, label, labelX, labelY, color, false);
        }
    }

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

    private int getMaxScroll() {
        int availableHeight = contentBottom - contentTop;
        return availableHeight > 0 ? Math.max(0, totalContentHeight - availableHeight) : 0;
    }

    private ScrollbarMetrics getScrollbarMetrics() {
        int availableHeight = contentBottom - contentTop;
        int maxScroll = getMaxScroll();
        if (maxScroll == 0) {
            return null;
        }

        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = (int) ((double) availableHeight / totalContentHeight * availableHeight);
        if (scrollbarHeight <= 0) {
            return null;
        }

        int scrollbarY = contentTop + (int) ((double) Math.min(scrollOffset, maxScroll) / maxScroll * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, contentTop, availableHeight, scrollbarY, scrollbarHeight, maxScroll);
    }

    public ChunkloaderInfoScreen(Screen parent) {
        super(Component.literal("Mod Information"));
        this.parent = parent;
        rebuildInfoLines();
    }

    private void rebuildInfoLines() {
        infoLines.clear();
        switch (selectedTopic) {
            case OVERVIEW -> {
                addSection("What actually loads the chunks?",
                        "The Fakeplayer or Chunkplayer stands where you placed it. Behind the scenes, both modes give the server a chunk ticket. That ticket keeps the area running even if no real player is nearby.");
                addSection("When spawning needs a player",
                        "Some farms need a player nearby in addition to loaded chunks. A Fakeplayer can count as that player for natural mob spawning, regular spawners and Trial Spawners. A Chunkplayer doesn't count for those checks.",
                        "Spawning and targeting are separate. With \"Enable mob target\", hostile mobs that normally go after players can go after the Fakeplayer, though they can't hurt it.");
                addSection("What happens to paused mobs?",
                        "Mobs stay active by default with a new Chunkplayer. If you pause them, the server skips their ticks, including villager ticks, wherever only that Chunkplayer keeps them active. Crops and redstone still run. A nearby real player can keep the mobs active.");
                addSection("When only real players count",
                        "Fakeplayers and Chunkplayers don't count toward the number of players needed to skip the night. They don't trigger patrols, village sieges, skeleton horse traps or sculk shriekers, and they don't receive potion or beacon effects.");
            }
            case SIMULATION -> {
                addSection("Why water can flow where mobs stop",
                        "The green or blue square is the area selected on the map. It gets entity ticks and random ticks, so mobs can move and crops can grow.",
                        "The first ring outside that square still gets scheduled block and fluid ticks. Water can flow into it, but a mob that goes with the water can stop there. The second ring is loaded and doesn't get normal ticks.",
                        "At a range of 0, that's 1 selected chunk + 8 in the first ring + 16 in the second, for 25 loaded chunks. That's for one Fakeplayer or Chunkplayer on its own. Others, including nearby real players, can keep more of the area ticking.");
                addSection("Which areas can overlap?",
                        "The outer rings can overlap. The selected areas can't. A disabled player still reserves its square so you can restore it in the same spot.");
                addSection("Why natural spawning is different at SD 1",
                        "In Minecraft, a natural spawn needs to be more than 24 blocks from the player. That's distance in any direction, so the height difference counts too.",
                        "At SD 1, the Fakeplayer has a 3 x 3 area. I lowered its minimum to more than 8 blocks so a small farm can fit beside it. Only the minimum distance from the Fakeplayer changes. The other spawn rules still apply, including the usual 24-block buffer around real players.",
                        "At SD 0, the green area is only one chunk. If the Fakeplayer stands near an edge, even 8 blocks sideways can put the spawn outside it. I kept the usual 24-block rule there. Put the spawning floor more than 24 blocks above or below the Fakeplayer to keep it in the same chunk.",
                        "At SD 2 and 3, the area has enough room to keep the usual distance horizontally. These are the settings I'd use for a mob farm.");
                addSection("Why keep SD 0 at all?",
                        "I wanted to be able to load a single patch of crops with a Chunkplayer. I kept the same range of 0 to 3 for both modes so switching between them doesn't change which sizes are available.");
            }
            case WORLD_DATA -> {
                addSection("Taking your players with a world",
                        "Copy the whole chunkloader/ folder inside the world save. It includes your Fakeplayers and Chunkplayers, their owners and shares, mod permissions and custom skins.",
                        "The mod saves changes automatically. Enabled players start again when the world opens. Disabled players stay in the saved list.");
                addSection("If the player data can't be read",
                        "Player positions, settings and shares are saved in chunkloader_config.json. If that file can't be read, the mod tries a copy from chunkloader/backups/.");
                addSection("What stays on your computer",
                        "Map layouts, panel colors and mod keybinds are saved by your client. They don't follow the world save. When you apply a skin to a player, the server keeps the PNG in chunkloader/skins/ and sends it to clients running the mod.",
                        "Custom skins can be 64 x 64 or 64 x 32 PNGs, up to 128 KiB.");
            }
            case SERVER -> {
                addSection("Who keeps the chunks active?",
                        "The server keeps the chunks ticking. The client mod gives you the map, HUDs and custom skins.",
                        "On a dedicated server, operators at permission level 2 or higher have access by default. They can use /fp permission grant to let someone create and manage their own Fakeplayers and Chunkplayers.");
                addSection("Sharing access without giving up ownership",
                        "A friend needs mod access and must be online when you share one of your players. They can use it while you're offline. You can take back access even while they're offline.",
                        "Only the owner or an admin can decide who gets access, delete the player or reset it to defaults.",
                        "Ownership saved to your Minecraft account survives a name change. Some older players only have their owner's name saved. An admin can use /fp owner to link one to an account or transfer ownership.");
                addSection("What counts toward a limit?",
                        "Admins can set a server limit, a default per owner and personal limits. Use 0 for an unlimited server or default limit.",
                        "Only active Fakeplayers and Chunkplayers count. A shared player counts toward its owner's limit.",
                        "A personal limit of 0 blocks adding or reactivating your own players. Active ones keep running and you can still edit, move or turn them off.",
                        "An unlimited personal limit takes priority over the default per owner. Reset the personal limit to use that default again. The server limit applies in both cases.");
                addSection("The Tab list and server list",
                        "Admins can show or hide Fakeplayers and Chunkplayers in the Tab list. The multiplayer server list counts only real players either way.");
            }
        }
    }

    private void addSection(String heading, String... lines) {
        addSection(heading, ChatFormatting.YELLOW, lines);
    }

    private void addSection(String heading, ChatFormatting color, String... lines) {
        if (!infoLines.isEmpty()) {
            infoLines.add(Component.empty());
        }
        infoLines.add(Component.literal(heading).withStyle(ChatFormatting.BOLD, color));
        for (String line : lines) {
            infoLines.add(content(line));
        }
    }

    private void selectTopic(InfoTopic topic) {
        if (selectedTopic == topic) {
            return;
        }
        selectedTopic = topic;
        scrollOffset = 0;
        scrollbarDragging = false;
        rebuildInfoLines();
        layoutInfoLines();
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

        InfoTopic[] topics = InfoTopic.values();
        if (sideNavigation) {
            navigationLeft = 20;
            int navigationWidth = 80;
            navigationDividerX = navigationLeft + navigationWidth + 8;
            contentLeft = navigationDividerX + 16;
            for (int i = 0; i < topics.length; i++) {
                this.addRenderableWidget(new TopicLink(topics[i], navigationLeft,
                        contentTop + i * 24, navigationWidth));
            }
        } else {
            int gap = 4;
            int linkWidth = Math.min(96, (this.width - 40 - gap * (topics.length - 1)) / topics.length);
            int rowWidth = linkWidth * topics.length + gap * (topics.length - 1);
            int startX = (this.width - rowWidth) / 2;
            for (int i = 0; i < topics.length; i++) {
                this.addRenderableWidget(new TopicLink(topics[i], startX + i * (linkWidth + gap), layoutTop + 40, linkWidth));
            }
        }

        layoutInfoLines();

        ScreenNavigation.addBackAndCloseButtons(this, this::addRenderableWidget, layout.bottom());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        Component heading = Component.literal("Mod Information").withStyle(ChatFormatting.BOLD);
        context.drawString(this.font, heading, (this.width - this.font.width(heading)) / 2, layoutTop + 20, 0xFFFFFFFF, false);

        if (sideNavigation) {
            context.fill(navigationDividerX, contentTop, navigationDividerX + 1, contentBottom, 0x44FFFFFF);
        }

        textPanel.render(context, this.font, contentLeft, contentTop, contentRight, contentBottom, Math.min(scrollOffset, getMaxScroll()));

        drawScrollbar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void layoutInfoLines() {
        int availableHeight = Math.max(0, contentBottom - contentTop);
        totalContentHeight = textPanel.layout(this.font, infoLines, contentRight - contentLeft, availableHeight);
    }

    private void drawScrollbar(GuiGraphics context) {
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
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        scrollOffset = Math.clamp(Math.min(scrollOffset, metrics.maxScroll) - (int) (verticalAmount * SectionedText.LINE_SPACING), 0, metrics.maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
