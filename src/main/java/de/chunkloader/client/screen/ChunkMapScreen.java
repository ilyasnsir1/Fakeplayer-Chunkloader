package de.chunkloader.client.screen;

import com.google.common.collect.ImmutableList;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.ChunkloaderNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ChunkMapScreen extends Screen {
    
    private static ClientConfig clientConfig = null;

    private static final int CELL_SIZE = 18;
    private static final int ACTION_ROW_GAP = 2;
    private static final int ACTION_SEARCH_HEIGHT = 18;
    private static final int ACTION_SEARCH_GAP = 0;
    private static final int ACTION_SEARCH_Y_OFFSET = -4;
    private static final int ACTION_SEARCH_LIST_GAP = 3;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final Identifier GRID_OVERLAY = Identifier.of("chunkloader", "textures/gui/grid_overlay.png");
    private static final Identifier HOVER_OVERLAY = Identifier.of("chunkloader", "textures/gui/cell_overlay.png");
    private static final Identifier FALLBACK_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private ChunkMapData data;
    private ChunkMapGrid grid;
    private int gridWidth;
    private int gridHeight;
    private int gridLeft;
    private int gridTop;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelWidth;
    private int leftPanelHeight;
    private int topBoxX;
    private int topBoxY;
    private int topBoxWidth;
    private int topBoxHeight;
    private final List<ButtonWidget> topBoxButtons = new ArrayList<>();
    private final List<ButtonWidget> actionButtons = new ArrayList<>();
    private final Map<ButtonWidget, Integer> actionButtonYOffset = new HashMap<>();
    private final Map<ButtonWidget, Boolean> buttonOriginalActiveState = new HashMap<>();
    private final List<ActionHeaderLayout> actionHeaderLayouts = new ArrayList<>();
    private int actionContentHeight = 0;
    private TextFieldWidget actionSearchField;
    private String actionSearchQuery = "";
    private ButtonWidget resetButton;
    private ButtonWidget infoButton;
    private ButtonWidget closeButton;
    private int footerRowY;
    private int actionScrollOffset = 0;
    private int actionViewportLeft;
    private int actionViewportRight;
    private int actionViewportTop;
    private int actionViewportBottom;
    private boolean buttonsNeedUpdate = true;
    private int lastScrollOffset = -1;
    private Boolean previousAllowMobSpawning = null;

    private boolean actionScrollbarDragging = false;
    private int actionScrollbarDragOffsetY = 0;

    private static final int TOPBOX_BUTTON_WIDTH = 50;
    private static final int TOPBOX_BUTTON_HEIGHT = 20;
    private static final int TOPBOX_BUTTON_COUNT = 5;

    private enum VerticalTopBoxPlacement {
        NONE,
        OUTER_LEFT,
        OUTER_RIGHT
    }

    private enum ChunkMapLayoutPreset {
        TOP,
        LEFT,
        RIGHT,
        BOTTOM,
        TOP_SWAP,
        LEFT_SWAP,
        RIGHT_SWAP,
        BOTTOM_SWAP;

        static ChunkMapLayoutPreset fromConfig(ClientConfig config) {
            if (config == null) {
                return TOP;
            }
            String raw = config.getChunkMapLayoutPreset();
            if (raw == null) {
                return TOP;
            }
            try {
                return ChunkMapLayoutPreset.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return TOP;
            }
        }

        ChunkMapLayoutPreset next() {
            return switch (this) {
                case TOP -> LEFT;
                case LEFT -> RIGHT;
                case RIGHT -> BOTTOM;
                case BOTTOM -> TOP_SWAP;
                case TOP_SWAP -> LEFT_SWAP;
                case LEFT_SWAP -> RIGHT_SWAP;
                case RIGHT_SWAP -> BOTTOM_SWAP;
                case BOTTOM_SWAP -> TOP;
            };
        }

        boolean isVerticalButtonBar() {
            return switch (this) {
                case LEFT, RIGHT, LEFT_SWAP, RIGHT_SWAP -> true;
                default -> false;
            };
        }

        boolean isTopBar() {
            return this == TOP || this == TOP_SWAP;
        }

        boolean isBottomBar() {
            return this == BOTTOM || this == BOTTOM_SWAP;
        }

        boolean isSwappedPanels() {
            return this == TOP_SWAP || this == LEFT_SWAP || this == RIGHT_SWAP || this == BOTTOM_SWAP;
        }

        VerticalTopBoxPlacement getVerticalTopBoxPlacement() {
            return switch (this) {
                case LEFT, LEFT_SWAP -> VerticalTopBoxPlacement.OUTER_LEFT;
                case RIGHT, RIGHT_SWAP -> VerticalTopBoxPlacement.OUTER_RIGHT;
                default -> VerticalTopBoxPlacement.NONE;
            };
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

    private ScrollbarMetrics getActionScrollbarMetrics() {
        int maxScroll = getMaxActionScroll();
        if (maxScroll <= 0) {
            return null;
        }

        int availableHeight = Math.max(0, (actionViewportBottom - actionViewportTop));
        int totalHeight = getActionContentHeight();
        if (availableHeight <= 0 || totalHeight <= 0) {
            return null;
        }

        int scrollbarWidth = 3;
        int scrollbarX = panelX + panelWidth - scrollbarWidth - 2;
        int scrollbarHeight = (int) ((double) availableHeight / totalHeight * availableHeight);
        scrollbarHeight = Math.max(8, Math.min(availableHeight, scrollbarHeight));
        if (scrollbarHeight <= 0) {
            return null;
        }

        int scrollbarTrackTop = actionViewportTop;
        int scrollbarY = scrollbarTrackTop + (int) ((double) actionScrollOffset / maxScroll * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, scrollbarTrackTop, availableHeight, scrollbarY, scrollbarHeight, maxScroll);
    }

    public ChunkMapScreen(ChunkMapData data) {
        super(Text.literal("Chunk Loader Map"));
        this.data = data;
        this.previousAllowMobSpawning = data.allowMobSpawning();
    }
    
    public void updateData(ChunkMapData newData) {
        boolean previousWasFakeplayer = this.data != null && this.data.allowMobSpawning();
        boolean newIsFakeplayer = newData.allowMobSpawning();
        
        this.data = newData;
        
        if (previousAllowMobSpawning != null && previousWasFakeplayer != newIsFakeplayer) {
            if (previousWasFakeplayer && !newIsFakeplayer) {
                de.chunkloader.client.hud.SimulationStatusHUD.setEnabled(false);
            } else if (!previousWasFakeplayer && newIsFakeplayer) {
                de.chunkloader.client.hud.ChunkplayerStatusHUD.setEnabled(false);
            }
        }
        previousAllowMobSpawning = newIsFakeplayer;
        if (this.grid != null) {
            ClientWorld world = MinecraftClient.getInstance().world;
            this.grid.close();
            this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE);
        }
        if (panelWidth > 0) {
            int savedScrollOffset = actionScrollOffset;
            buildTopBoxButtons();
            ensureActionSearchField();
            buildActionButtons();
            actionScrollOffset = clampActionScrollOffset(savedScrollOffset, getMaxActionScroll());
            buttonsNeedUpdate = true;
        }
    }

    @Override
    protected void init() {
        super.init();
        
        if (clientConfig == null) {
            clientConfig = ClientConfig.load();
        }
        
        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);

        this.gridWidth = data.mapWidth() * CELL_SIZE + 2;
        this.gridHeight = data.mapHeight() * CELL_SIZE + 2;

        this.panelWidth = Math.min(160, 160);
        if (this.panelWidth < 120) {
            this.panelWidth = 120;
        }

        this.leftPanelWidth = 100;

        int verticalTopBoxPadding = 8;
        int verticalTopBoxWidth = TOPBOX_BUTTON_WIDTH + verticalTopBoxPadding * 2;
        int gap = 12;

        boolean swappedPanels = layoutPreset.isSwappedPanels();
        int leftSlotWidth = swappedPanels ? this.panelWidth : this.leftPanelWidth;
        int rightSlotWidth = swappedPanels ? this.leftPanelWidth : this.panelWidth;
        VerticalTopBoxPlacement verticalTopBoxPlacement = layoutPreset.getVerticalTopBoxPlacement();

        int totalWidth = 0;
        if (verticalTopBoxPlacement == VerticalTopBoxPlacement.OUTER_LEFT) {
            totalWidth += verticalTopBoxWidth + gap;
        }
        totalWidth += leftSlotWidth + gap;
        totalWidth += this.gridWidth + gap;
        totalWidth += rightSlotWidth;
        if (verticalTopBoxPlacement == VerticalTopBoxPlacement.OUTER_RIGHT) {
            totalWidth += gap + verticalTopBoxWidth;
        }

        int startX = (this.width - totalWidth) / 2;
        if (startX < 16) {
            startX = 16;
        }

        int framePadding = 6;
        int mapFrameHeight = this.gridHeight + 2 * framePadding;
        int leftPanelBorder = 2;

        int frameTop;
        if (layoutPreset.isBottomBar()) {
            int requiredHeight = mapFrameHeight + 80;
            frameTop = (this.height - requiredHeight) / 2;
        } else {
            frameTop = (this.height - mapFrameHeight) / 2;
        }
        if (frameTop < 32) {
            frameTop = 32;
        }

        this.leftPanelY = frameTop + leftPanelBorder;
        this.leftPanelHeight = mapFrameHeight - 2 * leftPanelBorder;

        int cursorX = startX;
        if (verticalTopBoxPlacement == VerticalTopBoxPlacement.OUTER_LEFT) {
            this.topBoxX = cursorX;
            cursorX += verticalTopBoxWidth + gap;
        }

        int leftSlotX = cursorX;
        cursorX += leftSlotWidth + gap;

        this.gridLeft = cursorX;
        cursorX += this.gridWidth + gap;

        int rightSlotX = cursorX;
        cursorX += rightSlotWidth;

        if (verticalTopBoxPlacement == VerticalTopBoxPlacement.OUTER_RIGHT) {
            cursorX += gap;
            this.topBoxX = cursorX;
            cursorX += verticalTopBoxWidth;
        }

        if (!swappedPanels) {
            this.leftPanelX = leftSlotX;
            this.panelX = rightSlotX;
        } else {
            this.panelX = leftSlotX;
            this.leftPanelX = rightSlotX;
        }

        this.gridTop = frameTop + framePadding;

        this.panelY = this.leftPanelY;
        this.panelHeight = this.leftPanelHeight;

        int closeButtonWidth = 100;
        int closeButtonHeight = 20;
        int contentBottom = this.leftPanelY + this.leftPanelHeight;
        int closeButtonY;

        if (layoutPreset.isTopBar()) {
            int buttonSpacing = 12;
            int padding = 16;
            int numButtons = TOPBOX_BUTTON_COUNT;
            this.topBoxWidth = numButtons * TOPBOX_BUTTON_WIDTH + (numButtons - 1) * buttonSpacing + padding * 2 + 80;
            this.topBoxHeight = 28;
            this.topBoxX = (this.width - this.topBoxWidth) / 2;
            this.topBoxY = 35;
            closeButtonY = contentBottom + 10;
            this.footerRowY = closeButtonY;
        } else if (layoutPreset.isVerticalButtonBar()) {
            this.topBoxWidth = verticalTopBoxWidth;
            this.topBoxHeight = this.leftPanelHeight;
            this.topBoxY = this.leftPanelY;
            closeButtonY = contentBottom + 10;
            this.footerRowY = closeButtonY;
        } else {
            int buttonSpacing = 12;
            int padding = 16;
            int numButtons = TOPBOX_BUTTON_COUNT;
            this.topBoxWidth = numButtons * TOPBOX_BUTTON_WIDTH + (numButtons - 1) * buttonSpacing + padding * 2 + 80;
            this.topBoxHeight = 28;
            this.topBoxX = (this.width - this.topBoxWidth) / 2;

            this.footerRowY = contentBottom + 10;
            closeButtonY = this.footerRowY;

            int desiredTopBoxY = this.footerRowY + closeButtonHeight + 18;
            int maxTopBoxY = this.height - 8 - this.topBoxHeight;
            this.topBoxY = Math.min(desiredTopBoxY, maxTopBoxY);
        }
        
        ClientWorld world = MinecraftClient.getInstance().world;
        this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE);
        buildTopBoxButtons();

        // Screen.init() rebuilds the drawable/child lists; recreate the widget so it's interactable after returning
        // from other screens (e.g., list/menu/cancel flows).
        this.actionSearchField = null;
        ensureActionSearchField();
        buildActionButtons();
        
        int closeButtonX = this.gridLeft + (this.gridWidth - closeButtonWidth) / 2;
        this.closeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                btn -> this.close())
            .dimensions(closeButtonX, closeButtonY, closeButtonWidth, closeButtonHeight)
            .build()
        );
    }

    private void cycleLayoutPreset() {
        if (clientConfig == null) {
            clientConfig = ClientConfig.load();
        }

        ChunkMapLayoutPreset current = ChunkMapLayoutPreset.fromConfig(clientConfig);
        ChunkMapLayoutPreset next = current.next();
        clientConfig.setChunkMapLayoutPreset(next.name());

        actionScrollbarDragging = false;
        actionScrollbarDragOffsetY = 0;

        if (this.grid != null) {
            this.grid.close();
            this.grid = null;
        }

        buttonsNeedUpdate = true;
        lastScrollOffset = -1;

        MinecraftClient.getInstance().setScreen(new ChunkMapScreen(this.data));
    }

    @Override
    public void removed() {
        if (this.grid != null) {
            this.grid.close();
        }
        super.removed();
    }

    @Override
    public void close() {
        if (this.grid != null) {
            this.grid.close();
        }
        super.close();
    }

    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        drawMapFrame(context);

        if (buttonsNeedUpdate || lastScrollOffset != actionScrollOffset) {
            updateButtonPositions();
            buttonsNeedUpdate = false;
            lastScrollOffset = actionScrollOffset;
        }
        
        if (this.grid != null) {
            context.enableScissor(gridLeft - 2, gridTop - 2, gridLeft + gridWidth + 2, gridTop + gridHeight + 2);
            grid.render(context, mouseX, mouseY);
            drawChunkloaderPoints(context, mouseX, mouseY);
            context.disableScissor();
        }
        
        drawTopBox(context);
        drawLeftPanel(context);
        drawSidePanel(context);

        if (actionSearchField != null) {
            actionSearchField.render(context, mouseX, mouseY, delta);
        }

        int scissorTop = actionViewportTop;
        if (actionSearchField != null) {
            scissorTop = actionSearchField.getY() + actionSearchField.getHeight() + ACTION_SEARCH_LIST_GAP;
        }
        scissorTop = Math.min(scissorTop, actionViewportBottom);
        context.enableScissor(actionViewportLeft, scissorTop, actionViewportRight, actionViewportBottom);
        drawActionHeaders(context);
        for (ButtonWidget button : actionButtons) {
            if (button != resetButton) {
                button.render(context, mouseX, mouseY, delta);
            }
        }
        context.disableScissor();
        
        drawActionScrollbar(context);
        
        for (ButtonWidget button : topBoxButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        
        if (resetButton != null) {
            resetButton.render(context, mouseX, mouseY, delta);
        }
        
        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        ChunkMapGrid.Cell hovered = grid != null ? grid.getHoveredCell(mouseX, mouseY) : null;
        if (hovered != null) {
            context.drawTooltip(textRenderer, hovered.buildTooltip(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOverActionViewport(mouseX, mouseY)) {
            int maxScroll = getMaxActionScroll();
            if (maxScroll > 0) {
                int scrollStep = ACTION_BUTTON_HEIGHT + ACTION_ROW_GAP;
                int next = actionScrollOffset - (int) (verticalAmount * scrollStep);
                actionScrollOffset = clampActionScrollOffset(next, maxScroll);
                buttonsNeedUpdate = true;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isMouseOverActionViewport(double mouseX, double mouseY) {
        return mouseX >= actionViewportLeft
            && mouseX < actionViewportRight
            && mouseY >= actionViewportTop
            && mouseY < actionViewportBottom;
    }

    private int getMaxActionScroll() {
        int viewportHeight = Math.max(0, actionViewportBottom - actionViewportTop);
        int contentHeight = getActionContentHeight();
        return Math.max(0, contentHeight - viewportHeight);
    }

    private int getActionContentHeight() {
        return Math.max(0, actionContentHeight);
    }

    private int clampActionScrollOffset(int value, int maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    private void drawActionScrollbar(DrawContext context) {
        int maxScroll = getMaxActionScroll();
        if (maxScroll <= 0) {
            return;
        }

        int availableHeight = Math.max(0, (actionViewportBottom - actionViewportTop));
        int totalHeight = getActionContentHeight();

        int scrollbarWidth = 3;
        int scrollbarX = panelX + panelWidth - scrollbarWidth - 2;
        int scrollbarHeight = (int) ((double) availableHeight / totalHeight * availableHeight);
        scrollbarHeight = Math.max(8, Math.min(availableHeight, scrollbarHeight));

        int scrollbarTrackTop = actionViewportTop;
        int scrollbarY = scrollbarTrackTop + (int) ((double) actionScrollOffset / maxScroll * (availableHeight - scrollbarHeight));

        int scrollbarTrackColor = clientConfig != null ? clientConfig.getScrollbarTrackColor() : 0x33000000;
        int scrollbarThumbColor = clientConfig != null ? clientConfig.getScrollbarThumbColor() : 0xFFAAAAAA;
        context.fill(scrollbarX, scrollbarTrackTop, scrollbarX + scrollbarWidth, scrollbarTrackTop + availableHeight, scrollbarTrackColor);
        context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, scrollbarThumbColor);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();

            if (actionSearchField != null && !actionSearchField.isMouseOver(mouseX, mouseY)) {
                if (this.getFocused() == actionSearchField) {
                    this.setFocused(null);
                }
                actionSearchField.setFocused(false);
            }

            ScrollbarMetrics metrics = getActionScrollbarMetrics();
            if (metrics != null
                && mouseX >= metrics.x && mouseX < metrics.x + metrics.width
                && mouseY >= metrics.thumbY && mouseY < metrics.thumbY + metrics.thumbHeight) {
                actionScrollbarDragging = true;
                actionScrollbarDragOffsetY = (int) (mouseY - metrics.thumbY);
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        if (actionScrollbarDragging) {
            double mouseY = click.y();
            ScrollbarMetrics metrics = getActionScrollbarMetrics();
            if (metrics == null) {
                actionScrollbarDragging = false;
                return false;
            }

            int trackRange = metrics.trackHeight - metrics.thumbHeight;
            if (trackRange <= 0) {
                return true;
            }

            int newThumbY = (int) mouseY - actionScrollbarDragOffsetY;
            newThumbY = Math.max(metrics.trackTop, Math.min(metrics.trackTop + trackRange, newThumbY));

            int newScroll = (int) Math.round(((double) (newThumbY - metrics.trackTop) / trackRange) * metrics.maxScroll);
            actionScrollOffset = clampActionScrollOffset(newScroll, metrics.maxScroll);
            buttonsNeedUpdate = true;
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (actionScrollbarDragging) {
            actionScrollbarDragging = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    private void drawActionHeaders(DrawContext context) {
        if (actionHeaderLayouts.isEmpty()) {
            return;
        }

        int contentTop = actionViewportTop;
        int buttonX = panelX + 8;
        int buttonWidth = panelWidth - 16;

        int textColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        for (ActionHeaderLayout headerLayout : actionHeaderLayouts) {
            int rowY = contentTop + headerLayout.yOffset - actionScrollOffset;
            int textY = rowY + (headerLayout.height - this.textRenderer.fontHeight) / 2 + 1;
            if (textY + this.textRenderer.fontHeight < actionViewportTop || textY > actionViewportBottom) {
                continue;
            }

            int headerWidth = this.textRenderer.getWidth(headerLayout.text);
            int x = buttonX + Math.max(0, (buttonWidth - headerWidth) / 2);
            context.drawText(this.textRenderer, headerLayout.text, x, textY, textColor, false);
        }
    }

    private static final class ActionHeaderLayout {
        private final int yOffset;
        private final int height;
        private final Text text;

        private ActionHeaderLayout(int yOffset, int height, Text text) {
            this.yOffset = yOffset;
            this.height = height;
            this.text = text;
        }
    }
    
    private void drawMapFrame(DrawContext context) {
        int framePadding = 6;
        int borderThickness = 4;
        
        int innerLeft = gridLeft;
        int innerTop = gridTop;
        int innerRight = gridLeft + gridWidth;
        int innerBottom = gridTop + gridHeight;
        
        int frameLeft = innerLeft - framePadding;
        int frameTop = innerTop - framePadding;
        int frameRight = innerRight + framePadding;
        int frameBottom = innerBottom + framePadding;
        
        int frameColor = clientConfig != null ? clientConfig.getFrameColor() : 0xFF111417;
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        context.fill(frameLeft, frameTop, frameRight, frameBottom, frameColor);
        context.fill(innerLeft - borderThickness, innerTop - borderThickness, innerRight + borderThickness, innerBottom + borderThickness, panelColor);
    }
    

    private void updateButtonPositions() {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        int padding = 8;
        int innerTop = panelY + padding + ACTION_SEARCH_Y_OFFSET + ACTION_SEARCH_HEIGHT + ACTION_SEARCH_GAP + ACTION_SEARCH_LIST_GAP;
        int innerBottom = panelY + panelHeight - padding;
        int viewportTop = innerTop;
        int viewportBottom = Math.max(viewportTop, innerBottom);

        this.actionViewportLeft = panelX;
        this.actionViewportRight = panelX + panelWidth;
        this.actionViewportTop = viewportTop;
        this.actionViewportBottom = viewportBottom;

        int contentHeight = getActionContentHeight();
        int actualViewportHeight = Math.max(0, viewportBottom - viewportTop);
        int maxScroll = Math.max(0, contentHeight - actualViewportHeight);
        actionScrollOffset = clampActionScrollOffset(actionScrollOffset, maxScroll);

        int contentTop = viewportTop;

        for (ButtonWidget button : actionButtons) {
            if (button == resetButton) {
                continue;
            }
            Integer yOffset = actionButtonYOffset.get(button);
            if (yOffset == null) {
                continue;
            }
            int buttonY = contentTop + yOffset - actionScrollOffset;
            button.setY(buttonY);

            int buttonBottom = buttonY + ACTION_BUTTON_HEIGHT;
            boolean isVisible = buttonBottom > actionViewportTop && buttonY < actionViewportBottom;

            Boolean originalActive = buttonOriginalActiveState.get(button);
            if (originalActive == null) {
                originalActive = button.active;
                buttonOriginalActiveState.put(button, originalActive);
            }

            button.active = isVisible && originalActive;
        }

        if (actionSearchField != null) {
            actionSearchField.setX(panelX + 8);
            actionSearchField.setY(panelY + padding + ACTION_SEARCH_Y_OFFSET);
            actionSearchField.setWidth(panelWidth - 16);
            actionSearchField.setHeight(ACTION_SEARCH_HEIGHT);
        }
    }

    private void ensureActionSearchField() {
        int padding = 8;
        int x = panelX + 8;
        int y = panelY + padding + ACTION_SEARCH_Y_OFFSET;
        int width = panelWidth - 16;

        if (actionSearchField == null) {
            actionSearchField = new TextFieldWidget(this.textRenderer, x, y, width, ACTION_SEARCH_HEIGHT, Text.literal("Search"));
            actionSearchField.setMaxLength(64);
            actionSearchField.setText(actionSearchQuery);
            actionSearchField.setDrawsBackground(true);
            actionSearchField.setChangedListener(text -> {
                actionSearchQuery = text;
                buildActionButtons();
                actionScrollOffset = clampActionScrollOffset(actionScrollOffset, getMaxActionScroll());
                buttonsNeedUpdate = true;
            });
            applyActionSearchFieldStyle();
            this.addDrawableChild(actionSearchField);
        } else {
            actionSearchField.setX(x);
            actionSearchField.setY(y);
            actionSearchField.setWidth(width);
            actionSearchField.setHeight(ACTION_SEARCH_HEIGHT);
            actionSearchField.setText(actionSearchQuery);
            actionSearchField.setDrawsBackground(true);
            applyActionSearchFieldStyle();
        }
    }

    private void applyActionSearchFieldStyle() {
        if (actionSearchField == null) {
            return;
        }
        if (clientConfig == null) {
            return;
        }

        actionSearchField.setEditableColor(clientConfig.getActionSearchTextColor());

        int placeholderRgb = clientConfig.getActionSearchPlaceholderColor() & 0x00FFFFFF;
        actionSearchField.setPlaceholder(Text.literal("Search...").styled(style -> style.withColor(TextColor.fromRgb(placeholderRgb))));
    }

    private boolean actionSearchMatches(String label) {
        String query = actionSearchQuery == null ? "" : actionSearchQuery.trim();
        if (query.isEmpty()) {
            return true;
        }
        if (label == null) {
            return false;
        }
        return label.toLowerCase().contains(query.toLowerCase());
    }

    private void drawTopBox(DrawContext context) {
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, panelColor);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY - 1, borderColor);
        context.fill(topBoxX - 2, topBoxY + topBoxHeight + 1, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX - 1, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX + topBoxWidth + 1, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean verticalButtonBar = layoutPreset.isVerticalButtonBar();
        int numButtons = topBoxButtons.size();

        int dividerColor = clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF;
        if (!verticalButtonBar && numButtons >= 2) {
            int buttonWidth = TOPBOX_BUTTON_WIDTH;
            int totalButtonsWidth = numButtons * buttonWidth;
            int availableSpace = topBoxWidth - totalButtonsWidth;
            int spacing = availableSpace / (numButtons + 1);
            int startX = topBoxX + spacing;
            int lineY = topBoxY + 4;
            int lineHeight = topBoxHeight - 8;
            for (int i = 1; i < numButtons; i++) {
                int lineX = startX + buttonWidth * i + spacing * (i - 1) + spacing / 2;
                context.fill(lineX, lineY, lineX + 1, lineY + lineHeight, dividerColor);
            }
        } else if (verticalButtonBar && numButtons >= 2) {
            int availableSpace = topBoxHeight - (numButtons * TOPBOX_BUTTON_HEIGHT);
            int innerSpacing = availableSpace > 0 ? (availableSpace / (numButtons + 1)) : 2;
            innerSpacing = Math.max(2, innerSpacing);

            int startY = topBoxY + innerSpacing;
            int lineX1 = topBoxX + 4;
            int lineX2 = topBoxX + topBoxWidth - 4;
            for (int i = 1; i < numButtons; i++) {
                int lineY = startY + TOPBOX_BUTTON_HEIGHT * i + innerSpacing * (i - 1) + innerSpacing / 2;
                context.fill(lineX1, lineY, lineX2, lineY + 1, dividerColor);
            }
        }
    }
    
    private void drawLeftPanel(DrawContext context) {
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, panelColor);
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY - 1, borderColor);
        context.fill(leftPanelX - 2, leftPanelY + leftPanelHeight + 1, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX - 1, leftPanelY + leftPanelHeight + 2, borderColor);
        context.fill(leftPanelX + leftPanelWidth + 1, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);

        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        int nameY = 0;
        
        String ownerName = data != null && data.ownerName() != null ? data.ownerName() : null;

        int leftPanelNameColor = clientConfig != null ? clientConfig.getLeftPanelNameColor() : 0xFFFFFFFF;
        
        if (ownerName != null && !ownerName.isEmpty()) {
            int headX = leftPanelX + (leftPanelWidth - headSize) / 2;
            drawPlayerHead(context, headX, headY, headSize, ownerName);

            Text nameText = Text.literal(ownerName);
                int nameWidth = this.textRenderer.getWidth(nameText);
                int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
                nameY = headY + headSize + 4;
                context.drawText(this.textRenderer, nameText, nameX, nameY, leftPanelNameColor, false);
            } else {
            nameY = 0;
        }

        if (data == null) {
            return;
        }

        int infoY = nameY > 0 ? nameY + this.textRenderer.fontHeight + 8 : headY + headSize + 8;

        int dividerColor = clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF;
        context.fill(leftPanelX + padding, infoY - 4, leftPanelX + leftPanelWidth - padding, infoY - 3, dividerColor);
        infoY += 2;

        int leftPanelTextColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        int leftPanelValueColor = clientConfig != null ? clientConfig.getLeftPanelValueColor() : 0xFFFFFFFF;
        
        context.drawText(this.textRenderer, Text.literal("Status:"), leftPanelX + padding, infoY, leftPanelTextColor, false);
        String statusText = data.enabled() ? "active" : "inactive";
        int statusColor = data.enabled() ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) : 0xFF5555;
        int statusTextWidth = this.textRenderer.getWidth(statusText);
        context.drawText(this.textRenderer, Text.literal(statusText), 
            leftPanelX + leftPanelWidth - padding - statusTextWidth, infoY, statusColor | 0xFF000000, false);
        infoY += 12;

        context.drawText(this.textRenderer, Text.literal("Dim:"), 
            leftPanelX + padding, infoY, leftPanelTextColor, false);
        String dimName = data.dimensionKey().toLowerCase();
        String dimText;
        int dimColor;
        if (dimName.contains("overworld")) {
            dimText = "Overworld";
            dimColor = 0x55FF55;
        } else if (dimName.contains("nether")) {
            dimText = "Nether";
            dimColor = 0xFF5555;
        } else if (dimName.contains("end")) {
            dimText = "End";
            dimColor = 0xFF55FF;
        } else {
            dimText = "?";
            dimColor = 0xAAAAAA;
        }
        int dimTextWidth = this.textRenderer.getWidth(dimText);
        context.drawText(this.textRenderer, Text.literal(dimText), 
            leftPanelX + leftPanelWidth - padding - dimTextWidth, infoY, dimColor | 0xFF000000, false);
        infoY += 12;

        context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
        infoY += 12;

        Text chunkLabel = Text.literal("Chunk:");
        Text blockLabel = Text.literal("Block:");
        int chunkLabelWidth = this.textRenderer.getWidth(chunkLabel);
        int blockLabelWidth = this.textRenderer.getWidth(blockLabel);
        int maxLabelWidth = Math.max(chunkLabelWidth, blockLabelWidth);
        int coordStartX = leftPanelX + padding + maxLabelWidth + 4;
        
        int chunkBlockY = infoY - 3;
        context.drawText(this.textRenderer, chunkLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);
        
        String chunkXStr = "X:" + data.centerChunkX();
        String chunkZStr = "Z:" + data.centerChunkZ();
        
        context.drawText(this.textRenderer, Text.literal(chunkXStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawText(this.textRenderer, Text.literal(chunkZStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        
        context.drawText(this.textRenderer, blockLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);
        
        String xStr = "X:" + blockPos.getX();
        String yStr = "Y:" + data.blockY();
        String zStr = "Z:" + blockPos.getZ();
        
        context.drawText(this.textRenderer, Text.literal(xStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawText(this.textRenderer, Text.literal(yStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawText(this.textRenderer, Text.literal(zStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        int originalInfoY = chunkBlockY + 12 + 3;
        infoY = originalInfoY;

            context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
            int sdDividerY = infoY;
            infoY += 12;
        
            String radiusValue = String.valueOf(data.chunkRadius());
            String radiusSeparator = "/3";
        String radiusLabel = data.allowMobSpawning() ? "SD:" : "Radius:";
        int sdY = sdDividerY + 8;
        context.drawText(this.textRenderer, Text.literal(radiusLabel),
                leftPanelX + padding, sdY, leftPanelTextColor, false);
            context.drawText(this.textRenderer, Text.literal(radiusValue), 
                coordStartX, sdY, leftPanelValueColor, false);
            int radiusValueWidth = this.textRenderer.getWidth(radiusValue);
            context.drawText(this.textRenderer, Text.literal(radiusSeparator), 
                coordStartX + radiusValueWidth, sdY, leftPanelValueColor, false);
            infoY = sdY + 12;
    }

    private void drawSidePanel(DrawContext context) {
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, panelColor);
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY - 1, borderColor);
        context.fill(panelX - 2, panelY + panelHeight + 1, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);
        context.fill(panelX - 2, panelY - 2, panelX - 1, panelY + panelHeight + 2, borderColor);
        context.fill(panelX + panelWidth + 1, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);

        TextRenderer renderer = this.textRenderer;
        int padding = 8;
        int y = panelY + padding + 4;
        
        MutableText title = Text.literal("Chunkloader: " + data.displayName());
        context.drawText(renderer, title.formatted(net.minecraft.util.Formatting.BOLD), panelX + padding, y, 0xFFFFFF, false);
        y += 16;
        
        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawText(renderer, Text.literal("Status:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Text status = data.enabled()
            ? Text.literal("active")
            : Text.literal("inactive");
        int statusColor = data.enabled() 
            ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) 
            : 0xFF5555;
        context.drawText(renderer, status, panelX + padding + 50, y, statusColor, false);
        y += 12;

        context.drawText(renderer, Text.literal("Mode:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Text mode = data.allowMobSpawning()
            ? Text.literal("Fakeplayer (mob spawning)")
            : Text.literal("Chunkplayer");
        int modeColor = data.allowMobSpawning() ? 0x55FF55 : 0x79C0FF;
        context.drawText(renderer, mode, panelX + padding + 50, y, modeColor, false);
        y += 12;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawText(renderer, Text.literal("Dimension:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        context.drawText(renderer, Text.literal(data.dimensionKey()), panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        context.drawText(renderer, Text.literal("Position:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        context.drawText(renderer, Text.literal(blockPos.getX() + " / " + data.blockY() + " / " + blockPos.getZ()), 
            panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        String radiusLabelSide = data.allowMobSpawning() ? "SD:" : "Radius:";
        context.drawText(renderer, Text.literal(radiusLabelSide).formatted(net.minecraft.util.Formatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        MutableText radiusText = Text.literal(data.chunkRadius() + " / 3");
        context.drawText(renderer, radiusText, panelX + padding + 50, y, 0xFFFFFF, false);
            y += 12;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        if (!data.allowMobSpawning()) {
        int legendStartY = y;
        int buttonAreaStart = panelY + panelHeight - 80;
        int legendHeight = 12 + 14 + 14 + 14;
        
        if (legendStartY + legendHeight > buttonAreaStart - 10) {
            return;
        }
        
            context.drawText(renderer, Text.literal("Legend").formatted(net.minecraft.util.Formatting.BOLD), 
            panelX + padding, y, 0xFFFFFF, false);
        y += 12;
        
        int loadedColor = data.allowMobSpawning() 
            ? ChunkMapGrid.COLOR_LOADED 
            : ChunkMapGrid.COLOR_IN_RANGE;
        drawLegendItem(context, renderer, panelX + padding, y, loadedColor, 
                Text.literal("Loaded"));
        y += 14;
        
        if (data.allowMobSpawning()) {
            drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_LOADED, 
                    Text.literal("Simulation Distance"));
            y += 14;
        }
        
        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_IN_RANGE, 
                Text.literal("Within radius"));
        y += 14;
        
        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_OTHER, 
                Text.literal("Other chunkloader"));
        }
    }

    private Identifier getPlayerSkinTexture(String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || playerName == null || playerName.isEmpty()) {
            return FALLBACK_SKIN;
        }
        
        Identifier entryTexture = resolveSkinFromPlayerListEntry(client, playerName);
        if (entryTexture != null && !entryTexture.equals(FALLBACK_SKIN)) {
            return entryTexture;
        }
        
        return FALLBACK_SKIN;
    }

    private Identifier resolveSkinFromPlayerListEntry(MinecraftClient client, String playerName) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return null;
        }
        
        PlayerListEntry entry = null;
        for (var playerEntry : handler.getPlayerList()) {
            if (playerEntry.getProfile().name() != null && playerEntry.getProfile().name().equals(playerName)) {
                entry = playerEntry;
                break;
            }
        }
        
        if (entry == null) {
            return null;
        }
        
        try {
            var field45607 = entry.getClass().getDeclaredField("field_45607");
            field45607.setAccessible(true);
            Object supplier = field45607.get(entry);
            if (supplier instanceof java.util.function.Supplier) {
                Object result = ((java.util.function.Supplier<?>) supplier).get();
                if (result != null) {
                    try {
                        var comp1626 = result.getClass().getMethod("comp_1626");
                        Object textureResult = comp1626.invoke(result);
                        if (textureResult != null) {
                            var textureMethods = textureResult.getClass().getMethods();
                            for (var textureMethod : textureMethods) {
                                if (textureMethod.getParameterCount() == 0 && !textureMethod.getName().equals("getClass")) {
                                    try {
                                        Object idResult = textureMethod.invoke(textureResult);
                                        if (idResult instanceof Identifier id && id != null && !id.equals(FALLBACK_SKIN)) {
                return id;
            }
        } catch (Exception ignored) {
        }
                                }
                }
            }
        } catch (Exception ignored) {
        }
                }
            }
        } catch (Exception ignored) {
        }
        
        return null;
    }

    private void drawPlayerHead(DrawContext context, int x, int y, int size, String playerName) {
        Identifier texture = getPlayerSkinTexture(playerName);

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            8,
            8,
            size,
            size,
            8,
            8,
            64,
            64
        );

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            40,
            8,
            size,
            size,
            8,
            8,
            64,
            64
        );
    }
    
    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x4A4A4A);
    }

    private void drawLegendItem(DrawContext context, TextRenderer renderer, int x, int y, int color, Text text) {
        int squareSize = 6;
        int squareY = y + (renderer.fontHeight - squareSize) / 2;
        context.fill(x, squareY, x + squareSize, squareY + squareSize, 0xFF000000 | color);
        context.fill(x + 1, squareY + 1, x + squareSize - 1, squareY + squareSize - 1, color);
        context.drawText(renderer, text, x + 10, y, 0xFFFFFF, false);
    }
    
    private void drawChunkloaderPoints(DrawContext context, int mouseX, int mouseY) {
        int cellSize = CELL_SIZE;
        int gridInnerLeft = gridLeft + 1;
        int gridInnerTop = gridTop + 1;
        
        int ownOffsetX = data.fakeplayerChunkX() - data.centerChunkX();
        int ownOffsetZ = data.fakeplayerChunkZ() - data.centerChunkZ();
        int halfMap = (data.mapWidth() - 1) / 2;
        int ownColumn = ownOffsetX + halfMap;
        int ownRow = ownOffsetZ + halfMap;
        
        if (ownColumn >= 0 && ownColumn < data.mapWidth() && ownRow >= 0 && ownRow < data.mapHeight()) {
            int ownInnerChunkX = ((data.fakeplayerBlockX() % 16) + 16) % 16;
            int ownInnerChunkZ = ((data.fakeplayerBlockZ() % 16) + 16) % 16;
            
            int ownCellLeft = gridInnerLeft + ownColumn * cellSize;
            int ownCellTop = gridInnerTop + ownRow * cellSize;
            float ownPosInCellX = (ownInnerChunkX / 16.0f) * cellSize;
            float ownPosInCellZ = (ownInnerChunkZ / 16.0f) * cellSize;
            
            int ownPointX = ownCellLeft + (int)ownPosInCellX;
            int ownPointY = ownCellTop + (int)ownPosInCellZ;
            
            int ownPointColor = 0xFFFF0000;
            int ownPointSize = 3;
            int ownPointHalfSize = ownPointSize / 2;
            
            context.fill(
                ownPointX - ownPointHalfSize - 1, ownPointY - ownPointHalfSize - 1,
                ownPointX + ownPointHalfSize + 1, ownPointY + ownPointHalfSize + 1,
                0xFF000000
            );
            context.fill(
                ownPointX - ownPointHalfSize, ownPointY - ownPointHalfSize,
                ownPointX + ownPointHalfSize, ownPointY + ownPointHalfSize,
                ownPointColor
            );
            
            if (mouseX >= ownPointX - ownPointHalfSize - 2 && mouseX < ownPointX + ownPointHalfSize + 2 &&
                mouseY >= ownPointY - ownPointHalfSize - 2 && mouseY < ownPointY + ownPointHalfSize + 2) {
                String ownTooltipText = data.fakeplayerName() != null ? data.fakeplayerName() : 
                    String.format("%s at (%d, %d)", data.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer", 
                        data.fakeplayerChunkX(), data.fakeplayerChunkZ());
                context.drawTooltip(textRenderer, Text.literal(ownTooltipText), mouseX, mouseY);
            }
        }
        
        if (data.otherChunkloaders() == null || data.otherChunkloaders().isEmpty()) {
            return;
        }
        
        if (data.hideOtherDots()) {
            return;
        }
        
        for (de.chunkloader.network.ChunkloaderPosition pos : data.otherChunkloaders()) {
            int offsetX = pos.chunkX() - data.centerChunkX();
            int offsetZ = pos.chunkZ() - data.centerChunkZ();
            
            int column = offsetX + halfMap;
            int row = offsetZ + halfMap;
            
            if (column < 0 || column >= data.mapWidth() || row < 0 || row >= data.mapHeight()) {
                continue;
            }
            
            int innerChunkX = ((pos.blockX() % 16) + 16) % 16;
            int innerChunkZ = ((pos.blockZ() % 16) + 16) % 16;
            
            int cellLeft = gridInnerLeft + column * cellSize;
            int cellTop = gridInnerTop + row * cellSize;
            float posInCellX = (innerChunkX / 16.0f) * cellSize;
            float posInCellZ = (innerChunkZ / 16.0f) * cellSize;
            
            int pointX = cellLeft + (int)posInCellX;
            int pointY = cellTop + (int)posInCellZ;
            
            int pointColor;
            if (pos.isFakeplayer()) {
                pointColor = 0xFF55FF55;
            } else {
                pointColor = 0xFF5996FF;
            }
            int pointSize = 3;
            int pointHalfSize = pointSize / 2;
            
            context.fill(
                pointX - pointHalfSize - 1, pointY - pointHalfSize - 1,
                pointX + pointHalfSize + 1, pointY + pointHalfSize + 1,
                0xFF000000
            );
            context.fill(
                pointX - pointHalfSize, pointY - pointHalfSize,
                pointX + pointHalfSize, pointY + pointHalfSize,
                pointColor
            );
            
            if (mouseX >= pointX - pointHalfSize - 2 && mouseX < pointX + pointHalfSize + 2 &&
                mouseY >= pointY - pointHalfSize - 2 && mouseY < pointY + pointHalfSize + 2) {
                String tooltipText = pos.name() != null ? pos.name() : 
                    String.format("%s at (%d, %d)", pos.isFakeplayer() ? "Fakeplayer" : "Chunkplayer", pos.chunkX(), pos.chunkZ());
                context.drawTooltip(textRenderer, Text.literal(tooltipText), mouseX, mouseY);
            }
        }
    }

    private static class ChunkMapGrid implements AutoCloseable {

        private static final int COLOR_BACKGROUND = 0xAA000000;
        private static final int COLOR_BORDER = 0xFF3A3A3A;
        static final int COLOR_LOADED = 0x55FF55;
        static final int COLOR_IN_RANGE = 0x5996FF;
        static final int COLOR_OTHER = 0xC8C8C8;

        private static final int BORDER = 1;

        private final List<Cell> cells = new ArrayList<>();
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private final int gridInnerLeft;
        private final int gridInnerTop;
        private final int gridInnerRight;
        private final int gridInnerBottom;

        ChunkMapGrid(ClientWorld world, ChunkMapData data, int left, int top, int cellSize) {
            this.left = left;
            this.top = top;
            this.width = data.mapWidth() * cellSize + 2 * BORDER;
            this.height = data.mapHeight() * cellSize + 2 * BORDER;
            int sampleY = data.blockY();

            this.gridInnerLeft = left + BORDER;
            this.gridInnerTop = top + BORDER;
            this.gridInnerRight = gridInnerLeft + data.mapWidth() * cellSize;
            this.gridInnerBottom = gridInnerTop + data.mapHeight() * cellSize;

            Map<Long, ChunkMapCell> stateByOffset = new HashMap<>();
            for (ChunkMapCell cell : data.cells()) {
                stateByOffset.put(pack(cell.offsetX(), cell.offsetZ()), cell);
            }

            for (int row = 0; row < data.mapHeight(); row++) {
                for (int column = 0; column < data.mapWidth(); column++) {
                    int chunkX = data.topLeftChunkX() + column;
                    int chunkZ = data.topLeftChunkZ() + row;
                    int offsetX = chunkX - data.centerChunkX();
                    int offsetZ = chunkZ - data.centerChunkZ();
                    ChunkMapCell state = stateByOffset.getOrDefault(
                        pack(offsetX, offsetZ),
                        new ChunkMapCell(
                            offsetX,
                            offsetZ,
                            Math.abs(offsetX) <= data.chunkRadius() && Math.abs(offsetZ) <= data.chunkRadius() && data.enabled(),
                            Math.abs(offsetX) <= data.chunkRadius() && Math.abs(offsetZ) <= data.chunkRadius(),
                            false,
                            false,
                            null
                        )
                    );
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    int cellLeft = gridInnerLeft + column * cellSize;
                    int cellTop = gridInnerTop + row * cellSize;
                    this.cells.add(new Cell(
                        world,
                        chunkPos,
                        state,
                        cellLeft,
                        cellTop,
                        cellSize,
                        sampleY,
                        data,
                        gridInnerLeft,
                        gridInnerTop,
                        gridInnerRight,
                        gridInnerBottom
                    ));
                }
            }
        }

        void render(DrawContext context, int mouseX, int mouseY) {
            context.fill(left, top, left + width, top + height, COLOR_BACKGROUND);

            context.fill(left, top, left + width, top + BORDER, COLOR_BORDER);
            context.fill(left, top + height - BORDER, left + width, top + height, COLOR_BORDER);
            context.fill(left, top, left + BORDER, top + height, COLOR_BORDER);
            context.fill(left + width - BORDER, top, left + width, top + height, COLOR_BORDER);

            int scissorLeft = Math.max(left, gridInnerLeft);
            int scissorTop = Math.max(top, gridInnerTop);
            int scissorRight = Math.min(left + width, gridInnerRight);
            int scissorBottom = Math.min(top + height, gridInnerBottom);
            
            for (Cell cell : cells) {
                if (cell.left + cell.size >= scissorLeft && cell.left <= scissorRight &&
                    cell.top + cell.size >= scissorTop && cell.top <= scissorBottom) {
                    cell.render(context, mouseX, mouseY);
                }
            }
        }

        Cell getHoveredCell(int mouseX, int mouseY) {
            for (Cell cell : cells) {
                if (cell.isHovered(mouseX, mouseY)) {
                    return cell;
                }
            }
            return null;
        }

        @Override
        public void close() {
            for (Cell cell : cells) {
                cell.close();
            }
            cells.clear();
        }

        static long pack(int offsetX, int offsetZ) {
            return ((long)offsetX << 32) | (offsetZ & 0xFFFFFFFFL);
        }

        static class Cell implements AutoCloseable {

            private static final int COLOR_OCCUPIED = 0x88404040;
            private static final int COLOR_LOADED_OVERLAY = 0x2687FF59;
            private static final int COLOR_LOADED_OVERLAY_CHUNKPLAYER = 0x665555FF;
            private static final int COLOR_SIMULATION_DISTANCE = 0x2687FF59;
            private static final int COLOR_RANGE_OVERLAY = 0x1A3D7FFF;
            private static final int COLOR_RANGE_OVERLAY_DISABLED = 0x26FF5555;
            private final ChunkPos chunkPos;
            private final ChunkMapCell state;
            private final int left;
            private final int top;
            private final int size;
            private final int baseColor;
            private final ChunkTileImage tileImage;
            private final ChunkMapData data;
            private final int gridLeft;
            private final int gridTop;
            private final int gridRight;
            private final int gridBottom;

            Cell(ClientWorld world, ChunkPos chunkPos, ChunkMapCell state, int left, int top, int size, int sampleY, ChunkMapData data, int gridLeft, int gridTop, int gridRight, int gridBottom) {
                this.chunkPos = chunkPos;
                this.state = state;
                this.left = left;
                this.top = top;
                this.size = size;
                this.baseColor = ((chunkPos.x + chunkPos.z) & 1) == 0 ? 0xFF1F1F1F : 0xFF242424;
                this.tileImage = world != null ? new ChunkTileImage(world, chunkPos, sampleY) : null;
                this.data = data;
                this.gridLeft = gridLeft;
                this.gridTop = gridTop;
                this.gridRight = gridRight;
                this.gridBottom = gridBottom;
            }

            void render(DrawContext context, int mouseX, int mouseY) {
                int innerLeft = Math.max(gridLeft, left);
                int innerTop = Math.max(gridTop, top);
                int innerRight = Math.min(gridRight, left + size);
                int innerBottom = Math.min(gridBottom, top + size);

                if (innerRight <= innerLeft || innerBottom <= innerTop) {
                    return;
                }

                boolean isSimulatedOnly = state.simulatedByFakeplayer() && !state.loaded() && !state.occupiedByOther();
                
                if (!isSimulatedOnly) {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, 0x33000000);
                }

                Identifier textureId = null;
                if (tileImage != null && innerRight > innerLeft && innerBottom > innerTop) {
                    try {
                        textureId = tileImage.getTextureId();
                    } catch (Exception e) {
                        textureId = null;
                    }
                }
                int textureWidth = innerRight - innerLeft;
                int textureHeight = innerBottom - innerTop;

                if (textureId != null && textureWidth > 0 && textureHeight > 0) {
                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        innerLeft,
                        innerTop,
                        0.0f,
                        0.0f,
                        textureWidth,
                        textureHeight,
                        16,
                        16,
                        16,
                        16
                    );
                } else {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, baseColor);
                }

                int overlayWidth = innerRight - innerLeft;
                int overlayHeight = innerBottom - innerTop;
                if (overlayWidth > 0 && overlayHeight > 0) {
                    int srcX = innerLeft - left;
                    int srcY = innerTop - top;
                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        GRID_OVERLAY,
                        innerLeft,
                        innerTop,
                        srcX,
                        srcY,
                        overlayWidth,
                        overlayHeight,
                        18,
                        18
                    );
                }

                if (!data.allowMobSpawning()) {
                if (state.withinRange() && !state.loaded()) {
                        int overlayColor = data.enabled() ? COLOR_RANGE_OVERLAY : COLOR_RANGE_OVERLAY_DISABLED;
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, overlayColor);
                }
                if (state.loaded() && !state.occupiedByOther()) {
                        int loadedColor = data.allowMobSpawning() 
                            ? COLOR_LOADED_OVERLAY 
                            : COLOR_LOADED_OVERLAY_CHUNKPLAYER;
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, loadedColor);
                    }
                }
                
                if (state.simulatedByFakeplayer()) {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_SIMULATION_DISTANCE);
                }
                
                if (state.occupiedByOther()) {
                    if (data.allowMobSpawning()) {
                        if (!state.simulatedByFakeplayer()) {
                            context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                        }
                    } else {
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                    }
                }

                if (isHovered(mouseX, mouseY)) {
                    int hoverWidth = innerRight - innerLeft;
                    int hoverHeight = innerBottom - innerTop;
                    if (hoverWidth > 0 && hoverHeight > 0) {
                        int srcX = innerLeft - left;
                        int srcY = innerTop - top;
                        context.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            HOVER_OVERLAY,
                            innerLeft,
                            innerTop,
                            srcX,
                            srcY,
                            hoverWidth,
                            hoverHeight,
                            18,
                            18
                        );
                    }
                }
            }

            boolean isHovered(int mouseX, int mouseY) {
                int innerLeft = Math.max(gridLeft, left);
                int innerRight = Math.min(gridRight, left + size);
                int innerTop = Math.max(gridTop, top);
                int innerBottom = Math.min(gridBottom, top + size);

                return mouseX >= innerLeft && mouseX < innerRight &&
                       mouseY >= innerTop && mouseY < innerBottom;
            }

            List<Text> buildTooltip() {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(Text.literal("Chunk X:" + chunkPos.x + " Z:" + chunkPos.z));
                
                if (state.loaded()) {
                    tooltip.add(Text.literal("Loaded by this chunkloader"));
                } else if (state.withinRange()) {
                    if (!data.allowMobSpawning()) {
                        tooltip.add(Text.literal("Inside radius (enable to load)"));
                    } else {
                        tooltip.add(Text.literal("Inside simulation distance (enable to load)"));
                    }
                } else {
                    tooltip.add(Text.literal("Outside of this chunkloader"));
                }
                
                if (state.occupiedByOther() && state.loaded() && !state.simulatedByFakeplayer()) {
                    tooltip.add(Text.literal("Loaded by a different chunkloader"));
                }
                
                return ImmutableList.copyOf(tooltip);
            }

            @Override
            public void close() {
                if (tileImage != null) {
                    tileImage.close();
                }
            }
        }
    }

    private void buildTopBoxButtons() {
        topBoxButtons.forEach(this::remove);
        topBoxButtons.clear();

        if (clientConfig == null) {
            clientConfig = ClientConfig.load();
        }

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean verticalButtonBar = layoutPreset.isVerticalButtonBar();

        String uiLabel = switch (layoutPreset) {
            case TOP -> "UI";
            case LEFT -> "UI 2";
            case RIGHT -> "UI 3";
            case BOTTOM -> "UI 4";
            case TOP_SWAP -> "UI 5";
            case LEFT_SWAP -> "UI 6";
            case RIGHT_SWAP -> "UI 7";
            case BOTTOM_SWAP -> "UI 8";
        };

        int buttonWidth = TOPBOX_BUTTON_WIDTH;
        int buttonHeight = TOPBOX_BUTTON_HEIGHT;
        int numButtons = TOPBOX_BUTTON_COUNT;
        int spacing;
        int startX;
        int startY;

        if (!verticalButtonBar) {
            int totalButtonsWidth = numButtons * buttonWidth;
            int availableSpace = topBoxWidth - totalButtonsWidth;
            spacing = availableSpace / (numButtons + 1);
            startX = topBoxX + spacing;
            startY = topBoxY + (topBoxHeight - buttonHeight) / 2;
        } else {
            int availableSpace = topBoxHeight - (numButtons * buttonHeight);
            spacing = availableSpace > 0 ? (availableSpace / (numButtons + 1)) : 2;
            spacing = Math.max(2, spacing);
            startX = topBoxX + (topBoxWidth - buttonWidth) / 2;
            startY = topBoxY + spacing;
        }
        
        infoButton = ButtonWidget.builder(
            Text.literal("Info"),
            btn -> {
                MinecraftClient.getInstance().setScreen(new ChunkloaderMenuScreen(this));
            })
            .dimensions(startX, startY, buttonWidth, buttonHeight)
            .build();
        infoButton.setMessage(Text.literal("Info").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(infoButton);
        this.addDrawableChild(infoButton);

        int helpButtonX = verticalButtonBar ? startX : (startX + buttonWidth + spacing);
        int helpButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 1) : startY;
        
        ButtonWidget helpButton = ButtonWidget.builder(
            Text.literal("Help"),
            btn -> {
                MinecraftClient.getInstance().setScreen(new ChunkMapHelpScreen(this));
            })
            .dimensions(helpButtonX, helpButtonY, buttonWidth, buttonHeight)
            .build();
        helpButton.setMessage(Text.literal("Help").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(helpButton);
        this.addDrawableChild(helpButton);

        int listButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 2);
        int listButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 2) : startY;

        ButtonWidget listButton = ButtonWidget.builder(
            Text.literal("List"),
            btn -> {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            })
            .dimensions(listButtonX, listButtonY, buttonWidth, buttonHeight)
            .build();
        listButton.setMessage(Text.literal("List").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(listButton);
        this.addDrawableChild(listButton);

        int layoutButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 3);
        int layoutButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 3) : startY;
        ButtonWidget uiButton = ButtonWidget.builder(
            Text.literal(uiLabel),
            btn -> cycleLayoutPreset())
            .dimensions(layoutButtonX, layoutButtonY, buttonWidth, buttonHeight)
            .build();
        uiButton.setMessage(Text.literal(uiLabel).formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(uiButton);
        this.addDrawableChild(uiButton);

        int deleteButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 4);
        int deleteButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 4) : startY;
        
        ButtonWidget deleteButton = ButtonWidget.builder(
            Text.literal("Delete"),
            btn -> {
                this.client.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Text.literal("Delete Chunkloader?").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD),
                    Text.literal("This will permanently delete this chunkloader.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                            ChunkloaderActionPayload.Action.DELETE,
                            data.fakeplayerChunkX(),
                            data.fakeplayerChunkZ(),
                            0
                        );
                        this.client.setScreen(null);
                    },
                    null
                ));
            })
            .dimensions(deleteButtonX, deleteButtonY, buttonWidth, buttonHeight)
            .build();
        deleteButton.setMessage(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED));
        topBoxButtons.add(deleteButton);
        this.addDrawableChild(deleteButton);
    }

    private void buildActionButtons() {
        actionButtons.forEach(this::remove);
        actionButtons.clear();
        actionButtonYOffset.clear();
        buttonOriginalActiveState.clear();
        actionHeaderLayouts.clear();
        actionContentHeight = 0;
        if (panelWidth <= 0) {
            return;
        }
        int buttonWidth = panelWidth - 16;
        int buttonX = panelX + 8;

        int cursorY = 0;
        int gap = ACTION_ROW_GAP;
        int headerHeight = this.textRenderer.fontHeight + 4;

        boolean generalHeaderMatches = actionSearchMatches("General");
        String enableLabelRaw = data.enabled()
            ? (data.allowMobSpawning() ? "Disable Fakeplayer" : "Disable Chunkplayer")
            : (data.allowMobSpawning() ? "Enable Fakeplayer" : "Enable Chunkplayer");
        boolean enableButtonMatches = actionSearchMatches(enableLabelRaw);
        boolean showEnableButton = generalHeaderMatches || enableButtonMatches;

        if (showEnableButton) {
        ButtonWidget enableButton = ButtonWidget.builder(
            Text.literal(enableLabelRaw),
            btn -> {
                ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_ENABLED,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
                );
                if (data.enabled()) {
                    MinecraftClient.getInstance().setScreen(null);
                }
            })
            .dimensions(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.enabled()) {
            enableButton.setMessage((data.allowMobSpawning() ? Text.literal("Disable Fakeplayer") : Text.literal("Disable Chunkplayer")).formatted(net.minecraft.util.Formatting.RED));
        } else {
            enableButton.setMessage((data.allowMobSpawning() ? Text.literal("Enable Fakeplayer") : Text.literal("Enable Chunkplayer")).formatted(net.minecraft.util.Formatting.GREEN));
        }
        actionButtons.add(enableButton);
        actionButtonYOffset.put(enableButton, cursorY);
        this.addDrawableChild(enableButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean modeHeaderMatches = actionSearchMatches("Mode");
        String mobLabelRaw = data.allowMobSpawning() ? "Disable mob spawning" : "Enable mob spawning";
        boolean mobButtonMatches = actionSearchMatches(mobLabelRaw);

        int radiusY = 0;
        int halfWidth = (buttonWidth - 4) / 2;
        boolean isFakePlayer = data.allowMobSpawning();
        boolean canDecrease = data.chunkRadius() > 0;
        boolean canIncrease = data.canIncreaseRadius();

        String radiusHeaderRaw = isFakePlayer ? "Simulation distance" : "Radius";
        boolean radiusHeaderMatches = actionSearchMatches(radiusHeaderRaw);
        String radiusDownLabel = isFakePlayer ? "SD -1" : "Radius -1";
        String radiusUpLabel = isFakePlayer ? "SD +1" : "Radius +1";
        boolean radiusDownMatches = actionSearchMatches(radiusDownLabel);
        boolean radiusUpMatches = actionSearchMatches(radiusUpLabel);
        boolean showRadiusSection = radiusHeaderMatches || radiusDownMatches || radiusUpMatches;

        boolean showModeHeader = modeHeaderMatches || mobButtonMatches || showRadiusSection;
        if (showModeHeader) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Text.literal("Mode").formatted(Formatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        if (modeHeaderMatches || mobButtonMatches) {
        ButtonWidget mobButton = ButtonWidget.builder(
            Text.literal(mobLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_MOB_SPAWNING,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.allowMobSpawning()) {
            mobButton.setMessage(Text.literal("Disable mob spawning").formatted(net.minecraft.util.Formatting.BLUE));
        } else {
            mobButton.setMessage(Text.literal("Enable mob spawning").formatted(net.minecraft.util.Formatting.GREEN));
        }
        actionButtons.add(mobButton);
        actionButtonYOffset.put(mobButton, cursorY);
        this.addDrawableChild(mobButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean showRadiusButtons = modeHeaderMatches || showRadiusSection;
        if (showRadiusButtons) {
        ButtonWidget radiusDown = ButtonWidget.builder(
            Text.literal(radiusDownLabel),
            btn -> {
                if (canDecrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_DECREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                        1
                    );
                }
            })
            .dimensions(buttonX, radiusY, halfWidth, 20)
            .build();
        if (!canDecrease) {
            radiusDown.active = false;
            radiusDown.setMessage(Text.literal(radiusDownLabel).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        actionButtons.add(radiusDown);
        actionButtonYOffset.put(radiusDown, cursorY);
        this.addDrawableChild(radiusDown);

        ButtonWidget radiusUp = ButtonWidget.builder(
            Text.literal(radiusUpLabel),
            btn -> {
                if (canIncrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_INCREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                        1
                    );
                }
            })
            .dimensions(buttonX + halfWidth + 4, radiusY, halfWidth, 20)
            .build();
        if (!canIncrease) {
            radiusUp.active = false;
            radiusUp.setMessage(Text.literal(radiusUpLabel).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        actionButtons.add(radiusUp);
        actionButtonYOffset.put(radiusUp, cursorY);
        this.addDrawableChild(radiusUp);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean renameButtonMatches = actionSearchMatches("Rename");

        boolean hideHeaderMatches = actionSearchMatches("Hide options");
        String hideNameLabelRaw = data.nameVisible() ? "Hide name" : "Show name";
        String hideDotsLabelRaw = data.hideOtherDots() ? "Show other dots" : "Hide other dots";
        boolean hideNameMatches = actionSearchMatches(hideNameLabelRaw);
        boolean hideDotsMatches = actionSearchMatches(hideDotsLabelRaw);
        boolean showHideSection = hideHeaderMatches || hideNameMatches || hideDotsMatches;

        if (showHideSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Text.literal("Hide options").formatted(Formatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        int nameVisibleY = 0;
        if (showHideSection) {
        ButtonWidget nameVisibleButton = ButtonWidget.builder(
            Text.literal(hideNameLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_NAME_VISIBLE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, nameVisibleY, buttonWidth, 20)
            .build();
        if (data.nameVisible()) {
            nameVisibleButton.setMessage(Text.literal("Hide name").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            nameVisibleButton.setMessage(Text.literal("Show name").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(nameVisibleButton);
        actionButtonYOffset.put(nameVisibleButton, cursorY);
        this.addDrawableChild(nameVisibleButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int hideOtherDotsY = 0;
        ButtonWidget hideOtherDotsButton = ButtonWidget.builder(
            Text.literal(hideDotsLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_HIDE_OTHER_DOTS,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, hideOtherDotsY, buttonWidth, 20)
            .build();
        if (data.hideOtherDots()) {
            hideOtherDotsButton.setMessage(Text.literal("Show other dots").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            hideOtherDotsButton.setMessage(Text.literal("Hide other dots").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(hideOtherDotsButton);
        actionButtonYOffset.put(hideOtherDotsButton, cursorY);
        this.addDrawableChild(hideOtherDotsButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean visHeaderMatches = actionSearchMatches("Visualization");
        String visLabelRaw = data.visualizeActive() ? "Disable visualization" : "Enable visualization";
        String vis3DLabelRaw = data.visualize3DActive() ? "Disable 3D visualization" : "Enable 3D visualization";
        boolean visButtonMatches = actionSearchMatches(visLabelRaw);
        boolean vis3DButtonMatches = actionSearchMatches(vis3DLabelRaw);
        boolean showVisSection = visHeaderMatches || visButtonMatches || vis3DButtonMatches;

        if (showVisSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Text.literal("Visualization").formatted(Formatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        int visualizeY = 0;
        if (showVisSection) {
        ButtonWidget visualizeButton = ButtonWidget.builder(
            Text.literal(visLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, visualizeY, buttonWidth, 20)
            .build();
        if (data.visualizeActive()) {
            visualizeButton.setMessage(Text.literal("Disable visualization").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            visualizeButton.setMessage(Text.literal("Enable visualization").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(visualizeButton);
        actionButtonYOffset.put(visualizeButton, cursorY);
        this.addDrawableChild(visualizeButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int visualize3DY = 0;
        ButtonWidget visualize3DButton = ButtonWidget.builder(
            Text.literal(vis3DLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE3D,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, visualize3DY, buttonWidth, 20)
            .build();
        if (data.visualize3DActive()) {
            visualize3DButton.setMessage(Text.literal("Disable 3D visualization").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            visualize3DButton.setMessage(Text.literal("Enable 3D visualization").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(visualize3DButton);
        actionButtonYOffset.put(visualize3DButton, cursorY);
        this.addDrawableChild(visualize3DButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean settingsHeaderMatches = actionSearchMatches("Settings");
        boolean panelColorMatches = actionSearchMatches("Panel color");
        boolean keybindMatches = actionSearchMatches("Keybinds");
        boolean showSettingsSection = settingsHeaderMatches || panelColorMatches || keybindMatches || renameButtonMatches;

        if (showSettingsSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Text.literal("Settings").formatted(Formatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        int renameY = 0;
        if (showSettingsSection) {
        ButtonWidget renameButton = ButtonWidget.builder(
            Text.literal("Rename"),
            btn -> {
                this.client.setScreen(new RenameChunkloaderScreen(
                    this,
                    data.fakeplayerChunkX(),
                    data.fakeplayerChunkZ(),
                    data.displayName()
                ));
            })
            .dimensions(buttonX, renameY, buttonWidth, 20)
            .build();
        renameButton.setMessage(Text.literal("Rename").formatted(net.minecraft.util.Formatting.WHITE));
        actionButtons.add(renameButton);
        actionButtonYOffset.put(renameButton, cursorY);
        this.addDrawableChild(renameButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int panelColorY = 0;
        ButtonWidget panelColorButton = ButtonWidget.builder(
            Text.literal("Panel color"),
            btn -> {
                if (clientConfig == null) {
                    clientConfig = ClientConfig.load();
                }
                this.client.setScreen(new PanelColorScreen(this, clientConfig));
            })
            .dimensions(buttonX, panelColorY, buttonWidth, 20)
            .build();
        panelColorButton.setMessage(Text.literal("Panel color").formatted(net.minecraft.util.Formatting.WHITE));
        actionButtons.add(panelColorButton);
        actionButtonYOffset.put(panelColorButton, cursorY);
        this.addDrawableChild(panelColorButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        
        int keybindY = 0;
        ButtonWidget keybindButton = ButtonWidget.builder(
            Text.literal("Keybinds"),
            btn -> {
                this.client.setScreen(new KeybindConfigScreen(this));
            })
            .dimensions(buttonX, keybindY, buttonWidth, 20)
            .build();
        keybindButton.setMessage(Text.literal("Keybinds").formatted(net.minecraft.util.Formatting.WHITE));
        actionButtons.add(keybindButton);
        actionButtonYOffset.put(keybindButton, cursorY);
        this.addDrawableChild(keybindButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }
        
        int resetY = footerRowY;
        resetButton = ButtonWidget.builder(
            Text.literal("Reset to defaults"),
            btn -> {
                this.client.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Text.literal("Reset to Defaults?").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD),
                    Text.literal("This will reset all settings to default values.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.RESET_TO_DEFAULTS,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            0
                        );
                        if (clientConfig != null) {
                            clientConfig.resetToDefaults();
                        }
                    },
                    null
                ));
            })
            .dimensions(buttonX, resetY, buttonWidth, 20)
            .build();
        resetButton.setMessage(Text.literal("Reset to defaults").formatted(net.minecraft.util.Formatting.WHITE));
        this.addDrawableChild(resetButton);

        actionContentHeight = Math.max(0, cursorY - gap);

        buttonsNeedUpdate = true;
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

