package de.chunkloader.client.screen;

import com.google.common.collect.ImmutableList;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class ChunkMapScreen extends Screen {

    private static de.chunkloader.client.config.@Nullable ClientConfig clientConfig = null;

    private static final int CELL_SIZE = 18;
    private static final int ACTION_ROW_GAP = 2;
    private static final int ACTION_SEARCH_HEIGHT = 18;
    private static final int ACTION_SEARCH_GAP = 0;
    private static final int ACTION_SEARCH_Y_OFFSET = -6;
    private static final int ACTION_SEARCH_LIST_GAP = 3;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final ResourceLocation GRID_OVERLAY = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "textures/gui/grid_overlay.png");
    private static final ResourceLocation HOVER_OVERLAY = ResourceLocation.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "textures/gui/cell_overlay.png");
    private static final ResourceLocation FALLBACK_SKIN = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    private ChunkMapData data;
    private @Nullable ChunkMapGrid grid;
    private @Nullable ChunkMapTexture mapTexture;
    private @Nullable Boolean previousAllowMobSpawning = null;
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
    private final List<Button> topBoxButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private final Map<Button, Integer> actionButtonYOffset = new HashMap<>();
    private final Map<Button, Boolean> buttonOriginalActiveState = new HashMap<>();
    private final List<ActionHeaderLayout> actionHeaderLayouts = new ArrayList<>();
    private int actionContentHeight = 0;
    private @Nullable EditBox actionSearchField;
    private String actionSearchQuery = "";
    private @Nullable Button resetButton;
    private @Nullable Button infoButton;
    private @Nullable Button closeButton;
    private int footerRowY;
    private int actionScrollOffset = 0;
    private int actionViewportLeft;
    private int actionViewportRight;
    private int actionViewportTop;
    private int actionViewportBottom;

    private boolean buttonsNeedUpdate = true;
    private int lastScrollOffset = -1;

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
        super(Component.literal("Chunk Loader Map"));
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
            Level level = Minecraft.getInstance().level;
            this.grid.close();
            this.grid = new ChunkMapGrid(level, data, gridLeft, gridTop, CELL_SIZE);
            if (this.mapTexture != null) {
                this.mapTexture.close();
            }
            this.mapTexture = level != null ? new ChunkMapTexture(level, data) : null;
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

        this.actionSearchField = null;
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

        Level level = Minecraft.getInstance().level;
        this.grid = new ChunkMapGrid(level, data, gridLeft, gridTop, CELL_SIZE);
        this.mapTexture = level != null ? new ChunkMapTexture(level, data) : null;
        buildTopBoxButtons();
        ensureActionSearchField();
        buildActionButtons();

        int closeButtonX = this.gridLeft + (this.gridWidth - closeButtonWidth) / 2;
        this.closeButton = this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.onClose())
            .bounds(closeButtonX, closeButtonY, closeButtonWidth, closeButtonHeight)
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
        if (this.mapTexture != null) {
            this.mapTexture.close();
            this.mapTexture = null;
        }

        buttonsNeedUpdate = true;
        lastScrollOffset = -1;

        this.minecraft.setScreen(new ChunkMapScreen(this.data));
    }

    @Override
    public void removed() {
        if (this.grid != null) {
            this.grid.close();
        }
        if (this.mapTexture != null) {
            this.mapTexture.close();
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.grid != null) {
            this.grid.close();
        }
        if (this.mapTexture != null) {
            this.mapTexture.close();
        }
        super.onClose();
    }

    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);

        drawMapFrame(graphics);
        if (this.grid != null) {
            graphics.enableScissor(gridLeft - 2, gridTop - 2, gridLeft + gridWidth + 2, gridTop + gridHeight + 2);
            
            if (this.mapTexture != null) {
                int innerLeft = gridLeft + 1;
                int innerTop = gridTop + 1;
                int innerWidth = gridWidth - 2;
                int innerHeight = gridHeight - 2;
                this.mapTexture.render(graphics, innerLeft, innerTop, innerWidth, innerHeight);
            } else {
                graphics.fill(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight, 0x33000000);
            }

            grid.render(graphics, mouseX, mouseY);
            drawChunkloaderPoints(graphics, mouseX, mouseY);
            graphics.disableScissor();
        }
        
        drawTopBox(graphics);
        drawLeftPanel(graphics);
        drawSidePanel(graphics);

        if (buttonsNeedUpdate || lastScrollOffset != actionScrollOffset) {
            updateButtonPositions();
            buttonsNeedUpdate = false;
            lastScrollOffset = actionScrollOffset;
        }

        if (actionSearchField != null) {
            drawActionSearchFieldBackground(graphics);
            actionSearchField.render(graphics, mouseX, mouseY, delta);
        }

        int scissorTop = actionViewportTop;
        if (actionSearchField != null) {
            scissorTop = actionSearchField.getY() + actionSearchField.getHeight() + ACTION_SEARCH_LIST_GAP;
        }
        scissorTop = Math.min(scissorTop, actionViewportBottom);
        graphics.enableScissor(actionViewportLeft, scissorTop, actionViewportRight, actionViewportBottom);
        drawActionHeaders(graphics);
        for (Button button : actionButtons) {
            if (button != resetButton) {
                button.render(graphics, mouseX, mouseY, delta);
            }
        }
        graphics.disableScissor();
        
        drawActionScrollbar(graphics);
        
        for (Button button : topBoxButtons) {
            button.render(graphics, mouseX, mouseY, delta);
        }
        
        if (resetButton != null) {
            resetButton.render(graphics, mouseX, mouseY, delta);
        }
        
        if (closeButton != null) {
            closeButton.render(graphics, mouseX, mouseY, delta);
        }

        ChunkMapGrid.Cell hovered = grid != null ? grid.getHoveredCell(mouseX, mouseY) : null;
        if (hovered != null) {
            drawSimpleTooltip(graphics, hovered.buildTooltip(), mouseX, mouseY);
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
        int clamped = Math.max(0, Math.min(maxScroll, value));
        return Math.max(0, Math.min(maxScroll, clamped));
    }

    private void drawActionScrollbar(GuiGraphics graphics) {
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

        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
        }
        
        int scrollbarTrackColor = clientConfig != null ? clientConfig.getScrollbarTrackColor() : 0x33000000;
        int scrollbarThumbColor = clientConfig != null ? clientConfig.getScrollbarThumbColor() : 0xFFAAAAAA;
        
        graphics.fill(scrollbarX, scrollbarTrackTop, scrollbarX + scrollbarWidth, scrollbarTrackTop + availableHeight, scrollbarTrackColor);
        graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, scrollbarThumbColor);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0
            && actionSearchField != null
            && !actionSearchField.isMouseOver(event.x(), event.y())) {
            if (this.getFocused() == actionSearchField) {
                this.setFocused(null);
            }
            actionSearchField.setFocused(false);
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            ScrollbarMetrics metrics = getActionScrollbarMetrics();
            if (metrics != null
                && mouseX >= metrics.x && mouseX < metrics.x + metrics.width
                && mouseY >= metrics.thumbY && mouseY < metrics.thumbY + metrics.thumbHeight) {
                actionScrollbarDragging = true;
                actionScrollbarDragOffsetY = (int) (mouseY - metrics.thumbY);
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

        if (actionScrollbarDragging) {
            double mouseY = event.y();
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

        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }

        if (actionScrollbarDragging) {
            actionScrollbarDragging = false;
            return true;
        }

        return false;
    }

    private void drawActionHeaders(GuiGraphics graphics) {
        if (actionHeaderLayouts.isEmpty()) {
            return;
        }

        int contentTop = actionViewportTop;
        int buttonX = panelX + 8;
        int buttonWidth = panelWidth - 16;

        int textColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        for (ActionHeaderLayout headerLayout : actionHeaderLayouts) {
            int rowY = contentTop + headerLayout.yOffset - actionScrollOffset;
            int textY = rowY + (headerLayout.height - this.font.lineHeight) / 2 + 1;
            if (textY + this.font.lineHeight < actionViewportTop || textY > actionViewportBottom) {
                continue;
            }

            int headerWidth = this.font.width(headerLayout.text);
            int x = buttonX + Math.max(0, (buttonWidth - headerWidth) / 2);
            graphics.drawString(this.font, headerLayout.text, x, textY, textColor, false);
        }
    }

    private static final class ActionHeaderLayout {
        private final int yOffset;
        private final int height;
        private final Component text;

        private ActionHeaderLayout(int yOffset, int height, Component text) {
            this.yOffset = yOffset;
            this.height = height;
            this.text = text;
        }
    }
    
    private void drawMapFrame(GuiGraphics graphics) {
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
        
        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
        }
        
        int frameColor = clientConfig != null ? clientConfig.getFrameColor() : 0xFF111417;
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        
        graphics.fill(frameLeft, frameTop, frameRight, frameBottom, frameColor);
        graphics.fill(innerLeft - borderThickness, innerTop - borderThickness, innerRight + borderThickness, innerBottom + borderThickness, panelColor);
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

        for (Button button : actionButtons) {
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
            actionSearchField = new EditBox(this.font, x, y, width, ACTION_SEARCH_HEIGHT, Component.literal("Search"));
            actionSearchField.setMaxLength(64);
            actionSearchField.setValue(actionSearchQuery);
            actionSearchField.setResponder(text -> {
                actionSearchQuery = text;
                buildActionButtons();
                actionScrollOffset = clampActionScrollOffset(actionScrollOffset, getMaxActionScroll());
                buttonsNeedUpdate = true;
            });
            applyActionSearchFieldStyle();
            this.addWidget(actionSearchField);
        } else {
            actionSearchField.setX(x);
            actionSearchField.setY(y);
            actionSearchField.setWidth(width);
            actionSearchField.setHeight(ACTION_SEARCH_HEIGHT);
            actionSearchField.setValue(actionSearchQuery);
            applyActionSearchFieldStyle();
        }
    }

    private void applyActionSearchFieldStyle() {
        if (actionSearchField == null || clientConfig == null) {
            return;
        }

        actionSearchField.setTextColor(clientConfig.getActionSearchTextColor());
        int placeholderRgb = clientConfig.getActionSearchPlaceholderColor() & 0x00FFFFFF;
        Style placeholderStyle = Style.EMPTY.withColor(placeholderRgb);
        actionSearchField.setHint(Component.literal("Search...").withStyle(placeholderStyle));
    }

    private void drawActionSearchFieldBackground(GuiGraphics graphics) {
        if (actionSearchField == null) {
            return;
        }

        int x1 = actionSearchField.getX();
        int y1 = actionSearchField.getY();
        int x2 = x1 + actionSearchField.getWidth();
        int y2 = y1 + actionSearchField.getHeight();

        int backgroundColor = clientConfig != null ? clientConfig.getActionSearchBackgroundColor() : (clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36);
        int borderColor = clientConfig != null ? clientConfig.getActionSearchBorderColor() : (clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A);

        graphics.fill(x1, y1, x2, y2, backgroundColor);
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);
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

    private void drawTopBox(GuiGraphics graphics) {
        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
        }
        
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        int dividerColor = clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF;
        
        graphics.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, panelColor);
        
        graphics.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY - 1, borderColor);
        graphics.fill(topBoxX - 2, topBoxY + topBoxHeight + 1, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);
        graphics.fill(topBoxX - 2, topBoxY - 2, topBoxX - 1, topBoxY + topBoxHeight + 2, borderColor);
        graphics.fill(topBoxX + topBoxWidth + 1, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean verticalButtonBar = layoutPreset.isVerticalButtonBar();
        int numButtons = topBoxButtons.size();

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
                graphics.fill(lineX, lineY, lineX + 1, lineY + lineHeight, dividerColor);
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
                graphics.fill(lineX1, lineY, lineX2, lineY + 1, dividerColor);
            }
        }
    }
    
    private void drawLeftPanel(GuiGraphics graphics) {
        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
        }
        
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        int dividerColor = clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF;
        int leftPanelTextColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        int leftPanelValueColor = clientConfig != null ? clientConfig.getLeftPanelValueColor() : 0xFFFFFFFF;
        int leftPanelNameColor = clientConfig != null ? clientConfig.getLeftPanelNameColor() : 0xFFFFFFFF;
        
        graphics.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, panelColor);
        
        graphics.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY - 1, borderColor);
        graphics.fill(leftPanelX - 2, leftPanelY + leftPanelHeight + 1, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);
        graphics.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX - 1, leftPanelY + leftPanelHeight + 2, borderColor);
        graphics.fill(leftPanelX + leftPanelWidth + 1, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);

        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        int nameY = 0;
        
        String ownerName = data != null && data.ownerName() != null ? data.ownerName() : null;

        if (ownerName != null && !ownerName.isEmpty()) {
            int headX = leftPanelX + (leftPanelWidth - headSize) / 2;
            drawPlayerHead(graphics, headX, headY, headSize, ownerName);

            Component nameText = Component.literal(ownerName);
                int nameWidth = this.font.width(nameText);
                int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
                nameY = headY + headSize + 4;
                graphics.drawString(this.font, nameText, nameX, nameY, leftPanelNameColor, false);
            } else {
            nameY = 0;
        }

        if (data == null) {
            return;
        }

        int infoY = nameY > 0 ? nameY + this.font.lineHeight + 8 : headY + headSize + 8;

        graphics.fill(leftPanelX + padding, infoY - 4, leftPanelX + leftPanelWidth - padding, infoY - 3, dividerColor);
        infoY += 2;

        graphics.drawString(this.font, Component.literal("Status:"), leftPanelX + padding, infoY, leftPanelTextColor, false);
        String statusText = data.enabled() ? "active" : "inactive";
        int statusColor = data.enabled() ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) : 0xFF5555;
        int statusTextWidth = this.font.width(statusText);
        graphics.drawString(this.font, Component.literal(statusText), 
            leftPanelX + leftPanelWidth - padding - statusTextWidth, infoY, statusColor | 0xFF000000, false);
        infoY += 12;

        graphics.drawString(this.font, Component.literal("Dim:"), 
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
        int dimTextWidth = this.font.width(dimText);
        graphics.drawString(this.font, Component.literal(dimText), 
            leftPanelX + leftPanelWidth - padding - dimTextWidth, infoY, dimColor | 0xFF000000, false);
        infoY += 12;

        graphics.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
        infoY += 12;

        Component chunkLabel = Component.literal("Chunk:");
        Component blockLabel = Component.literal("Block:");
        int chunkLabelWidth = this.font.width(chunkLabel);
        int blockLabelWidth = this.font.width(blockLabel);
        int maxLabelWidth = Math.max(chunkLabelWidth, blockLabelWidth);
        int coordStartX = leftPanelX + padding + maxLabelWidth + 4;
        
        int chunkBlockY = infoY - 3;
        graphics.drawString(this.font, chunkLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);
        
        String chunkXStr = "X:" + data.centerChunkX();
        String chunkZStr = "Z:" + data.centerChunkZ();
        
        graphics.drawString(this.font, Component.literal(chunkXStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        graphics.drawString(this.font, Component.literal(chunkZStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        
        graphics.drawString(this.font, blockLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);
        
        String xStr = "X:" + blockPos.getX();
        String yStr = "Y:" + data.blockY();
        String zStr = "Z:" + blockPos.getZ();
        
        graphics.drawString(this.font, Component.literal(xStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        graphics.drawString(this.font, Component.literal(yStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        graphics.drawString(this.font, Component.literal(zStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        int originalInfoY = chunkBlockY + 12 + 3;
        infoY = originalInfoY;

            graphics.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
            int sdDividerY = infoY;
            infoY += 12;
        
            String radiusValue = String.valueOf(data.chunkRadius());
            String radiusSeparator = "/3";
        String radiusLabel = data.allowMobSpawning() ? "SD:" : "Radius:";
        int sdY = sdDividerY + 8;
        graphics.drawString(this.font, Component.literal(radiusLabel),
                leftPanelX + padding, sdY, leftPanelTextColor, false);
            graphics.drawString(this.font, Component.literal(radiusValue), 
                coordStartX, sdY, leftPanelValueColor, false);
            int radiusValueWidth = this.font.width(radiusValue);
            graphics.drawString(this.font, Component.literal(radiusSeparator), 
                coordStartX + radiusValueWidth, sdY, leftPanelValueColor, false);
            infoY = sdY + 12;
    }

    private void drawSidePanel(GuiGraphics graphics) {
        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
        }
        
        int panelColor = clientConfig != null ? clientConfig.getPanelColor() : 0xFF2B2F36;
        int borderColor = clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A;
        
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, panelColor);
        
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY - 1, borderColor);
        graphics.fill(panelX - 2, panelY + panelHeight + 1, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);
        graphics.fill(panelX - 2, panelY - 2, panelX - 1, panelY + panelHeight + 2, borderColor);
        graphics.fill(panelX + panelWidth + 1, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);

        net.minecraft.client.gui.Font font = this.font;
        int padding = 8;
        int y = panelY + padding + 4;
        
        Component title = Component.literal("Chunkloader: " + data.displayName());
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), panelX + padding, y, 0xFFFFFF, false);
        y += 16;
        
        drawSeparator(graphics, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        graphics.drawString(font, Component.literal("Status:").withStyle(ChatFormatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Component status = data.enabled()
            ? Component.literal("active")
            : Component.literal("inactive");
        int statusColor = data.enabled() 
            ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) 
            : 0xFF5555;
        graphics.drawString(font, status, panelX + padding + 50, y, statusColor, false);
        y += 12;

        graphics.drawString(font, Component.literal("Mode:").withStyle(ChatFormatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Component mode = data.allowMobSpawning()
            ? Component.literal("Fakeplayer (mob spawning)")
            : Component.literal("Chunkplayer");
        int modeColor = data.allowMobSpawning() ? 0x55FF55 : 0x79C0FF;
        graphics.drawString(font, mode, panelX + padding + 50, y, modeColor, false);
        y += 12;

        drawSeparator(graphics, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        graphics.drawString(font, Component.literal("Dimension:").withStyle(ChatFormatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(data.dimensionKey()), panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        graphics.drawString(font, Component.literal("Position:").withStyle(ChatFormatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(blockPos.getX() + " / " + data.blockY() + " / " + blockPos.getZ()), 
            panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        String radiusLabelSide = data.allowMobSpawning() ? "SD:" : "Radius:";
        graphics.drawString(font, Component.literal(radiusLabelSide).withStyle(ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        Component radiusText = Component.literal(data.chunkRadius() + " / 3");
        graphics.drawString(font, radiusText, panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        drawSeparator(graphics, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        if (!data.allowMobSpawning()) {
            int legendStartY = y;
            int buttonAreaStart = panelY + panelHeight - 80;
            int legendHeight = 12 + 14 + 14 + 14;

            if (legendStartY + legendHeight > buttonAreaStart - 10) {
                return;
            }

            graphics.drawString(font, Component.literal("Legend").withStyle(ChatFormatting.BOLD),
                panelX + padding, y, 0xFFFFFF, false);
            y += 12;

            int loadedColor = data.allowMobSpawning()
                ? ChunkMapGrid.COLOR_LOADED
                : ChunkMapGrid.COLOR_IN_RANGE;
            drawLegendItem(graphics, font, panelX + padding, y, loadedColor,
                Component.literal("Loaded"));
            y += 14;

            if (data.allowMobSpawning()) {
                drawLegendItem(graphics, font, panelX + padding, y, ChunkMapGrid.COLOR_LOADED,
                    Component.literal("Simulation Distance"));
                y += 14;
            }

            drawLegendItem(graphics, font, panelX + padding, y, ChunkMapGrid.COLOR_IN_RANGE,
                Component.literal("Within radius"));
            y += 14;

            drawLegendItem(graphics, font, panelX + padding, y, ChunkMapGrid.COLOR_OTHER,
                Component.literal("Other chunkloader"));
        }
    }

    private ResourceLocation getPlayerSkinTexture(String playerName) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || playerName == null || playerName.isEmpty()) {
            return FALLBACK_SKIN;
        }
        
        ResourceLocation entryTexture = resolveSkinFromPlayerListEntry(client, playerName);
        if (entryTexture != null && !entryTexture.equals(FALLBACK_SKIN)) {
            return entryTexture;
        }
        
        return FALLBACK_SKIN;
    }

    private ResourceLocation resolveSkinFromPlayerListEntry(Minecraft client, String playerName) {
        return null;
    }

    private void drawPlayerHead(GuiGraphics graphics, int x, int y, int size, String playerName) {
        ResourceLocation texture = getPlayerSkinTexture(playerName);

        blitNonAtlas(graphics, texture, x, y, 8f, 8f, 8, 8, size, size, 64, 64);
        blitNonAtlas(graphics, texture, x, y, 40f, 8f, 8, 8, size, size, 64, 64);
    }

    private static void blitNonAtlas(
        GuiGraphics g,
        ResourceLocation texture,
        int x,
        int y,
        float u,
        float v,
        int uWidth,
        int vHeight,
        int width,
        int height,
        int textureWidth,
        int textureHeight
    ) {
        try {
            try {
                var tm = Minecraft.getInstance().getTextureManager();
                var at = tm.getTexture(texture);
                if (at != null) {
                    RenderSystem.setShaderTexture(0, at.getTextureView());
                }
            } catch (Throwable ignored) {}

            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, 0xFFFFFFFF);
        } catch (Throwable t) {
            g.blit(texture, x, y, (int) u, (int) v, width, height, textureWidth, textureHeight);
        }
    }
    
    private void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x4A4A4A);
    }

    private void drawLegendItem(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int color, Component text) {
        int squareSize = 6;
        int squareY = y + (font.lineHeight - squareSize) / 2;
        graphics.fill(x, squareY, x + squareSize, squareY + squareSize, 0xFF000000 | color);
        graphics.fill(x + 1, squareY + 1, x + squareSize - 1, squareY + squareSize - 1, color);
        graphics.drawString(font, text, x + 10, y, 0xFFFFFF, false);
    }

    private void drawSimpleTooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY, ItemStack.EMPTY);
    }

    private void buildTopBoxButtons() {
        topBoxButtons.forEach(this::removeWidget);
        topBoxButtons.clear();

        if (clientConfig == null) {
            clientConfig = de.chunkloader.client.config.ClientConfig.load();
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
        
        infoButton = Button.builder(
            Component.literal("Info"),
            btn -> {
                this.minecraft.setScreen(new ChunkloaderMenuScreen(this));
            })
            .bounds(startX, startY, buttonWidth, buttonHeight)
            .build();
        infoButton.setMessage(Component.literal("Info").withStyle(ChatFormatting.WHITE));
        topBoxButtons.add(infoButton);
        this.addRenderableWidget(infoButton);
        
        int helpButtonX = verticalButtonBar ? startX : (startX + buttonWidth + spacing);
        int helpButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 1) : startY;
        Button helpButton = Button.builder(
            Component.literal("Help"),
            btn -> {
                this.minecraft.setScreen(new ChunkMapHelpScreen(this));
            })
            .bounds(helpButtonX, helpButtonY, buttonWidth, buttonHeight)
            .build();
        helpButton.setMessage(Component.literal("Help").withStyle(ChatFormatting.WHITE));
        topBoxButtons.add(helpButton);
        this.addRenderableWidget(helpButton);

        int listButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 2);
        int listButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 2) : startY;
        Button listButton = Button.builder(
            Component.literal("List"),
            btn -> {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            })
            .bounds(listButtonX, listButtonY, buttonWidth, buttonHeight)
            .build();
        listButton.setMessage(Component.literal("List").withStyle(ChatFormatting.WHITE));
        topBoxButtons.add(listButton);
        this.addRenderableWidget(listButton);

        int layoutButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 3);
        int layoutButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 3) : startY;
        Button uiButton = Button.builder(
            Component.literal(uiLabel),
            btn -> cycleLayoutPreset())
            .bounds(layoutButtonX, layoutButtonY, buttonWidth, buttonHeight)
            .build();
        uiButton.setMessage(Component.literal(uiLabel).withStyle(ChatFormatting.WHITE));
        topBoxButtons.add(uiButton);
        this.addRenderableWidget(uiButton);
        
        int deleteButtonX = verticalButtonBar ? startX : (startX + (buttonWidth + spacing) * 4);
        int deleteButtonY = verticalButtonBar ? (startY + (buttonHeight + spacing) * 4) : startY;
        Button deleteButton = Button.builder(
            Component.literal("Delete"),
            btn -> {
                this.minecraft.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Component.literal("Delete Chunkloader?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.literal("This will permanently delete this chunkloader.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                            ChunkloaderActionPayload.Action.DELETE,
                            data.fakeplayerChunkX(),
                            data.fakeplayerChunkZ(),
                            0
                        );
                        this.minecraft.setScreen(null);
                    },
                    null
                ));
            })
            .bounds(deleteButtonX, deleteButtonY, buttonWidth, buttonHeight)
            .build();
        deleteButton.setMessage(Component.literal("Delete").withStyle(ChatFormatting.RED));
        topBoxButtons.add(deleteButton);
        this.addRenderableWidget(deleteButton);
    }

    private void buildActionButtons() {
        actionButtons.forEach(this::removeWidget);
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
        int headerHeight = this.font.lineHeight + 4;

        boolean generalHeaderMatches = actionSearchMatches("General");
        String enableLabelRaw = data.enabled()
            ? (data.allowMobSpawning() ? "Disable Fakeplayer" : "Disable Chunkplayer")
            : (data.allowMobSpawning() ? "Enable Fakeplayer" : "Enable Chunkplayer");
        boolean enableButtonMatches = actionSearchMatches(enableLabelRaw);
        boolean showEnableButton = generalHeaderMatches || enableButtonMatches;

        Button enableButton = Button.builder(
            data.enabled()
                ? (data.allowMobSpawning() ? Component.literal("Disable Fakeplayer") : Component.literal("Disable Chunkplayer"))
                : (data.allowMobSpawning() ? Component.literal("Enable Fakeplayer") : Component.literal("Enable Chunkplayer")),
            btn -> {
                ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_ENABLED,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
                );
                if (data.enabled()) {
                    Minecraft.getInstance().setScreen(null);
                }
            })
            .bounds(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.enabled()) {
            enableButton.setMessage((data.allowMobSpawning() ? Component.literal("Disable Fakeplayer") : Component.literal("Disable Chunkplayer")).withStyle(ChatFormatting.RED));
        } else {
            enableButton.setMessage((data.allowMobSpawning() ? Component.literal("Enable Fakeplayer") : Component.literal("Enable Chunkplayer")).withStyle(ChatFormatting.GREEN));
        }
        if (showEnableButton) {
            actionButtons.add(enableButton);
            actionButtonYOffset.put(enableButton, cursorY);
            this.addRenderableWidget(enableButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        String radiusHeaderRaw = data.allowMobSpawning() ? "Simulation distance" : "Radius";
        boolean radiusHeaderMatches = actionSearchMatches(radiusHeaderRaw);
        boolean radiusDownMatches = actionSearchMatches(data.allowMobSpawning() ? "SD -1" : "Radius -1");
        boolean radiusUpMatches = actionSearchMatches(data.allowMobSpawning() ? "SD +1" : "Radius +1");
        boolean showRadiusRow = radiusHeaderMatches || radiusDownMatches || radiusUpMatches;

        boolean modeHeaderMatches = actionSearchMatches("Mode");
        boolean mobButtonMatches = actionSearchMatches("mob spawning");
        boolean showModeHeader = modeHeaderMatches || mobButtonMatches || showRadiusRow;
        if (showModeHeader) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Mode").withStyle(ChatFormatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        Button mobButton = Button.builder(
            data.allowMobSpawning()
                ? Component.literal("Disable mob spawning")
                : Component.literal("Enable mob spawning"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_MOB_SPAWNING,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .bounds(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.allowMobSpawning()) {
            mobButton.setMessage(Component.literal("Disable mob spawning").withStyle(ChatFormatting.BLUE));
        } else {
            mobButton.setMessage(Component.literal("Enable mob spawning").withStyle(ChatFormatting.GREEN));
        }
        if (modeHeaderMatches || mobButtonMatches) {
            actionButtons.add(mobButton);
            actionButtonYOffset.put(mobButton, cursorY);
            this.addRenderableWidget(mobButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean showRadiusButtons = modeHeaderMatches || showRadiusRow;

        int radiusY = 0;
        int halfWidth = (buttonWidth - 4) / 2;
        boolean isFakePlayer = data.allowMobSpawning();
        boolean canDecrease = data.chunkRadius() > 0;
        boolean canIncrease = data.canIncreaseRadius();
        
        String radiusDownLabel = isFakePlayer ? "SD -1" : "Radius -1";
        Button radiusDown = Button.builder(
            Component.literal(radiusDownLabel),
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
            .bounds(buttonX, radiusY, halfWidth, 20)
            .build();
        if (!canDecrease) {
            radiusDown.active = false;
            radiusDown.setMessage(Component.literal(radiusDownLabel).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (showRadiusButtons) {
            actionButtons.add(radiusDown);
            actionButtonYOffset.put(radiusDown, cursorY);
            this.addRenderableWidget(radiusDown);
        }

        String radiusUpLabel = isFakePlayer ? "SD +1" : "Radius +1";
        Button radiusUp = Button.builder(
            Component.literal(radiusUpLabel),
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
            .bounds(buttonX + halfWidth + 4, radiusY, halfWidth, 20)
            .build();
        if (!canIncrease) {
            radiusUp.active = false;
            radiusUp.setMessage(Component.literal(radiusUpLabel).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (showRadiusButtons) {
            actionButtons.add(radiusUp);
            actionButtonYOffset.put(radiusUp, cursorY);
            this.addRenderableWidget(radiusUp);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean renameMatches = actionSearchMatches("Rename");
        boolean showHideNameMatches = actionSearchMatches("Show name") || actionSearchMatches("Hide name");

        Button renameButton = Button.builder(
            Component.literal("Rename"),
            btn -> {
                this.minecraft.setScreen(new RenameChunkloaderScreen(
                    this,
                    data.fakeplayerChunkX(),
                    data.fakeplayerChunkZ(),
                    data.displayName()
                ));
            })
            .bounds(buttonX, 0, buttonWidth, 20)
            .build();
        renameButton.setMessage(Component.literal("Rename").withStyle(ChatFormatting.WHITE));

        int nameVisibleY = 0;
        Button nameVisibleButton = Button.builder(
            data.nameVisible()
                ? Component.literal("Hide name")
                : Component.literal("Show name"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_NAME_VISIBLE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .bounds(buttonX, nameVisibleY, buttonWidth, 20)
            .build();
        if (data.nameVisible()) {
            nameVisibleButton.setMessage(Component.literal("Hide name").withStyle(ChatFormatting.WHITE));
        } else {
            nameVisibleButton.setMessage(Component.literal("Show name").withStyle(ChatFormatting.WHITE));
        }

        boolean hideOptionsHeaderMatches = actionSearchMatches("Hide options");
        boolean hideOtherDotsMatches = actionSearchMatches("Hide other dots") || actionSearchMatches("Show other dots");
        boolean showHideOptionsSection = hideOptionsHeaderMatches || hideOtherDotsMatches || showHideNameMatches;
        if (showHideOptionsSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Hide options").withStyle(ChatFormatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        if (showHideOptionsSection) {
            actionButtons.add(nameVisibleButton);
            actionButtonYOffset.put(nameVisibleButton, cursorY);
            this.addRenderableWidget(nameVisibleButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        int hideOtherDotsY = 0;
        Button hideOtherDotsButton = Button.builder(
            data.hideOtherDots()
                ? Component.literal("Show other dots")
                : Component.literal("Hide other dots"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_HIDE_OTHER_DOTS,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .bounds(buttonX, hideOtherDotsY, buttonWidth, 20)
            .build();
        if (data.hideOtherDots()) {
            hideOtherDotsButton.setMessage(Component.literal("Show other dots").withStyle(ChatFormatting.WHITE));
        } else {
            hideOtherDotsButton.setMessage(Component.literal("Hide other dots").withStyle(ChatFormatting.WHITE));
        }
        if (showHideOptionsSection) {
            actionButtons.add(hideOtherDotsButton);
            actionButtonYOffset.put(hideOtherDotsButton, cursorY);
            this.addRenderableWidget(hideOtherDotsButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean visualizationHeaderMatches = actionSearchMatches("Visualization");
        boolean visualizeMatches = actionSearchMatches("visualization");
        boolean visualize3dMatches = actionSearchMatches("3D");
        boolean showVisualizationSection = visualizationHeaderMatches || visualizeMatches || visualize3dMatches;
        if (showVisualizationSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Visualization").withStyle(ChatFormatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        int visualizeY = 0;
        Button visualizeButton = Button.builder(
            data.visualizeActive()
                ? Component.literal("Disable visualization")
                : Component.literal("Enable visualization"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .bounds(buttonX, visualizeY, buttonWidth, 20)
            .build();
        if (data.visualizeActive()) {
            visualizeButton.setMessage(Component.literal("Disable visualization").withStyle(ChatFormatting.WHITE));
        } else {
            visualizeButton.setMessage(Component.literal("Enable visualization").withStyle(ChatFormatting.WHITE));
        }
        if (showVisualizationSection) {
            actionButtons.add(visualizeButton);
            actionButtonYOffset.put(visualizeButton, cursorY);
            this.addRenderableWidget(visualizeButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        int visualize3DY = 0;
        Button visualize3DButton = Button.builder(
            data.visualize3DActive()
                ? Component.literal("Disable 3D visualization")
                : Component.literal("Enable 3D visualization"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE3D,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .bounds(buttonX, visualize3DY, buttonWidth, 20)
            .build();
        if (data.visualize3DActive()) {
            visualize3DButton.setMessage(Component.literal("Disable 3D visualization").withStyle(ChatFormatting.WHITE));
        } else {
            visualize3DButton.setMessage(Component.literal("Enable 3D visualization").withStyle(ChatFormatting.WHITE));
        }
        if (showVisualizationSection) {
            actionButtons.add(visualize3DButton);
            actionButtonYOffset.put(visualize3DButton, cursorY);
            this.addRenderableWidget(visualize3DButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean settingsHeaderMatches = actionSearchMatches("Settings");
        boolean panelColorMatches = actionSearchMatches("Panel color");
        boolean keybindMatches = actionSearchMatches("Keybinds");
        boolean showSettingsSection = settingsHeaderMatches || panelColorMatches || keybindMatches || renameMatches;
        if (showSettingsSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Settings").withStyle(ChatFormatting.GRAY)));
            cursorY += headerHeight + gap;
        }

        if (showSettingsSection) {
            actionButtons.add(renameButton);
            actionButtonYOffset.put(renameButton, cursorY);
            this.addRenderableWidget(renameButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }
        
        int panelColorY = 0;
        Button panelColorButton = Button.builder(
            Component.literal("Panel color"),
            btn -> {
                if (clientConfig == null) {
                    clientConfig = de.chunkloader.client.config.ClientConfig.load();
                }
                this.minecraft.setScreen(new PanelColorScreen(this, clientConfig));
            })
            .bounds(buttonX, panelColorY, buttonWidth, 20)
            .build();
        panelColorButton.setMessage(Component.literal("Panel color").withStyle(ChatFormatting.WHITE));
        if (showSettingsSection) {
            actionButtons.add(panelColorButton);
            actionButtonYOffset.put(panelColorButton, cursorY);
            this.addRenderableWidget(panelColorButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }
        
        int keybindY = 0;
        Button keybindButton = Button.builder(
            Component.literal("Keybinds"),
            btn -> {
                this.minecraft.setScreen(new KeybindConfigScreen(this));
            })
            .bounds(buttonX, keybindY, buttonWidth, 20)
            .build();
        keybindButton.setMessage(Component.literal("Keybinds").withStyle(ChatFormatting.WHITE));
        if (showSettingsSection) {
            actionButtons.add(keybindButton);
            actionButtonYOffset.put(keybindButton, cursorY);
            this.addRenderableWidget(keybindButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        actionContentHeight = cursorY > 0 ? Math.max(0, cursorY - gap) : 0;
        
        int resetY = footerRowY;
        resetButton = Button.builder(
            Component.literal("Reset to defaults"),
            btn -> {
                this.minecraft.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Component.literal("Reset to Defaults?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.literal("This will reset all settings to default values.\nThis action cannot be undone!"),
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
            .bounds(buttonX, resetY, buttonWidth, 20)
            .build();
        resetButton.setMessage(Component.literal("Reset to defaults").withStyle(ChatFormatting.WHITE));
        this.addRenderableWidget(resetButton);

        buttonsNeedUpdate = true;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawChunkloaderPoints(GuiGraphics graphics, int mouseX, int mouseY) {
        int cellSize = CELL_SIZE;
        int gridInnerLeft = gridLeft + 1;
        int gridInnerTop = gridTop + 1;

        int ownOffsetX = data.fakeplayerChunkX() - data.centerChunkX();
        int ownOffsetZ = data.fakeplayerChunkZ() - data.centerChunkZ();
        int halfMap = (data.mapWidth() - 1) / 2;
        int ownColumn = ownOffsetX + halfMap;
        int ownRow = ownOffsetZ + halfMap;

        if (ownColumn >= 0 && ownColumn < data.mapWidth() && ownRow >= 0 && ownRow < data.mapHeight()) {
            int ownInnerChunkX = Math.floorMod(data.fakeplayerBlockX(), 16);
            int ownInnerChunkZ = Math.floorMod(data.fakeplayerBlockZ(), 16);

            int ownCellLeft = gridInnerLeft + ownColumn * cellSize;
            int ownCellTop = gridInnerTop + ownRow * cellSize;
            float ownPosInCellX = (ownInnerChunkX / 16.0f) * cellSize;
            float ownPosInCellZ = (ownInnerChunkZ / 16.0f) * cellSize;

            int ownPointX = ownCellLeft + (int) ownPosInCellX;
            int ownPointY = ownCellTop + (int) ownPosInCellZ;

            int ownPointColor = 0xFFFF0000;
            int ownPointSize = 3;
            int ownPointHalfSize = ownPointSize / 2;

            graphics.fill(
                ownPointX - ownPointHalfSize - 1, ownPointY - ownPointHalfSize - 1,
                ownPointX + ownPointHalfSize + 1, ownPointY + ownPointHalfSize + 1,
                0xFF000000
            );
            graphics.fill(
                ownPointX - ownPointHalfSize, ownPointY - ownPointHalfSize,
                ownPointX + ownPointHalfSize, ownPointY + ownPointHalfSize,
                ownPointColor
            );

            if (mouseX >= ownPointX - ownPointHalfSize - 2 && mouseX < ownPointX + ownPointHalfSize + 2 &&
                mouseY >= ownPointY - ownPointHalfSize - 2 && mouseY < ownPointY + ownPointHalfSize + 2) {
                String ownTooltipText = data.fakeplayerName() != null ? data.fakeplayerName() :
                    String.format("%s at (%d, %d)", data.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer",
                        data.fakeplayerChunkX(), data.fakeplayerChunkZ());
                drawSimpleTooltip(graphics, List.of(Component.literal(ownTooltipText)), mouseX, mouseY);
            }
        }

        if (data.otherChunkloaders() == null || data.otherChunkloaders().isEmpty()) {
            return;
        }
        
        if (data.hideOtherDots()) {
            return;
        }

        int halfMapWidth = (data.mapWidth() - 1) / 2;
        int halfMapHeight = (data.mapHeight() - 1) / 2;

        for (de.chunkloader.network.ChunkloaderPosition pos : data.otherChunkloaders()) {
            int offsetX = pos.chunkX() - data.centerChunkX();
            int offsetZ = pos.chunkZ() - data.centerChunkZ();

            int column = offsetX + halfMapWidth;
            int row = offsetZ + halfMapHeight;

            if (column < 0 || column >= data.mapWidth() || row < 0 || row >= data.mapHeight()) {
                continue;
            }

            int innerChunkX = Math.floorMod(pos.blockX(), 16);
            int innerChunkZ = Math.floorMod(pos.blockZ(), 16);

            int cellLeft = gridInnerLeft + column * cellSize;
            int cellTop = gridInnerTop + row * cellSize;
            float posInCellX = (innerChunkX / 16.0f) * cellSize;
            float posInCellZ = (innerChunkZ / 16.0f) * cellSize;

            int pointX = cellLeft + (int) posInCellX;
            int pointY = cellTop + (int) posInCellZ;

            int pointColor = pos.isFakeplayer() ? 0xFF55FF55 : 0xFF5996FF;
            int pointSize = 3;
            int pointHalfSize = pointSize / 2;

            graphics.fill(
                pointX - pointHalfSize - 1, pointY - pointHalfSize - 1,
                pointX + pointHalfSize + 1, pointY + pointHalfSize + 1,
                0xFF000000
            );
            graphics.fill(
                pointX - pointHalfSize, pointY - pointHalfSize,
                pointX + pointHalfSize, pointY + pointHalfSize,
                pointColor
            );

            if (mouseX >= pointX - pointHalfSize - 2 && mouseX < pointX + pointHalfSize + 2 &&
                mouseY >= pointY - pointHalfSize - 2 && mouseY < pointY + pointHalfSize + 2) {
                String tooltipText = pos.name() != null ? pos.name() :
                    String.format("%s at (%d, %d)", pos.isFakeplayer() ? "Fakeplayer" : "Chunkplayer", pos.chunkX(), pos.chunkZ());
                drawSimpleTooltip(graphics, List.of(Component.literal(tooltipText)), mouseX, mouseY);
            }
        }
    }

    private static class ChunkMapGrid implements AutoCloseable {

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

        ChunkMapGrid(Level level, ChunkMapData data, int left, int top, int cellSize) {
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
                        level,
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

        void render(GuiGraphics graphics, int mouseX, int mouseY) {
            graphics.fill(left, top, left + width, top + BORDER, COLOR_BORDER);
            graphics.fill(left, top + height - BORDER, left + width, top + height, COLOR_BORDER);
            graphics.fill(left, top, left + BORDER, top + height, COLOR_BORDER);
            graphics.fill(left + width - BORDER, top, left + width, top + height, COLOR_BORDER);

            for (Cell cell : cells) {
                cell.render(graphics, mouseX, mouseY);
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
            return ((long) offsetX << 32) | (offsetZ & 0xFFFFFFFFL);
        }

        static class Cell implements AutoCloseable {

            private static final int COLOR_OCCUPIED = 0x88404040;
            private static final int COLOR_LOADED_OVERLAY = 0x2687FF59;
            private static final int COLOR_LOADED_OVERLAY_CHUNKPLAYER = 0x265555FF;
            private static final int COLOR_SIMULATION_DISTANCE = 0x2687FF59;
            private static final int COLOR_RANGE_OVERLAY = 0x1A3D7FFF;
            private static final int COLOR_RANGE_OVERLAY_DISABLED = 0x26FF5555;

            private final ChunkPos chunkPos;
            private final ChunkMapCell state;
            private final int left;
            private final int top;
            private final int size;
            private final ChunkMapData data;
            private final int gridLeft;
            private final int gridTop;
            private final int gridRight;
            private final int gridBottom;

            Cell(Level level, ChunkPos chunkPos, ChunkMapCell state, int left, int top, int size, int sampleY,
                 ChunkMapData data, int gridLeft, int gridTop, int gridRight, int gridBottom) {
                this.chunkPos = chunkPos;
                this.state = state;
                this.left = left;
                this.top = top;
                this.size = size;
                this.data = data;
                this.gridLeft = gridLeft;
                this.gridTop = gridTop;
                this.gridRight = gridRight;
                this.gridBottom = gridBottom;
            }

            void render(GuiGraphics graphics, int mouseX, int mouseY) {
                int innerLeft = Math.max(gridLeft, left);
                int innerTop = Math.max(gridTop, top);
                int innerRight = Math.min(gridRight, left + size);
                int innerBottom = Math.min(gridBottom, top + size);

                if (innerRight <= innerLeft || innerBottom <= innerTop) {
                    return;
                }

                boolean isSimulatedOnly = state.simulatedByFakeplayer() && !state.loaded() && !state.occupiedByOther();

                if (!isSimulatedOnly) {
                    graphics.fill(innerLeft, innerTop, innerRight, innerBottom, 0x1A000000);
                }

                int overlayWidth = innerRight - innerLeft;
                int overlayHeight = innerBottom - innerTop;
                if (overlayWidth > 0 && overlayHeight > 0) {
                    int srcX = innerLeft - left;
                    int srcY = innerTop - top;
                    ChunkMapScreen.blitNonAtlas(
                        graphics,
                        GRID_OVERLAY,
                        innerLeft,
                        innerTop,
                        srcX,
                        srcY,
                        overlayWidth,
                        overlayHeight,
                        overlayWidth,
                        overlayHeight,
                        18,
                        18
                    );
                }

                if (state.withinRange() && !state.loaded()) {
                    int overlayColor = data.enabled() ? COLOR_RANGE_OVERLAY : COLOR_RANGE_OVERLAY_DISABLED;
                    graphics.fill(innerLeft, innerTop, innerRight, innerBottom, overlayColor);
                }

                if (state.loaded() && !state.occupiedByOther()) {
                    int loadedColor = data.allowMobSpawning()
                        ? COLOR_LOADED_OVERLAY
                        : COLOR_LOADED_OVERLAY_CHUNKPLAYER;
                    graphics.fill(innerLeft, innerTop, innerRight, innerBottom, loadedColor);
                }

                if (state.simulatedByFakeplayer()) {
                    graphics.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_SIMULATION_DISTANCE);
                }

                if (state.occupiedByOther()) {
                    if (data.allowMobSpawning()) {
                        if (!state.simulatedByFakeplayer()) {
                            graphics.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                        }
                    } else {
                        graphics.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                    }
                }

                if (isHovered(mouseX, mouseY)) {
                    int hoverWidth = innerRight - innerLeft;
                    int hoverHeight = innerBottom - innerTop;
                    if (hoverWidth > 0 && hoverHeight > 0) {
                        int srcX = innerLeft - left;
                        int srcY = innerTop - top;
                        ChunkMapScreen.blitNonAtlas(
                            graphics,
                            HOVER_OVERLAY,
                            innerLeft,
                            innerTop,
                            srcX,
                            srcY,
                            hoverWidth,
                            hoverHeight,
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

            List<Component> buildTooltip() {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("Chunk X:" + chunkPos.x + " Z:" + chunkPos.z));

                if (state.loaded()) {
                    tooltip.add(Component.literal("Loaded by this chunkloader"));
                } else if (state.withinRange()) {
                    if (!data.allowMobSpawning()) {
                        tooltip.add(Component.literal("Inside radius (enable to load)"));
                    } else {
                        tooltip.add(Component.literal("Inside simulation distance (enable to load)"));
                    }
                } else {
                    tooltip.add(Component.literal("Outside of this chunkloader"));
                }

                if (state.occupiedByOther() && state.loaded() && !state.simulatedByFakeplayer()) {
                    tooltip.add(Component.literal("Loaded by a different chunkloader"));
                }

                return ImmutableList.copyOf(tooltip);
            }

            @Override
            public void close() {
            }
        }
    }
}
