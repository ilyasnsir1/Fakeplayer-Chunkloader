package de.chunkloader.client.screen;

import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class DisabledChunkloadersScreen extends Screen {
    
    private List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders = new ArrayList<>();
    private EditBox searchField;
    private String searchQuery = "";
    private boolean ignoreSearchChange = false;
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 40;
    private static final int SEARCH_HEIGHT = 18;
    private int contentTop;
    private int contentBottom;
    private final Screen parentScreen;

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
    
    public DisabledChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders) {
        this(disabledChunkloaders, null);
    }
    
    public DisabledChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders, Screen parentScreen) {
        super(Component.literal("Disabled Fakeplayer/Chunkplayer"));
        this.parentScreen = parentScreen;
        this.disabledChunkloaders = new ArrayList<>();
        if (disabledChunkloaders != null) {
            this.disabledChunkloaders.addAll(disabledChunkloaders);
        }
    }
    
    public void updateDisabledChunkloaders(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders) {
        this.disabledChunkloaders = new ArrayList<>();
        if (disabledChunkloaders != null) {
            this.disabledChunkloaders.addAll(disabledChunkloaders);
        }
        this.scrollOffset = 0;
        this.clearWidgets();
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
            searchField = new EditBox(this.font, searchX, searchY, searchWidth, SEARCH_HEIGHT, Component.literal("Search"));
            searchField.setMaxLength(64);
            ignoreSearchChange = true;
            searchField.setValue(searchQuery);
            ignoreSearchChange = false;
            searchField.setHint(Component.literal("Search player...").withStyle(ChatFormatting.GRAY));
            searchField.setResponder(text -> {
                if (ignoreSearchChange) {
                    return;
                }
                searchQuery = text;
                scrollOffset = 0;
                this.clearWidgets();
                this.init();
            });
            this.addRenderableWidget(searchField);
        } else {
            searchField.setX(searchX);
            searchField.setY(searchY);
            searchField.setWidth(searchWidth);
            searchField.setHeight(SEARCH_HEIGHT);
            ignoreSearchChange = true;
            searchField.setValue(searchQuery);
            ignoreSearchChange = false;
            this.addRenderableWidget(searchField);
        }

        contentTop = searchY + SEARCH_HEIGHT + 12;
        contentBottom = this.height - 60;

        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> visibleEntries = getFilteredEntries();
        
        int buttonWidth = 100;
        int buttonSpacing = 10;
        int totalButtonsWidth = parentScreen != null ? buttonWidth * 2 + buttonSpacing : buttonWidth;
        int buttonX = (this.width - totalButtonsWidth) / 2;
        int buttonY = this.height - 30;

        if (parentScreen != null) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Back"),
                    btn -> this.minecraft.setScreen(parentScreen))
                .bounds(buttonX, buttonY, buttonWidth, 20)
                .build()
            );
            buttonX += buttonWidth + buttonSpacing;
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.minecraft.setScreen(null))
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
        
        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;

        int maxScroll = Math.max(0, visibleEntries.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        var font = this.font;
        int maxTextWidth = 0;
        for (var e : visibleEntries) {
            String eName = e.name() != null ? e.name() : "Unnamed";
            String ePosText = String.format("Chunk: %d, %d | Block: %d, %d, %d",
                e.chunkX(), e.chunkZ(), e.blockX(), e.blockY(), e.blockZ());
            String eDimText = "Dimension: " + e.dimension().replace("minecraft:", "");
            int textWidth = Math.max(
                font.width(eName),
                Math.max(
                    font.width(ePosText),
                    font.width(eDimText)
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
            int editButtonSpacing = 15;
            int editButtonX = infoEndX + editButtonSpacing;
            int editButtonWidth = 60;
            
            int deleteButtonWidth = 70;
            int restoreButtonWidth = 70;
            int restoreDeleteSpacing = 10;
            int rightMargin = 20;
            
            int deleteButtonX = this.width - rightMargin - deleteButtonWidth;
            int restoreButtonX = deleteButtonX - restoreDeleteSpacing - restoreButtonWidth;
            
            this.addRenderableWidget(Button.builder(
                    Component.literal("Edit"),
                    btn -> {
                        this.minecraft.setScreen(new EditDisabledChunkloaderCoordsScreen(this, entry));
                    })
                .bounds(editButtonX, itemY + 10, editButtonWidth, 20)
                .build()
            );
            
            Button restoreButton = Button.builder(
                    Component.literal("Restore"),
                    btn -> {
                        ChunkloaderNetworking.sendRestoreDisabledChunkloader(entry.chunkX(), entry.chunkZ());
                        ChunkloaderNetworking.requestDisabledChunkloadersList();
                    })
                .bounds(restoreButtonX, itemY + 10, restoreButtonWidth, 20)
                .build();
            this.addRenderableWidget(restoreButton);
            
            this.addRenderableWidget(Button.builder(
                    Component.literal("Delete").withStyle(ChatFormatting.RED),
                    btn -> {
                        String name = entry.name() != null ? entry.name() : "Unnamed";
                        Component title = Component.literal("Delete Chunkloader?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        Component message = Component.literal("Are you sure you want to permanently delete\n" + name + "?");
                        DisabledChunkloadersScreen screen = this;
                        int deleteChunkX = entry.chunkX();
                        int deleteChunkZ = entry.chunkZ();
                        this.minecraft.setScreen(new ChunkloaderConfirmationScreen(
                            screen,
                            title,
                            message,
                            () -> {
                                List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> newList = new ArrayList<>(disabledChunkloaders);
                                newList.removeIf(e -> e.chunkX() == deleteChunkX && e.chunkZ() == deleteChunkZ);
                                disabledChunkloaders.clear();
                                disabledChunkloaders.addAll(newList);
                                scrollOffset = 0;
                                ChunkloaderNetworking.sendDeleteDisabledChunkloader(deleteChunkX, deleteChunkZ);
                                ChunkloaderNetworking.requestDisabledChunkloadersList();
                                this.minecraft.setScreen(screen);
                            },
                            null
                        ));
                    })
                .bounds(deleteButtonX, itemY + 10, deleteButtonWidth, 20)
                .build()
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);
        
        var font = this.font;
        
        Component title = Component.literal("Disabled Fakeplayer/Chunkplayer").withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, (this.width - titleWidth) / 2, 20, 0xFFFF5555, false);
        
        List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> visibleEntries = getFilteredEntries();

        if (visibleEntries.isEmpty()) {
            Component emptyText = Component.literal("No disabled fakeplayers/chunkplayers").withStyle(ChatFormatting.GRAY);
            int emptyWidth = font.width(emptyText);
            graphics.drawString(font, emptyText, (this.width - emptyWidth) / 2, this.height / 2, 0xFFCCCCCC, false);
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }
        
        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;
        
        int maxScroll = Math.max(0, visibleEntries.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        
        graphics.enableScissor(0, contentTop, this.width, contentBottom);
        
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
            ChatFormatting color;
            int colorValue;
            if (entry.allowMobSpawning()) {
                if (entry.hasWarning()) {
                    color = ChatFormatting.YELLOW;
                    colorValue = 0xFFFFFF55;
                } else {
                    color = ChatFormatting.GREEN;
                    colorValue = 0xFF55FF55;
                }
            } else {
                color = ChatFormatting.BLUE;
                colorValue = 0xFF5596FF;
            }
            Component nameText = Component.literal(name).withStyle(color);
            graphics.drawString(font, nameText, 20, itemY + 5, colorValue, false);
            
            String posText = String.format("Chunk: %d, %d | Block: %d, %d, %d", 
                entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
            Component positionText = Component.literal(posText).withStyle(ChatFormatting.GRAY);
            graphics.drawString(font, positionText, 20, itemY + 18, 0xFFCCCCCC, false);
            
            String dimText = entry.dimension().replace("minecraft:", "");
            ChatFormatting dimColor = ChatFormatting.GRAY;
            String dimLower = dimText.toLowerCase();
            if (dimLower.contains("overworld")) {
                dimColor = ChatFormatting.GREEN;
            } else if (dimLower.contains("end")) {
                dimColor = ChatFormatting.LIGHT_PURPLE;
            } else if (dimLower.contains("nether")) {
                dimColor = ChatFormatting.RED;
            }
            Component dimensionText = Component.literal("Dimension: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(dimText).withStyle(dimColor));
            graphics.drawString(font, dimensionText, 20, itemY + 29, 0xFFFFFFFF, false);
            
            if (i < itemsThatFitFully - 1 && (scrollOffset + i + 1) < visibleEntries.size()) {
                int lineY = itemY + ITEM_HEIGHT - 1;
                int lineLeft = 10;
                int lineRight = this.width - 10;
                graphics.fill(lineLeft, lineY, lineRight, lineY + 1, 0x33FFFFFF);
            }
        }
        
        graphics.disableScissor();
        
        drawScrollbar(graphics);
        
        super.render(graphics, mouseX, mouseY, delta);
    }
    
    private void drawScrollbar(GuiGraphics graphics) {
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
            
            graphics.fill(scrollbarX, contentTop, scrollbarX + scrollbarWidth, contentBottom, 0x33000000);
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
        }
    }
    
    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int availableHeight = contentBottom - contentTop;
        int maxItems = Math.max(0, getFilteredEntries().size() - (availableHeight / ITEM_HEIGHT));
        
        int scrollDelta = (int)(verticalAmount * 3);
        scrollOffset = Math.max(0, Math.min(maxItems, scrollOffset - scrollDelta));
        
        this.clearWidgets();
        this.init();
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && searchField != null && !searchField.isMouseOver(event.x(), event.y())) {
            if (this.getFocused() == searchField) {
                this.setFocused(null);
            }
            searchField.setFocused(false);
        }

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
            newScroll = Math.max(0, Math.min(metrics.maxScroll, newScroll));

            if (newScroll != scrollOffset) {
                scrollOffset = newScroll;
                this.clearWidgets();
                this.init();
            }
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
    
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

