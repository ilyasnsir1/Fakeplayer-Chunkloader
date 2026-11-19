package de.chunkloader.client.screen;

import de.chunkloader.client.ChunkloaderClient;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.ChunkloaderNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ActiveChunkloadersScreen extends Screen {

    private List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders = new ArrayList<>();
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private boolean ignoreSearchChange = false;
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 40;
    private static final int SEARCH_HEIGHT = 18;
    private int contentTop;
    private int contentBottom;
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
        int itemsVisible = availableHeight / ITEM_HEIGHT;
        int total = getFilteredEntries().size();
        int maxItems = Math.max(0, total - itemsVisible);

        if (maxItems <= 0 || availableHeight <= 0 || total <= 0) {
            return null;
        }

        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = (int) ((double) itemsVisible / total * availableHeight);
        if (scrollbarHeight <= 0) {
            return null;
        }

        int thumbY = contentTop + (int) ((double) scrollOffset / maxItems * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, contentTop, availableHeight, thumbY, scrollbarHeight, maxItems);
    }

    private final Screen parentScreen;

    public ActiveChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries) {
        this(entries, null);
    }

    public ActiveChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> entries, Screen parentScreen) {
        super(Text.literal("Active Fakeplayer/Chunkplayer"));
        this.parentScreen = parentScreen;
        this.disabledChunkloaders = new ArrayList<>();
        if (entries != null) {
            this.disabledChunkloaders.addAll(entries);
        }
    }

    public Screen getParentScreen() {
        return parentScreen;
    }

    public void updateEntries(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders) {
        this.disabledChunkloaders = new ArrayList<>();
        if (disabledChunkloaders != null) {
            this.disabledChunkloaders.addAll(disabledChunkloaders);
        }
        this.scrollOffset = 0;
        this.clearChildren();
        this.init();
    }

    private List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> getFilteredEntries() {
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        if (query.isEmpty()) {
            return disabledChunkloaders;
        }

        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> filtered = new ArrayList<>();
        for (DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry : disabledChunkloaders) {
            if (entry == null) {
                continue;
            }
            String name = entry.name();
            if (name != null && name.toLowerCase().contains(query)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    @Override
    protected void init() {
        super.init();

        int searchX = 20;
        int searchWidth = Math.max(80, this.width - 40);
        int searchY = 35;

        if (searchField == null) {
            searchField = new TextFieldWidget(this.textRenderer, searchX, searchY, searchWidth, SEARCH_HEIGHT, Text.literal("Search"));
            searchField.setMaxLength(64);
            ignoreSearchChange = true;
            searchField.setText(searchQuery);
            ignoreSearchChange = false;
            searchField.setChangedListener(text -> {
                if (ignoreSearchChange) {
                    return;
                }
                searchQuery = text;
                scrollOffset = 0;
                this.clearChildren();
                this.init();
            });
            searchField.setPlaceholder(Text.literal("Search player...").formatted(Formatting.GRAY));
            this.addDrawableChild(searchField);
        } else {
            searchField.setX(searchX);
            searchField.setY(searchY);
            searchField.setWidth(searchWidth);
            searchField.setHeight(SEARCH_HEIGHT);
            ignoreSearchChange = true;
            searchField.setText(searchQuery);
            ignoreSearchChange = false;
            this.addDrawableChild(searchField);
        }

        contentTop = searchY + SEARCH_HEIGHT + 12;
        contentBottom = this.height - 60;

        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> visibleEntries = getFilteredEntries();

        int buttonWidth = 100;
        int buttonSpacing = 10;
        int buttonCount = 2;
        int buttonX = (this.width - (buttonWidth * buttonCount + buttonSpacing * (buttonCount - 1))) / 2;
        int buttonY = this.height - 30;

        if (parentScreen != null) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.close())
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build()
            );
            buttonX += buttonWidth + buttonSpacing;
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Disabled"),
                btn -> ChunkloaderNetworking.requestDisabledChunkloadersList())
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
        buttonX += buttonWidth + buttonSpacing;

        if (parentScreen == null) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Close"),
                    btn -> this.close())
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build()
            );
        }


        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;

        int maxScroll = Math.max(0, visibleEntries.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        TextRenderer textRenderer = this.textRenderer;
        int maxTextWidth = 0;
        for (var e : visibleEntries) {
            String eName = e.name() != null ? e.name() : "Unnamed";
            String ePosText = String.format("Chunk: %d, %d | Block: %d, %d, %d",
                e.chunkX(), e.chunkZ(), e.blockX(), e.blockY(), e.blockZ());
            String eDimText = "Dimension: " + e.dimension().replace("minecraft:", "");
            int textWidth = Math.max(
                textRenderer.getWidth(eName),
                Math.max(
                    textRenderer.getWidth(ePosText),
                    textRenderer.getWidth(eDimText)
                )
            );
            maxTextWidth = Math.max(maxTextWidth, textWidth);
        }

        for (int i = 0; i < itemsThatFitFully && (scrollOffset + i) < visibleEntries.size(); i++) {
            int index = scrollOffset + i;
            if (index < 0 || index >= visibleEntries.size()) {
                continue;
            }
            DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry = visibleEntries.get(index);
            int itemY = contentTop + i * ITEM_HEIGHT;

            if (itemY < contentTop || itemY + ITEM_HEIGHT > contentBottom) {
                continue;
            }

            int infoStartX = 20;
            int infoEndX = infoStartX + maxTextWidth;
            int editButtonWidgetSpacing = 15;
            int editButtonWidgetX = infoEndX + editButtonWidgetSpacing;
            int editButtonWidgetWidth = 60;

            int openMapButtonWidth = 80;
            int rightMargin = 20;
            int openMapButtonX = this.width - rightMargin - openMapButtonWidth;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Move"),
                    btn -> {
                        this.client.setScreen(new EditDisabledChunkloaderCoordsScreen(this, entry));
                    })
                .dimensions(editButtonWidgetX, itemY + 10, editButtonWidgetWidth, 20)
                .build()
            );

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Open map"),
                    btn -> ChunkloaderNetworking.requestOpenChunkMap(entry.chunkX(), entry.chunkZ(), entry.dimension()))
                .dimensions(openMapButtonX, itemY + 10, openMapButtonWidth, 20)
                .build()
            );
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        TextRenderer renderer = this.textRenderer;

        Text title = Text.literal("Active Fakeplayer/Chunkplayer").formatted(Formatting.BOLD, Formatting.GREEN);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, 20, 0xFF55FF55, false);

        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> visibleEntries = getFilteredEntries();

        if (visibleEntries.isEmpty()) {
            Text emptyText = Text.literal("No active players").formatted(Formatting.GRAY);
            int emptyWidth = renderer.getWidth(emptyText);
            context.drawText(renderer, emptyText, (this.width - emptyWidth) / 2, this.height / 2, 0xFFCCCCCC, false);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;

        int maxScroll = Math.max(0, visibleEntries.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        context.enableScissor(0, contentTop, this.width, contentBottom);

        for (int i = 0; i < itemsThatFitFully && (scrollOffset + i) < visibleEntries.size(); i++) {
            int index = scrollOffset + i;
            if (index < 0 || index >= visibleEntries.size()) {
                continue;
            }
            DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry = visibleEntries.get(index);
            int itemY = contentTop + i * ITEM_HEIGHT;

            if (itemY < contentTop || itemY + ITEM_HEIGHT > contentBottom) {
                continue;
            }

            String name = entry.name() != null ? entry.name() : "Unnamed";
            if (entry.sharedBy() != null) {
                name += " (Shared by " + entry.sharedBy() + ")";
            }
            Formatting color;
            int colorValue;
            if (entry.easterEggSkinIndex() >= 0) {
                color = Formatting.GOLD;
                colorValue = 0xFFFFAA00;
            } else if (entry.allowMobSpawning()) {
                if (entry.hasWarning()) {
                    color = Formatting.YELLOW;
                    colorValue = 0xFFFFFF55;
                } else {
                    color = Formatting.GREEN;
                    colorValue = 0xFF55FF55;
                }
            } else {
                color = Formatting.BLUE;
                colorValue = 0xFF5555FF;
            }
            Text nameText = Text.literal(name).formatted(color);
            context.drawText(renderer, nameText, 20, itemY + 5, colorValue, false);

            String posText = String.format("Chunk: %d, %d | Block: %d, %d, %d",
                entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
            Text positionText = Text.literal(posText).formatted(Formatting.GRAY);
            context.drawText(renderer, positionText, 20, itemY + 18, 0xFFCCCCCC, false);

            String dimText = entry.dimension().replace("minecraft:", "");
            Formatting dimColor = Formatting.GRAY;
            String dimLower = dimText.toLowerCase();
            if (dimLower.contains("overworld")) {
                dimColor = Formatting.GREEN;
            } else if (dimLower.contains("end")) {
                dimColor = Formatting.LIGHT_PURPLE;
            } else if (dimLower.contains("nether")) {
                dimColor = Formatting.RED;
            }
            Text dimensionText = Text.literal("Dimension: ").formatted(Formatting.GRAY)
                .append(Text.literal(dimText).formatted(dimColor));
            context.drawText(renderer, dimensionText, 20, itemY + 29, 0xFFFFFFFF, false);

            if (i < itemsThatFitFully - 1 && (scrollOffset + i + 1) < visibleEntries.size()) {
                int lineY = itemY + ITEM_HEIGHT - 1;
                int lineLeft = 10;
                int lineRight = this.width - 10;
                context.fill(lineLeft, lineY, lineRight, lineY + 1, 0x33FFFFFF);
            }
        }

        context.disableScissor();

        drawScrollbar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawScrollbar(DrawContext context) {
        int availableHeight = contentBottom - contentTop;
        int itemsVisible = availableHeight / ITEM_HEIGHT;
        int total = getFilteredEntries().size();
        int maxItems = Math.max(0, total - itemsVisible);

        if (maxItems <= 0) {
            return;
        }

        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = total <= 0 ? 0 : (int)((double)itemsVisible / total * availableHeight);
        if (maxItems > 0) {
            int scrollbarY = contentTop + (int)((double)scrollOffset / maxItems * (availableHeight - scrollbarHeight));

            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarWidth, contentBottom, 0x33000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
        }
    }

    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int availableHeight = contentBottom - contentTop;
        int maxItems = Math.max(0, getFilteredEntries().size() - (availableHeight / ITEM_HEIGHT));

        int scrollDelta = (int)(verticalAmount * 3);
        scrollOffset = Math.max(0, Math.min(maxItems, scrollOffset - scrollDelta));

        this.clearChildren();
        this.init();
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();

            if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
                if (this.getFocused() == searchField) {
                    this.setFocused(null);
                }
                searchField.setFocused(false);
            }

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
            newScroll = Math.max(0, Math.min(metrics.maxScroll, newScroll));

            if (newScroll != scrollOffset) {
                scrollOffset = newScroll;
                this.clearChildren();
                this.init();
            }
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

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (ChunkloaderClient.activeChunkloadersKey != null
                && ChunkloaderClient.activeChunkloadersKey.matchesKey(keyInput)) {
            this.client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void close() {
        this.client.setScreen(parentScreen);
    }

}
