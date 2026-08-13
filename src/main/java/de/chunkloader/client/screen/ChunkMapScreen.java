package de.chunkloader.client.screen;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import de.chunkloader.ChunkloaderForgeMod;
import com.google.common.collect.ImmutableList;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.client.config.PanelColorTarget;
import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.ChunkloaderNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ChunkMapScreen extends Screen {

    private static ClientConfig clientConfig = null;

    private static final int CELL_SIZE = 18;
    private static final int ACTION_ROW_GAP = 2;
    private static final int ACTION_SEARCH_HEIGHT = 18;
    private static final int ACTION_SEARCH_INSET_X = 4;
    private static final int ACTION_SEARCH_INSET_Y = 5;
    private static final int ACTION_SEARCH_TEXT_HEIGHT = 10;
    private static final int ACTION_SEARCH_GAP = 0;
    private static final int ACTION_SEARCH_Y_OFFSET = -4;
    private static final int ACTION_SEARCH_LIST_GAP = 3;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final Identifier GRID_OVERLAY = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "textures/gui/grid_overlay.png");
    private static final Identifier HOVER_OVERLAY = Identifier.fromNamespaceAndPath(ChunkloaderForgeMod.MODID, "textures/gui/cell_overlay.png");
    private static final Identifier FALLBACK_SKIN = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    private static final Component LABEL_STATUS = Component.literal("Status:");
    private static final Component LABEL_DIM = Component.literal("Dim:");
    private static final Component LABEL_CHUNK = Component.literal("Chunk:");
    private static final Component LABEL_BLOCK = Component.literal("Block:");
    private static final Component LABEL_SD = Component.literal("SD:");
    private static final Component LABEL_RADIUS = Component.literal("Radius:");

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
    private final List<Button> topBoxButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private final Map<Button, Integer> actionButtonYOffset = new HashMap<>();
    private final Map<Button, Boolean> buttonOriginalActiveState = new HashMap<>();
    private final List<ActionHeaderLayout> actionHeaderLayouts = new ArrayList<>();
    private int actionContentHeight = 0;
    private EditBox actionSearchField;
    private String actionSearchQuery = "";
    private Button resetButton;
    private Button infoButton;
    private Button closeButton;
    private Button easterEggLockedSkinButton;
    private int footerRowY;
    private int actionScrollOffset = 0;
    private int actionViewportLeft;
    private int actionViewportRight;
    private int actionViewportTop;
    private int actionViewportBottom;
    private boolean buttonsNeedUpdate = true;
    private int lastScrollOffset = -1;
    private Boolean previousAllowMobSpawning = null;
    private int mapRotation = 0;

    private boolean actionScrollbarDragging = false;
    private int actionScrollbarDragOffsetY = 0;
    private PanelColorEditorOverlay colorEditorOverlay;
    private boolean retainChunkMapSession;

    public ChunkMapData getData() { return this.data; }

    public void clearAndInit() { this.clearWidgets(); this.init(); }

    private static final int TOPBOX_BUTTON_WIDTH = 50;
    private static final int TOPBOX_BUTTON_HEIGHT = 20;
    private static final int TOPBOX_BUTTON_COUNT = 5;

    public record PanelColorTargetHit(PanelColorTarget target, int left, int top, int right, int bottom) {
    }

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

    private int getVisibleActionScrollOffset() {
        return colorEditorOverlay != null ? 0 : actionScrollOffset;
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
        int scrollbarY = scrollbarTrackTop + (int) ((double) getVisibleActionScrollOffset() / maxScroll * (availableHeight - scrollbarHeight));
        return new ScrollbarMetrics(scrollbarX, scrollbarWidth, scrollbarTrackTop, availableHeight, scrollbarY, scrollbarHeight, maxScroll);
    }

    public ChunkMapScreen(ChunkMapData data) {
        super(Component.literal("Chunk Loader Map"));
        this.data = data;
        this.previousAllowMobSpawning = data.allowMobSpawning();
        this.mapRotation = rotationFromYaw(getOpenDirectionYaw());
    }

    private float getOpenDirectionYaw() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            return client.player.getYRot();
        }
        return data.fakeplayerYaw();
    }

    private static int rotationFromYaw(float yawDegrees) {
        float wrappedYaw = Mth.wrapDegrees(yawDegrees);
        if (wrappedYaw >= -45.0f && wrappedYaw <= 45.0f) {
            return 2;
        }
        if (wrappedYaw > 45.0f && wrappedYaw < 135.0f) {
            return 1;
        }
        if (wrappedYaw >= 135.0f || wrappedYaw <= -135.0f) {
            return 0;
        }
        return 3;
    }

        public void updateData(ChunkMapData newData) {
            boolean previousWasFakeplayer = this.data != null && this.data.allowMobSpawning();
            boolean newIsFakeplayer = newData.allowMobSpawning();

            boolean sameMapLayout = this.data != null
                && this.data.centerChunkX() == newData.centerChunkX()
                && this.data.centerChunkZ() == newData.centerChunkZ()
                && this.data.topLeftChunkX() == newData.topLeftChunkX()
                && this.data.topLeftChunkZ() == newData.topLeftChunkZ()
                && this.data.mapWidth() == newData.mapWidth()
                && this.data.mapHeight() == newData.mapHeight()
                && this.data.chunkRadius() == newData.chunkRadius()
                && this.data.enabled() == newData.enabled()
                && this.data.allowMobSpawning() == newData.allowMobSpawning()
                && this.data.blockY() == newData.blockY()
                && java.util.Objects.equals(this.data.dimensionKey(), newData.dimensionKey());

            this.data = newData;

            if (previousAllowMobSpawning != null && previousWasFakeplayer != newIsFakeplayer) {
                if (previousWasFakeplayer && !newIsFakeplayer) {
                    de.chunkloader.client.hud.SimulationStatusHUD.setEnabled(false);
                } else if (!previousWasFakeplayer && newIsFakeplayer) {
                    de.chunkloader.client.hud.ChunkplayerStatusHUD.setEnabled(false);
                }
            }
            previousAllowMobSpawning = newIsFakeplayer;

            if (this.grid != null && sameMapLayout && this.grid.applyMarkerStates(newData)) {
                if (panelWidth > 0) {
                    int savedScrollOffset = actionScrollOffset;
                    buildTopBoxButtons();
                    ensureActionSearchField();
                    buildActionButtons();
                    actionScrollOffset = clampActionScrollOffset(savedScrollOffset, getMaxActionScroll());
                    buttonsNeedUpdate = true;
                }
                return;
            }

            if (this.width > 0 && this.height > 0) {
                this.init();
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

        int overlayRight = getVerticalOverlayRightEdge();
        int startX;
        if (overlayRight > 0) {
            int leftMargin = 18;
            int availableLeft = overlayRight;
            int availableRight = this.width - leftMargin;
            int availableWidth = availableRight - availableLeft;
            startX = availableLeft + (availableWidth - totalWidth) / 2;
            if (startX < availableLeft + 12) {
                startX = availableLeft + 12;
            }
        } else {
            startX = (this.width - totalWidth) / 2;
            if (startX < 16) {
                startX = 16;
            }
        }

        int framePadding = 6;
        int mapFrameHeight = this.gridHeight + 2 * framePadding;
        int leftPanelBorder = 2;

        int frameTop;
        if (colorEditorOverlay != null && layoutPreset.isVerticalButtonBar()) {
            int overlayBottom = getHorizontalOverlayBottomEdge() > 0 ? getHorizontalOverlayBottomEdge() + 6 : 135;
            int availH = (this.height - 15) - overlayBottom;
            int menuTotalHeight = layoutPreset.isBottomBar() ? (mapFrameHeight + 80) : mapFrameHeight;
            if (availH > menuTotalHeight) {
                frameTop = overlayBottom + (availH - menuTotalHeight) / 2;
            } else {
                frameTop = overlayBottom + 4;
            }
        } else {
            if (layoutPreset.isBottomBar()) {
                int requiredHeight = mapFrameHeight + 80;
                frameTop = (this.height - requiredHeight) / 2;
            } else if (layoutPreset.isTopBar()) {
                frameTop = (this.height - mapFrameHeight) / 2;
                if (frameTop < 75) {
                    frameTop = 75;
                }
            } else {
                frameTop = (this.height - mapFrameHeight) / 2;
                if (frameTop < 32) {
                    frameTop = 32;
                }
            }
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
        int closeButtonY = contentBottom + 10;
        this.footerRowY = closeButtonY;

        int buttonSpacing = 12;
        int padding = 16;
        int numButtons = TOPBOX_BUTTON_COUNT;
        this.topBoxWidth = numButtons * TOPBOX_BUTTON_WIDTH + (numButtons - 1) * buttonSpacing + padding * 2 + 80;

        if (layoutPreset.isTopBar()) {
            this.topBoxHeight = 28;
            if (overlayRight > 0) {
                int availableLeft = overlayRight;
                int availableRight = this.width - 18;
                int availableWidth = availableRight - availableLeft;
                this.topBoxX = availableLeft + (availableWidth - this.topBoxWidth) / 2;
            } else {
                this.topBoxX = (this.width - this.topBoxWidth) / 2;
            }
            this.topBoxY = 35;
        } else if (layoutPreset.isVerticalButtonBar()) {
            this.topBoxWidth = verticalTopBoxWidth;
            this.topBoxHeight = this.leftPanelHeight;
            this.topBoxY = this.leftPanelY;
        } else {
            this.topBoxHeight = 28;
            if (overlayRight > 0) {
                int availableLeft = overlayRight;
                int availableRight = this.width - 18;
                int availableWidth = availableRight - availableLeft;
                this.topBoxX = availableLeft + (availableWidth - this.topBoxWidth) / 2;
            } else {
                this.topBoxX = (this.width - this.topBoxWidth) / 2;
            }

            int desiredTopBoxY = this.footerRowY + closeButtonHeight + 18;
            int maxTopBoxY = this.height - 8 - this.topBoxHeight;
            this.topBoxY = Math.min(desiredTopBoxY, maxTopBoxY);
        }

        ClientLevel world = Minecraft.getInstance().level;
        this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE, mapRotation);
        buildTopBoxButtons();

        this.actionSearchField = null;
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

        buttonsNeedUpdate = true;
        lastScrollOffset = -1;

        openChildScreen(new ChunkMapScreen(this.data));
    }

    private void openPanelColorEditor() {
        if (clientConfig == null) {
            clientConfig = ClientConfig.load();
        }
        colorEditorOverlay = new PanelColorEditorOverlay(this, clientConfig);
        this.clearAndInit();
    }

    void closePanelColorEditor() {
        if (colorEditorOverlay != null) {
            colorEditorOverlay.close();
        }
        if (clientConfig != null) {
            clientConfig.clearColorPreviewOverrides();
        }
        colorEditorOverlay = null;
        applyActionSearchFieldStyle();
        this.clearAndInit();
    }

    boolean useVerticalPaletteLayout() {
        ChunkMapLayoutPreset preset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        return !preset.isVerticalButtonBar();
    }

    public int getVerticalOverlayRightEdge() {
        if (colorEditorOverlay != null && useVerticalPaletteLayout()) {
            return colorEditorOverlay.getCardRight();
        }
        return 0;
    }

    public int getHorizontalOverlayBottomEdge() {
        if (colorEditorOverlay != null && !useVerticalPaletteLayout()) {
            return colorEditorOverlay.getCardBottom();
        }
        return 0;
    }

    <T extends EditBox> T addOverlayWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    private void openChildScreen(Screen child) {
        retainChunkMapSession = true;
        this.minecraft.setScreen(child);
    }

    @Override
    public void removed() {
        if (colorEditorOverlay != null) {
            closePanelColorEditor();
        }
        if (!retainChunkMapSession) {
            ChunkloaderNetworking.sendCloseChunkMapToServer();
            if (this.grid != null) {
                this.grid.close();
                this.grid = null;
            }
        }

        retainChunkMapSession = false;
        super.removed();
    }

    @Override
    public void onClose() {
        if (colorEditorOverlay != null) {
            closePanelColorEditor();
            return;
        }
        if (this.grid != null) {
            this.grid.close();
        }
        super.onClose();
    }

    private void drawDimBackground(GuiGraphics context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean renderColorPreview = colorEditorOverlay != null && clientConfig != null;
        if (renderColorPreview) {
            clientConfig.setColorPreviewOverrides(colorEditorOverlay.getDraftColors());
        }

        try {
            drawDimBackground(context);

            boolean isSkinScreenPage = colorEditorOverlay != null && colorEditorOverlay.getCurrentPage() == 2;
            if (!isSkinScreenPage) {
                drawMapFrame(context);

                if (buttonsNeedUpdate || lastScrollOffset != getVisibleActionScrollOffset()) {
                    updateButtonPositions();
                    buttonsNeedUpdate = false;
                    lastScrollOffset = getVisibleActionScrollOffset();
                }

                int widgetMouseX = colorEditorOverlay != null ? -999 : mouseX;
                int widgetMouseY = colorEditorOverlay != null ? -999 : mouseY;

                if (this.grid != null) {
                    context.enableScissor(gridLeft - 2, gridTop - 2, gridLeft + gridWidth + 2, gridTop + gridHeight + 2);
                    if (colorEditorOverlay != null) {
                        context.fill(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight, 0xFF0A0D10);
                    } else {
                        grid.render(context, widgetMouseX, widgetMouseY);
                        drawChunkloaderPoints(context, widgetMouseX, widgetMouseY);
                    }
                    context.disableScissor();
                    String[] compassLabels = {"N", "W", "S", "E"};
                    String upLabel = compassLabels[mapRotation];
                    Font font = Minecraft.getInstance().font;
                    int compassX = gridLeft + gridWidth / 2 - this.font.width(upLabel) / 2;
                    int compassY = gridTop - this.font.lineHeight - 8;
                    int compassColor = toGuiFillColor(clientConfig != null ? clientConfig.getCompassDirectionColor() : 0xFFFFFFAA);
                    context.drawString(font, Component.literal(upLabel), compassX, compassY, compassColor, true);
                }

                drawTopBox(context);
                drawLeftPanel(context);
                drawSidePanel(context);

                if (actionSearchField != null) {
                    applyActionSearchFieldStyle();
                    drawActionSearchFieldChrome(context);
                    actionSearchField.setBordered(false);
                    actionSearchField.render(context, widgetMouseX, widgetMouseY, delta);
                }

                int scissorTop = actionViewportTop;
                if (actionSearchField != null) {
                    scissorTop = getActionSearchOuterBottom() + ACTION_SEARCH_LIST_GAP;
                }
                scissorTop = Math.min(scissorTop, actionViewportBottom);
                context.enableScissor(actionViewportLeft, scissorTop, actionViewportRight, actionViewportBottom);
                drawActionHeaders(context);
                for (Button button : actionButtons) {
                    if (button != resetButton) {
                        button.render(context, widgetMouseX, widgetMouseY, delta);
                    }
                }
                context.disableScissor();

                drawActionScrollbar(context);

                for (Button button : topBoxButtons) {
                    if (button != null) {
                        button.render(context, widgetMouseX, widgetMouseY, delta);
                    }
                }

                if (resetButton != null) {
                    resetButton.render(context, widgetMouseX, widgetMouseY, delta);
                }

                if (closeButton != null) {
                    closeButton.render(context, widgetMouseX, widgetMouseY, delta);
                }

                if (colorEditorOverlay == null) {
                    ChunkMapGrid.Cell hovered = grid != null ? grid.getHoveredCell(mouseX, mouseY) : null;
                    if (hovered != null) {
                        drawSimpleTooltip(context, hovered.buildTooltip(), mouseX, mouseY);
                    } else if (isMouseOverEasterEggLockedSkinButton(mouseX, mouseY)) {
                        drawSimpleTooltip(
                            context,
                            List.of(
                                Component.literal("Not available for easter"),
                                Component.literal("egg players")
                            ),
                            mouseX,
                            mouseY
                        );
                    }
                }
            }
        } finally {
            if (renderColorPreview) {
                clientConfig.clearColorPreviewOverrides();
            }
        }

        if (colorEditorOverlay != null) {
            colorEditorOverlay.render(context, mouseX, mouseY);
        }
    }

    private void renderLightweightButton(GuiGraphics context, int x1, int y1, int x2, int y2, Component label, boolean active) {
        int bg = active ? 0xFF5A5A5A : 0xFF303030;
        int fg = active ? 0xFFFFFFFF : 0xFF777777;
        context.fill(x1, y1, x2, y2, bg);
        context.fill(x1, y1, x2, y1 + 1, 0xFF202020);
        context.fill(x1, y2 - 1, x2, y2, 0xFF202020);
        context.fill(x1, y1, x1 + 1, y2, 0xFF202020);
        context.fill(x2 - 1, y1, x2, y2, 0xFF202020);
        Font f = Minecraft.getInstance().font;
        int textX = x1 + (x2 - x1 - f.width(label)) / 2;
        int textY = y1 + (y2 - y1 - f.lineHeight) / 2 + 1;
        context.drawString(f, label, textX, textY, fg, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (colorEditorOverlay != null) {
            return true;
        }

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

    private boolean isMouseOverEasterEggLockedSkinButton(int mouseX, int mouseY) {
        if (easterEggLockedSkinButton == null || colorEditorOverlay != null) {
            return false;
        }
        if (!isMouseOverActionViewport(mouseX, mouseY)) {
            return false;
        }
        return mouseX >= easterEggLockedSkinButton.getX()
            && mouseX < easterEggLockedSkinButton.getX() + easterEggLockedSkinButton.getWidth()
            && mouseY >= easterEggLockedSkinButton.getY()
            && mouseY < easterEggLockedSkinButton.getY() + easterEggLockedSkinButton.getHeight();
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

    private void drawActionScrollbar(GuiGraphics context) {
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
        int scrollbarY = scrollbarTrackTop + (int) ((double) getVisibleActionScrollOffset() / maxScroll * (availableHeight - scrollbarHeight));

        int scrollbarTrackColor = toGuiFillColor(clientConfig != null ? clientConfig.getScrollbarTrackColor() : 0x33000000);
        int scrollbarThumbColor = toGuiFillColor(clientConfig != null ? clientConfig.getScrollbarThumbColor() : 0xFFAAAAAA);
        context.fill(scrollbarX, scrollbarTrackTop, scrollbarX + scrollbarWidth, scrollbarTrackTop + availableHeight, scrollbarTrackColor);
        context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, scrollbarThumbColor);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (colorEditorOverlay != null) {
            return colorEditorOverlay.mouseClicked(click);
        }

        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();

            if (mouseX >= gridLeft && mouseX < gridLeft + gridWidth
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight) {
                mapRotation = (mapRotation + 1) % 4;
                ClientLevel world = Minecraft.getInstance().level;
                if (this.grid != null) {
                    this.grid.close();
                }
                this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE, mapRotation);
                return true;
            }

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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (colorEditorOverlay != null) {
            return colorEditorOverlay.mouseDragged(click);
        }

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
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (colorEditorOverlay != null) {
            return colorEditorOverlay.mouseReleased(click);
        }

        if (actionScrollbarDragging) {
            actionScrollbarDragging = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        if (colorEditorOverlay != null) {
            return colorEditorOverlay.keyPressed(keyInput);
        }
        return super.keyPressed(keyInput);
    }

    public boolean charTyped(net.minecraft.client.input.CharacterEvent characterEvent) {
        if (colorEditorOverlay != null) {
            return colorEditorOverlay.charTyped(characterEvent);
        }
        return super.charTyped(characterEvent);
    }

    private static int toGuiFillColor(int argb) {
        return argb;
    }

    private void drawActionHeaders(GuiGraphics context) {
        if (actionHeaderLayouts.isEmpty()) {
            return;
        }

        int contentTop = actionViewportTop;
        int buttonX = panelX + 8;
        int buttonWidth = panelWidth - 16;

        int textColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        for (ActionHeaderLayout headerLayout : actionHeaderLayouts) {
            int rowY = contentTop + headerLayout.yOffset - getVisibleActionScrollOffset();
            int textY = rowY + (headerLayout.height - this.font.lineHeight) / 2 + 1;
            if (textY + this.font.lineHeight < actionViewportTop || textY > actionViewportBottom) {
                continue;
            }

            int headerWidth = this.font.width(headerLayout.text);
            int x = buttonX + Math.max(0, (buttonWidth - headerWidth) / 2);
            context.drawString(this.font, headerLayout.text, x, textY, textColor, false);
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

    private void drawMapFrame(GuiGraphics context) {
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

        int frameColor = toGuiFillColor(clientConfig != null ? clientConfig.getFrameColor() : 0xFF111417);
        int panelColor = toGuiFillColor(clientConfig != null ? clientConfig.getPanelColor() : 0xFF2C2C2C);
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

        for (Button button : actionButtons) {
            if (button == resetButton) {
                continue;
            }
            Integer yOffset = actionButtonYOffset.get(button);
            if (yOffset == null) {
                continue;
            }
            int buttonY = contentTop + yOffset - getVisibleActionScrollOffset();
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
            layoutActionSearchField();
        }
    }

    private int getActionSearchOuterLeft() {
        return panelX + 8;
    }

    private int getActionSearchOuterTop() {
        return panelY + 8 + ACTION_SEARCH_Y_OFFSET;
    }

    private int getActionSearchOuterWidth() {
        return panelWidth - 16;
    }

    private int getActionSearchOuterRight() {
        return getActionSearchOuterLeft() + getActionSearchOuterWidth();
    }

    private int getActionSearchOuterBottom() {
        return getActionSearchOuterTop() + ACTION_SEARCH_HEIGHT;
    }

    private void layoutActionSearchField() {
        if (actionSearchField == null) {
            return;
        }
        int outerLeft = getActionSearchOuterLeft();
        int outerTop = getActionSearchOuterTop();
        int outerWidth = getActionSearchOuterWidth();
        actionSearchField.setX(outerLeft + ACTION_SEARCH_INSET_X);
        actionSearchField.setY(outerTop + ACTION_SEARCH_INSET_Y);
        actionSearchField.setWidth(Math.max(0, outerWidth - ACTION_SEARCH_INSET_X * 2));
        actionSearchField.setHeight(ACTION_SEARCH_TEXT_HEIGHT);
    }

    private void ensureActionSearchField() {
        int x = getActionSearchOuterLeft();
        int y = getActionSearchOuterTop();
        int width = getActionSearchOuterWidth();

        if (actionSearchField == null) {
            actionSearchField = new EditBox(
                this.font,
                x + ACTION_SEARCH_INSET_X,
                y + ACTION_SEARCH_INSET_Y,
                Math.max(0, width - ACTION_SEARCH_INSET_X * 2),
                ACTION_SEARCH_TEXT_HEIGHT,
                Component.literal("Search")
            );
            actionSearchField.setMaxLength(64);
            actionSearchField.setValue(actionSearchQuery);
            actionSearchField.setBordered(false);
            actionSearchField.setTextShadow(false);
            actionSearchField.setResponder(text -> {
                actionSearchQuery = text;
                buildActionButtons();
                actionScrollOffset = clampActionScrollOffset(actionScrollOffset, getMaxActionScroll());
                buttonsNeedUpdate = true;
            });
            applyActionSearchFieldStyle();
            this.addRenderableWidget(actionSearchField);
        } else {
            layoutActionSearchField();
            actionSearchField.setValue(actionSearchQuery);
            actionSearchField.setBordered(false);
            actionSearchField.setTextShadow(false);
            applyActionSearchFieldStyle();
        }
    }

    private void drawActionSearchFieldChrome(GuiGraphics context) {
        if (actionSearchField == null) {
            return;
        }
        int left = getActionSearchOuterLeft();
        int top = getActionSearchOuterTop();
        int right = getActionSearchOuterRight();
        int bottom = getActionSearchOuterBottom();
        int bg = toGuiFillColor(clientConfig != null ? clientConfig.getActionSearchBackgroundColor() : 0xFF000000);
        int border = toGuiFillColor(clientConfig != null ? clientConfig.getActionSearchBorderColor() : 0xFF4A4A4A);
        if (actionSearchField.isFocused()) {
            border = 0xFFFFFFFF;
        }
        context.fill(left, top, right, bottom, bg);
        context.fill(left, top, right, top + 1, border);
        context.fill(left, bottom - 1, right, bottom, border);
        context.fill(left, top, left + 1, bottom, border);
        context.fill(right - 1, top, right, bottom, border);
    }

    private void applyActionSearchFieldStyle() {
        if (actionSearchField == null) {
            return;
        }
        if (clientConfig == null) {
            return;
        }

        actionSearchField.setTextColor(clientConfig.getActionSearchTextColor() | 0xFF000000);
        int placeholderRgb = clientConfig.getActionSearchPlaceholderColor() & 0x00FFFFFF;
        actionSearchField.setHint(
            Component.literal("Search...").withStyle(style -> style.withColor(TextColor.fromRgb(placeholderRgb)))
        );
        actionSearchField.setVisible(true);
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

    public List<PanelColorTargetHit> collectLinkedPanelColorHits(PanelColorTarget target) {
        List<PanelColorTargetHit> hits = new ArrayList<>();
        if (target == null || target.getPage() != 1) {
            return hits;
        }

        switch (target) {
            case LEFT_PANEL_TEXT -> {
                collectActionHeaderHits(hits);
                collectInfoLabelHits(hits);
            }
            case LEFT_PANEL_VALUE -> collectInfoValueHits(hits);
            case LEFT_PANEL_STATUS -> collectInfoStatusHits(hits);
            case LEFT_PANEL_DIM -> collectInfoDimHits(hits);
            case LEFT_PANEL_NAME -> collectInfoNameHits(hits);
            case DIVIDER -> collectDividerHits(hits);
            case PANEL -> {
                addPanelHits(hits, PanelColorTarget.PANEL);
                collectMapFramePanelHit(hits);
            }
            case BORDER -> addPanelHits(hits, PanelColorTarget.BORDER);
            case FRAME -> collectMapFrameHit(hits);
            case SCROLLBAR_TRACK, SCROLLBAR_THUMB -> collectScrollbarHits(hits, target);
            case SEARCHBAR_BACKGROUND, SEARCHBAR_BORDER, SEARCHBAR_TEXT, SEARCHBAR_PLACEHOLDER ->
                collectSearchbarHits(hits, target);
            case COMPASS_DIRECTION -> collectCompassHit(hits);
            default -> {
            }
        }
        return hits;
    }

    private void collectActionHeaderHits(List<PanelColorTargetHit> hits) {
        int buttonX = panelX + 8;
        int buttonWidth = panelWidth - 16;
        for (ActionHeaderLayout headerLayout : actionHeaderLayouts) {
            int rowY = actionViewportTop + headerLayout.yOffset - getVisibleActionScrollOffset();
            int textY = rowY + (headerLayout.height - this.font.lineHeight) / 2 + 1;
            int textWidth = this.font.width(headerLayout.text);
            int textX = buttonX + Math.max(0, (buttonWidth - textWidth) / 2);
            int textTop = textY - 1;
            int textBottom = textY + this.font.lineHeight + 1;
            if (textBottom < actionViewportTop || textTop > actionViewportBottom) {
                continue;
            }
            hits.add(hit(
                PanelColorTarget.LEFT_PANEL_TEXT,
                textX - 2,
                textTop - 1,
                textX + textWidth + 1,
                textBottom - 1
            ));
        }
    }

    private void collectInfoLabelHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout == null) {
            return;
        }
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_TEXT, "Status:", leftPanelX + layout.padding, layout.infoY);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_TEXT, "Dim:", leftPanelX + layout.padding, layout.dimY);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_TEXT, "Chunk:", leftPanelX + layout.padding, layout.chunkY);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_TEXT, "Block:", leftPanelX + layout.padding, layout.blockY);
        String radiusLabel = data.allowMobSpawning() ? "SD:" : "Radius:";
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_TEXT, radiusLabel, leftPanelX + layout.padding, layout.sdY);
    }

    private void collectInfoValueHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout == null) {
            return;
        }
        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, "X:" + data.centerChunkX(), layout.coordStartX, layout.chunkY);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, "Z:" + data.centerChunkZ(), layout.coordStartX, layout.chunkY + 12);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, "X:" + blockPos.getX(), layout.coordStartX, layout.blockY);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, "Y:" + data.blockY(), layout.coordStartX, layout.blockY + 12);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, "Z:" + blockPos.getZ(), layout.coordStartX, layout.blockY + 24);
        addTextHit(hits, PanelColorTarget.LEFT_PANEL_VALUE, data.chunkRadius() + "/3", layout.coordStartX, layout.sdY);
    }

    private void collectInfoStatusHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout == null) {
            return;
        }
        String statusText = data.enabled() ? "active" : "inactive";
        int statusTextWidth = this.font.width(statusText);
        addTextHit(
            hits,
            PanelColorTarget.LEFT_PANEL_STATUS,
            statusText,
            leftPanelX + leftPanelWidth - layout.padding - statusTextWidth,
            layout.infoY
        );
    }

    private void collectInfoDimHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout == null) {
            return;
        }
        String dimName = data.dimensionKey().toLowerCase();
        String dimText = dimName.contains("overworld") ? "Overworld"
            : (dimName.contains("nether") ? "Nether" : (dimName.contains("end") ? "End" : "?"));
        int dimTextWidth = this.font.width(dimText);
        addTextHit(
            hits,
            PanelColorTarget.LEFT_PANEL_DIM,
            dimText,
            leftPanelX + leftPanelWidth - layout.padding - dimTextWidth,
            layout.dimY
        );
    }

    private void collectInfoNameHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout == null || layout.ownerName == null || layout.ownerName.isEmpty()) {
            return;
        }
        int nameWidth = this.font.width(layout.ownerName);
        int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
        hits.add(hit(
            PanelColorTarget.LEFT_PANEL_NAME,
            nameX - 2,
            layout.nameY - 2,
            nameX + nameWidth + 1,
            layout.nameY + this.font.lineHeight
        ));
    }

    private void collectDividerHits(List<PanelColorTargetHit> hits) {
        InfoLayout layout = getInfoLayout();
        if (layout != null) {
            int padding = layout.padding;
            int div1Y = layout.initialInfoY - 4;
            int div2Y = layout.initialInfoY + 26;
            int div3Y = layout.initialInfoY + 98;
            hits.add(hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div1Y, leftPanelX + leftPanelWidth - padding, div1Y + 1));
            hits.add(hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div2Y, leftPanelX + leftPanelWidth - padding, div2Y + 1));
            hits.add(hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div3Y, leftPanelX + leftPanelWidth - padding, div3Y + 1));
        }

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean verticalButtonBar = layoutPreset.isVerticalButtonBar();
        int numberOfTopButtons = topBoxButtons.size();
        if (!verticalButtonBar && numberOfTopButtons >= 2) {
            int totalButtonsWidth = numberOfTopButtons * TOPBOX_BUTTON_WIDTH;
            int spacing = (topBoxWidth - totalButtonsWidth) / (numberOfTopButtons + 1);
            int startX = topBoxX + spacing;
            int lineTop = topBoxY + 4;
            int lineBottom = topBoxY + topBoxHeight - 4;
            for (int index = 1; index < numberOfTopButtons; index++) {
                int lineX = startX + TOPBOX_BUTTON_WIDTH * index + spacing * (index - 1) + spacing / 2;
                hits.add(hit(PanelColorTarget.DIVIDER, lineX, lineTop, lineX + 1, lineBottom));
            }
        } else if (verticalButtonBar && numberOfTopButtons >= 2) {
            int availableSpace = topBoxHeight - (numberOfTopButtons * TOPBOX_BUTTON_HEIGHT);
            int innerSpacing = Math.max(2, availableSpace > 0 ? availableSpace / (numberOfTopButtons + 1) : 2);
            int startY = topBoxY + innerSpacing;
            int lineLeft = topBoxX + 4;
            int lineRight = topBoxX + topBoxWidth - 4;
            for (int index = 1; index < numberOfTopButtons; index++) {
                int lineY = startY + TOPBOX_BUTTON_HEIGHT * index + innerSpacing * (index - 1) + innerSpacing / 2;
                hits.add(hit(PanelColorTarget.DIVIDER, lineLeft, lineY, lineRight, lineY + 1));
            }
        }
    }

    private void addPanelHits(List<PanelColorTargetHit> hits, PanelColorTarget target) {
        addSinglePanelHit(hits, target, topBoxX, topBoxY, topBoxWidth, topBoxHeight);
        addSinglePanelHit(hits, target, leftPanelX, leftPanelY, leftPanelWidth, leftPanelHeight);
        addSinglePanelHit(hits, target, panelX, panelY, panelWidth, panelHeight);
    }

    private void addSinglePanelHit(
        List<PanelColorTargetHit> hits,
        PanelColorTarget target,
        int panelLeft,
        int panelTop,
        int width,
        int height
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int outerLeft = panelLeft - 2;
        int outerTop = panelTop - 2;
        int outerRight = panelLeft + width + 2;
        int outerBottom = panelTop + height + 2;
        if (target == PanelColorTarget.BORDER) {
            hits.add(hit(PanelColorTarget.BORDER, outerLeft, outerTop, outerRight, outerBottom));
        } else {
            hits.add(hit(PanelColorTarget.PANEL, panelLeft - 1, panelTop - 1, panelLeft + width + 1, panelTop + height + 1));
        }
    }

    private void collectMapFramePanelHit(List<PanelColorTargetHit> hits) {
        if (this.grid == null) {
            return;
        }
        int borderThickness = 4;
        hits.add(hit(
            PanelColorTarget.PANEL,
            gridLeft - borderThickness,
            gridTop - borderThickness,
            gridLeft + gridWidth + borderThickness,
            gridTop + gridHeight + borderThickness
        ));
    }

    private void collectMapFrameHit(List<PanelColorTargetHit> hits) {
        if (this.grid == null) {
            return;
        }
        int framePadding = 6;
        hits.add(hit(
            PanelColorTarget.FRAME,
            gridLeft - framePadding,
            gridTop - framePadding,
            gridLeft + gridWidth + framePadding,
            gridTop + gridHeight + framePadding
        ));
    }

    private void collectScrollbarHits(List<PanelColorTargetHit> hits, PanelColorTarget target) {
        ScrollbarMetrics metrics = getActionScrollbarMetrics();
        if (metrics == null) {
            return;
        }
        if (target == PanelColorTarget.SCROLLBAR_THUMB) {
            hits.add(hit(
                PanelColorTarget.SCROLLBAR_THUMB,
                metrics.x,
                metrics.thumbY,
                metrics.x + metrics.width,
                metrics.thumbY + metrics.thumbHeight
            ));
        } else {
            hits.add(hit(
                PanelColorTarget.SCROLLBAR_TRACK,
                metrics.x,
                metrics.trackTop,
                metrics.x + metrics.width,
                metrics.trackTop + metrics.trackHeight
            ));
        }
    }

    private void collectSearchbarHits(List<PanelColorTargetHit> hits, PanelColorTarget target) {
        if (actionSearchField == null) {
            return;
        }
        int left = getActionSearchOuterLeft();
        int top = getActionSearchOuterTop();
        int right = getActionSearchOuterRight();
        int bottom = getActionSearchOuterBottom();
        switch (target) {
            case SEARCHBAR_BORDER -> hits.add(hit(PanelColorTarget.SEARCHBAR_BORDER, left, top, right, bottom));
            case SEARCHBAR_BACKGROUND -> hits.add(hit(PanelColorTarget.SEARCHBAR_BACKGROUND, left + 1, top + 1, right - 1, bottom - 1));
            case SEARCHBAR_TEXT -> {
                int textLeft = left + ACTION_SEARCH_INSET_X;
                int textTop = top + ACTION_SEARCH_INSET_Y + Math.max(0, (ACTION_SEARCH_TEXT_HEIGHT - this.font.lineHeight) / 2);
                String query = actionSearchQuery == null ? "" : actionSearchQuery;
                if (!query.isBlank()) {
                    hits.add(hit(
                        PanelColorTarget.SEARCHBAR_TEXT,
                        textLeft,
                        textTop - 1,
                        textLeft + this.font.width(query) + 3,
                        textTop + this.font.lineHeight + 1
                    ));
                } else {
                    hits.add(hit(PanelColorTarget.SEARCHBAR_TEXT, left + (right - left) / 2, top + 1, right - 1, bottom - 1));
                }
            }
            case SEARCHBAR_PLACEHOLDER -> {
                String placeholder = "Search...";
                int textLeft = left + ACTION_SEARCH_INSET_X;
                int textTop = top + ACTION_SEARCH_INSET_Y + Math.max(0, (ACTION_SEARCH_TEXT_HEIGHT - this.font.lineHeight) / 2);
                hits.add(hit(
                    PanelColorTarget.SEARCHBAR_PLACEHOLDER,
                    textLeft,
                    textTop - 1,
                    textLeft + this.font.width(placeholder) + 3,
                    textTop + this.font.lineHeight + 1
                ));
            }
            default -> {
            }
        }
    }

    private void collectCompassHit(List<PanelColorTargetHit> hits) {
        if (this.grid == null) {
            return;
        }
        String[] compassLabels = {"N", "W", "S", "E"};
        String upLabel = compassLabels[mapRotation];
        int compassX = gridLeft + gridWidth / 2 - this.font.width(upLabel) / 2;
        int compassY = gridTop - this.font.lineHeight - 8;
        hits.add(hit(
            PanelColorTarget.COMPASS_DIRECTION,
            compassX - 3,
            compassY - 2,
            compassX + this.font.width(upLabel) + 3,
            compassY + this.font.lineHeight + 2
        ));
    }

    private void addTextHit(List<PanelColorTargetHit> hits, PanelColorTarget target, String text, int x, int y) {
        hits.add(hit(target, x - 2, y - 2, x + this.font.width(text) + 1, y + this.font.lineHeight));
    }

    private InfoLayout getInfoLayout() {
        if (data == null) {
            return null;
        }
        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        String ownerName = data.ownerName() != null ? data.ownerName() : null;
        int nameY = 0;
        if (ownerName != null && !ownerName.isEmpty()) {
            nameY = headY + headSize + 4;
        }
        int initialInfoY = nameY > 0 ? nameY + this.font.lineHeight + 8 : headY + headSize + 8;
        int infoY = initialInfoY + 2;
        int dimY = infoY + 12;
        int chunkY = dimY + 24 - 3;
        int blockY = chunkY + 24;
        int blockZY = blockY + 24;
        int sdY = blockZY + 15 + 8;
        int maxLabelWidth = Math.max(this.font.width("Chunk:"), this.font.width("Block:"));
        int coordStartX = leftPanelX + padding + maxLabelWidth + 4;

        return new InfoLayout(
            padding,
            ownerName,
            nameY,
            initialInfoY,
            infoY,
            dimY,
            chunkY,
            blockY,
            sdY,
            coordStartX
        );
    }

    private record InfoLayout(
        int padding,
        String ownerName,
        int nameY,
        int initialInfoY,
        int infoY,
        int dimY,
        int chunkY,
        int blockY,
        int sdY,
        int coordStartX
    ) {
    }

    public PanelColorTargetHit getPanelColorTargetHit(int mouseX, int mouseY) {
        if (contains(mouseX, mouseY, gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)) {
            return null;
        }

        for (Button button : topBoxButtons) {
            if (button != null && contains(mouseX, mouseY, button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight())) {
                return null;
            }
        }

        for (Button button : actionButtons) {
            if (button != null && button.active && contains(mouseX, mouseY, button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight())) {
                return null;
            }
        }

        if (closeButton != null && contains(mouseX, mouseY, closeButton.getX(), closeButton.getY(), closeButton.getX() + closeButton.getWidth(), closeButton.getY() + closeButton.getHeight())) {
            return null;
        }
        if (resetButton != null && contains(mouseX, mouseY, resetButton.getX(), resetButton.getY(), resetButton.getX() + resetButton.getWidth(), resetButton.getY() + resetButton.getHeight())) {
            return null;
        }

        PanelColorTargetHit hit = getCompassDirectionColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getActionSearchColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getActionHeaderColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getScrollbarColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getInfoTextColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getDividerColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getMapFrameColorHit(mouseX, mouseY);
        if (hit != null) {
            return hit;
        }

        hit = getPanelColorHit(mouseX, mouseY, topBoxX, topBoxY, topBoxWidth, topBoxHeight);
        if (hit != null) {
            return hit;
        }

        hit = getPanelColorHit(mouseX, mouseY, leftPanelX, leftPanelY, leftPanelWidth, leftPanelHeight);
        if (hit != null) {
            return hit;
        }

        return getPanelColorHit(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight);
    }

    private PanelColorTargetHit getCompassDirectionColorHit(int mouseX, int mouseY) {
        if (this.grid == null) {
            return null;
        }
        String[] compassLabels = {"N", "W", "S", "E"};
        String upLabel = compassLabels[mapRotation];
        Font font = Minecraft.getInstance().font;
        int compassX = gridLeft + gridWidth / 2 - this.font.width(upLabel) / 2;
        int compassY = gridTop - this.font.lineHeight - 8;
        return hitIfContains(
            PanelColorTarget.COMPASS_DIRECTION,
            mouseX,
            mouseY,
            compassX - 3,
            compassY - 2,
            compassX + this.font.width(upLabel) + 3,
            compassY + this.font.lineHeight + 2
        );
    }

    private PanelColorTargetHit getActionSearchColorHit(int mouseX, int mouseY) {
        if (actionSearchField == null) {
            return null;
        }

        int left = getActionSearchOuterLeft();
        int top = getActionSearchOuterTop();
        int right = getActionSearchOuterRight();
        int bottom = getActionSearchOuterBottom();
        if (!contains(mouseX, mouseY, left, top, right, bottom)) {
            return null;
        }

        if (mouseX < left + 2 || mouseX >= right - 2 || mouseY < top + 2 || mouseY >= bottom - 2) {
            return hit(PanelColorTarget.SEARCHBAR_BORDER, left, top, right, bottom);
        }

        int textLeft = left + ACTION_SEARCH_INSET_X;
        int textTop = top + ACTION_SEARCH_INSET_Y + Math.max(0, (ACTION_SEARCH_TEXT_HEIGHT - this.font.lineHeight) / 2);
        String query = actionSearchQuery == null ? "" : actionSearchQuery;
        if (!query.isBlank()) {
            PanelColorTargetHit textHit = hitIfContains(
                PanelColorTarget.SEARCHBAR_TEXT,
                mouseX,
                mouseY,
                textLeft,
                textTop - 1,
                textLeft + this.font.width(query) + 3,
                textTop + this.font.lineHeight + 1
            );
            if (textHit != null) {
                return textHit;
            }
        } else {
            String placeholder = "Search...";
            PanelColorTargetHit placeholderHit = hitIfContains(
                PanelColorTarget.SEARCHBAR_PLACEHOLDER,
                mouseX,
                mouseY,
                textLeft,
                textTop - 1,
                textLeft + this.font.width(placeholder) + 3,
                textTop + this.font.lineHeight + 1
            );
            if (placeholderHit != null) {
                return placeholderHit;
            }
            if (mouseX >= left + (right - left) / 2) {
                return hit(PanelColorTarget.SEARCHBAR_TEXT, left + (right - left) / 2, top + 1, right - 1, bottom - 1);
            }
        }

        return hit(PanelColorTarget.SEARCHBAR_BACKGROUND, left + 1, top + 1, right - 1, bottom - 1);
    }

    private PanelColorTargetHit getActionHeaderColorHit(int mouseX, int mouseY) {
        int buttonX = panelX + 8;
        int buttonWidth = panelWidth - 16;
        for (ActionHeaderLayout headerLayout : actionHeaderLayouts) {
            int rowY = actionViewportTop + headerLayout.yOffset - getVisibleActionScrollOffset();
            int textY = rowY + (headerLayout.height - this.font.lineHeight) / 2 + 1;
            int textWidth = this.font.width(headerLayout.text);
            int textX = buttonX + Math.max(0, (buttonWidth - textWidth) / 2);

            int textTop = textY - 1;
            int textBottom = textY + this.font.lineHeight + 1;
            if (textBottom < actionViewportTop || textTop > actionViewportBottom) {
                continue;
            }

            PanelColorTargetHit hit = hitIfContains(
                PanelColorTarget.LEFT_PANEL_TEXT,
                mouseX,
                mouseY,
                textX - 2,
                textTop - 1,
                textX + textWidth + 1,
                textBottom - 1
            );
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private PanelColorTargetHit getScrollbarColorHit(int mouseX, int mouseY) {
        ScrollbarMetrics metrics = getActionScrollbarMetrics();
        if (metrics == null) {
            return null;
        }

        PanelColorTargetHit thumbHit = hitIfContains(
            PanelColorTarget.SCROLLBAR_THUMB,
            mouseX,
            mouseY,
            metrics.x,
            metrics.thumbY,
            metrics.x + metrics.width,
            metrics.thumbY + metrics.thumbHeight
        );
        if (thumbHit != null) {
            return thumbHit;
        }

        return hitIfContains(
            PanelColorTarget.SCROLLBAR_TRACK,
            mouseX,
            mouseY,
            metrics.x,
            metrics.trackTop,
            metrics.x + metrics.width,
            metrics.trackTop + metrics.trackHeight
        );
    }

    private PanelColorTargetHit getInfoTextColorHit(int mouseX, int mouseY) {
        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        String ownerName = data != null && data.ownerName() != null ? data.ownerName() : null;
        int nameY = 0;

        if (ownerName != null && !ownerName.isEmpty()) {
            int nameWidth = this.font.width(ownerName);
            int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
            nameY = headY + headSize + 4;
            PanelColorTargetHit nameHit = hitIfContains(
                PanelColorTarget.LEFT_PANEL_NAME,
                mouseX,
                mouseY,
                nameX - 2,
                nameY - 2,
                nameX + nameWidth + 1,
                nameY + this.font.lineHeight
            );
            if (nameHit != null) {
                return nameHit;
            }
        }

        if (data == null) {
            return null;
        }

        int infoY = nameY > 0 ? nameY + this.font.lineHeight + 8 : headY + headSize + 8;
        infoY += 2;

        PanelColorTargetHit hit = hitText(PanelColorTarget.LEFT_PANEL_TEXT, mouseX, mouseY, "Status:", leftPanelX + padding, infoY);
        if (hit != null) {
            return hit;
        }
        String statusText = data.enabled() ? "active" : "inactive";
        int statusTextWidth = this.font.width(statusText);
        hit = hitText(PanelColorTarget.LEFT_PANEL_STATUS, mouseX, mouseY, statusText, leftPanelX + leftPanelWidth - padding - statusTextWidth, infoY);
        if (hit != null) {
            return hit;
        }
        infoY += 12;

        hit = hitText(PanelColorTarget.LEFT_PANEL_TEXT, mouseX, mouseY, "Dim:", leftPanelX + padding, infoY);
        if (hit != null) {
            return hit;
        }
        String dimName = data.dimensionKey().toLowerCase();
        String dimText = dimName.contains("overworld") ? "Overworld" : (dimName.contains("nether") ? "Nether" : (dimName.contains("end") ? "End" : "?"));
        int dimTextWidth = this.font.width(dimText);
        hit = hitText(PanelColorTarget.LEFT_PANEL_DIM, mouseX, mouseY, dimText, leftPanelX + leftPanelWidth - padding - dimTextWidth, infoY);
        if (hit != null) {
            return hit;
        }
        infoY += 24;

        String chunkLabel = "Chunk:";
        String blockLabel = "Block:";
        int maxLabelWidth = Math.max(this.font.width(chunkLabel), this.font.width(blockLabel));
        int coordStartX = leftPanelX + padding + maxLabelWidth + 4;
        int chunkBlockY = infoY - 3;

        hit = hitText(PanelColorTarget.LEFT_PANEL_TEXT, mouseX, mouseY, chunkLabel, leftPanelX + padding, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        hit = hitText(PanelColorTarget.LEFT_PANEL_VALUE, mouseX, mouseY, "X:" + data.centerChunkX(), coordStartX, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        chunkBlockY += 12;
        hit = hitText(PanelColorTarget.LEFT_PANEL_VALUE, mouseX, mouseY, "Z:" + data.centerChunkZ(), coordStartX, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        chunkBlockY += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        hit = hitText(PanelColorTarget.LEFT_PANEL_TEXT, mouseX, mouseY, blockLabel, leftPanelX + padding, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        hit = hitText(PanelColorTarget.LEFT_PANEL_VALUE, mouseX, mouseY, "X:" + blockPos.getX(), coordStartX, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        chunkBlockY += 12;
        hit = hitText(PanelColorTarget.LEFT_PANEL_VALUE, mouseX, mouseY, "Y:" + data.blockY(), coordStartX, chunkBlockY);
        if (hit != null) {
            return hit;
        }
        chunkBlockY += 12;
        hit = hitText(PanelColorTarget.LEFT_PANEL_VALUE, mouseX, mouseY, "Z:" + blockPos.getZ(), coordStartX, chunkBlockY);
        if (hit != null) {
            return hit;
        }

        int sdDividerY = chunkBlockY + 15;
        int sdY = sdDividerY + 8;
        String radiusLabel = data.allowMobSpawning() ? "SD:" : "Radius:";
        hit = hitText(PanelColorTarget.LEFT_PANEL_TEXT, mouseX, mouseY, radiusLabel, leftPanelX + padding, sdY);
        if (hit != null) {
            return hit;
        }
        return hitText(
            PanelColorTarget.LEFT_PANEL_VALUE,
            mouseX,
            mouseY,
            data.chunkRadius() + "/3",
            coordStartX,
            sdY
        );
    }

    private PanelColorTargetHit getDividerColorHit(int mouseX, int mouseY) {
        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        String ownerName = data != null && data.ownerName() != null ? data.ownerName() : null;
        int nameY = ownerName != null && !ownerName.isEmpty() ? headY + headSize + 4 : 0;
        int initialInfoY = nameY > 0 ? nameY + this.font.lineHeight + 8 : headY + headSize + 8;

        if (data == null) {
            return null;
        }

        int div1Y = initialInfoY - 4;
        int div2Y = initialInfoY + 26;
        int div3Y = initialInfoY + 98;

        if (contains(mouseX, mouseY, leftPanelX + padding, div1Y - 3, leftPanelX + leftPanelWidth - padding, div1Y + 4)) {
            return hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div1Y, leftPanelX + leftPanelWidth - padding, div1Y + 1);
        }

        if (contains(mouseX, mouseY, leftPanelX + padding, div2Y - 3, leftPanelX + leftPanelWidth - padding, div2Y + 4)) {
            return hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div2Y, leftPanelX + leftPanelWidth - padding, div2Y + 1);
        }

        if (contains(mouseX, mouseY, leftPanelX + padding, div3Y - 3, leftPanelX + leftPanelWidth - padding, div3Y + 4)) {
            return hit(PanelColorTarget.DIVIDER, leftPanelX + padding, div3Y, leftPanelX + leftPanelWidth - padding, div3Y + 1);
        }

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean verticalButtonBar = layoutPreset.isVerticalButtonBar();
        int numberOfTopButtons = topBoxButtons.size();
        if (!verticalButtonBar && numberOfTopButtons >= 2) {
            int totalButtonsWidth = numberOfTopButtons * TOPBOX_BUTTON_WIDTH;
            int spacing = (topBoxWidth - totalButtonsWidth) / (numberOfTopButtons + 1);
            int startX = topBoxX + spacing;
            int lineTop = topBoxY + 4;
            int lineBottom = topBoxY + topBoxHeight - 4;
            for (int index = 1; index < numberOfTopButtons; index++) {
                int lineX = startX + TOPBOX_BUTTON_WIDTH * index + spacing * (index - 1) + spacing / 2;
                if (contains(mouseX, mouseY, lineX - 3, lineTop, lineX + 4, lineBottom)) {
                    return hit(PanelColorTarget.DIVIDER, lineX, lineTop, lineX + 1, lineBottom);
                }
            }
        } else if (verticalButtonBar && numberOfTopButtons >= 2) {
            int availableSpace = topBoxHeight - (numberOfTopButtons * TOPBOX_BUTTON_HEIGHT);
            int innerSpacing = Math.max(2, availableSpace > 0 ? availableSpace / (numberOfTopButtons + 1) : 2);
            int startY = topBoxY + innerSpacing;
            int lineLeft = topBoxX + 4;
            int lineRight = topBoxX + topBoxWidth - 4;
            for (int index = 1; index < numberOfTopButtons; index++) {
                int lineY = startY + TOPBOX_BUTTON_HEIGHT * index + innerSpacing * (index - 1) + innerSpacing / 2;
                if (contains(mouseX, mouseY, lineLeft, lineY - 3, lineRight, lineY + 4)) {
                    return hit(PanelColorTarget.DIVIDER, lineLeft, lineY, lineRight, lineY + 1);
                }
            }
        }
        return null;
    }

    private PanelColorTargetHit getMapFrameColorHit(int mouseX, int mouseY) {
        int framePadding = 6;
        int borderThickness = 4;
        int frameLeft = gridLeft - framePadding;
        int frameTop = gridTop - framePadding;
        int frameRight = gridLeft + gridWidth + framePadding;
        int frameBottom = gridTop + gridHeight + framePadding;
        if (!contains(mouseX, mouseY, frameLeft - 1, frameTop - 1, frameRight + 1, frameBottom + 1)) {
            return null;
        }

        if (contains(mouseX, mouseY, gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)) {
            return null;
        }

        if (contains(
            mouseX,
            mouseY,
            gridLeft - borderThickness,
            gridTop - borderThickness,
            gridLeft + gridWidth + borderThickness,
            gridTop + gridHeight + borderThickness
        )) {
            return hit(
                PanelColorTarget.PANEL,
                gridLeft - borderThickness,
                gridTop - borderThickness,
                gridLeft + gridWidth + borderThickness,
                gridTop + gridHeight + borderThickness
            );
        }

        return hit(PanelColorTarget.FRAME, frameLeft, frameTop, frameRight, frameBottom);
    }

    private PanelColorTargetHit getPanelColorHit(int mouseX, int mouseY, int panelLeft, int panelTop, int width, int height) {
        int outerLeft = panelLeft - 2;
        int outerTop = panelTop - 2;
        int outerRight = panelLeft + width + 2;
        int outerBottom = panelTop + height + 2;
        if (!contains(mouseX, mouseY, outerLeft, outerTop, outerRight, outerBottom)) {
            return null;
        }

        if (
            mouseX < panelLeft - 1
                || mouseX >= panelLeft + width + 1
                || mouseY < panelTop - 1
                || mouseY >= panelTop + height + 1
        ) {
            return hit(PanelColorTarget.BORDER, outerLeft, outerTop, outerRight, outerBottom);
        }
        return hit(PanelColorTarget.PANEL, panelLeft - 1, panelTop - 1, panelLeft + width + 1, panelTop + height + 1);
    }

    private PanelColorTargetHit hitText(PanelColorTarget target, int mouseX, int mouseY, String text, int x, int y) {
        return hitIfContains(target, mouseX, mouseY, x - 2, y - 2, x + this.font.width(text) + 1, y + this.font.lineHeight);
    }

    private static PanelColorTargetHit hitIfContains(
        PanelColorTarget target,
        int mouseX,
        int mouseY,
        int left,
        int top,
        int right,
        int bottom
    ) {
        return contains(mouseX, mouseY, left, top, right, bottom) ? hit(target, left, top, right, bottom) : null;
    }

    private static PanelColorTargetHit hit(PanelColorTarget target, int left, int top, int right, int bottom) {
        return new PanelColorTargetHit(target, left, top, right, bottom);
    }

    private static boolean contains(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private void drawTopBox(GuiGraphics context) {
        int panelColor = toGuiFillColor(clientConfig != null ? clientConfig.getPanelColor() : 0xFF2C2C2C);
        int borderColor = toGuiFillColor(clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, panelColor);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY - 1, borderColor);
        context.fill(topBoxX - 2, topBoxY + topBoxHeight + 1, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX - 1, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX + topBoxWidth + 1, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);

        int dividerColor = toGuiFillColor(clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF);
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

    private void drawLeftPanel(GuiGraphics context) {
        int panelColor = toGuiFillColor(clientConfig != null ? clientConfig.getPanelColor() : 0xFF2C2C2C);
        int borderColor = toGuiFillColor(clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A);
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

            Component nameText = Component.literal(ownerName);
                int nameWidth = this.font.width(nameText);
                int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
                nameY = headY + headSize + 4;
                context.drawString(this.font, nameText, nameX, nameY, leftPanelNameColor, false);
            } else {
            nameY = 0;
        }

        if (data == null) {
            return;
        }

        int infoY = nameY > 0 ? nameY + this.font.lineHeight + 8 : headY + headSize + 8;

        int dividerColor = toGuiFillColor(clientConfig != null ? clientConfig.getDividerColor() : 0x33FFFFFF);
        context.fill(leftPanelX + padding, infoY - 4, leftPanelX + leftPanelWidth - padding, infoY - 3, dividerColor);
        infoY += 2;

        int leftPanelTextColor = clientConfig != null ? clientConfig.getLeftPanelTextColor() : 0xCC808080;
        int leftPanelValueColor = clientConfig != null ? clientConfig.getLeftPanelValueColor() : 0xFFFFFFFF;
        int leftPanelStatusColor = clientConfig != null ? clientConfig.getLeftPanelStatusColor() : 0;
        int leftPanelDimColor = clientConfig != null ? clientConfig.getLeftPanelDimColor() : 0;

        context.drawString(this.font, LABEL_STATUS, leftPanelX + padding, infoY, leftPanelTextColor, false);
        String statusText = data.enabled() ? "active" : "inactive";
        int defaultStatusColor = data.enabled() ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) : 0xFF5555;
        int finalStatusColor = (leftPanelStatusColor != 0 && leftPanelStatusColor != 0xFF55FF55) ? leftPanelStatusColor : (defaultStatusColor | 0xFF000000);
        int statusTextWidth = this.font.width(statusText);
        context.drawString(this.font, Component.literal(statusText),
            leftPanelX + leftPanelWidth - padding - statusTextWidth, infoY, finalStatusColor, false);
        infoY += 12;

        context.drawString(this.font, LABEL_DIM,
            leftPanelX + padding, infoY, leftPanelTextColor, false);
        String dimName = data.dimensionKey().toLowerCase();
        String dimText;
        int defaultDimColor;
        if (dimName.contains("overworld")) {
            dimText = "Overworld";
            defaultDimColor = 0x55FF55;
        } else if (dimName.contains("nether")) {
            dimText = "Nether";
            defaultDimColor = 0xFF5555;
        } else if (dimName.contains("end")) {
            dimText = "End";
            defaultDimColor = 0xFF55FF;
        } else {
            dimText = "?";
            defaultDimColor = 0xAAAAAA;
        }
        int finalDimColor = (leftPanelDimColor != 0 && leftPanelDimColor != 0xFF55FF55) ? leftPanelDimColor : (defaultDimColor | 0xFF000000);
        int dimTextWidth = this.font.width(dimText);
        context.drawString(this.font, Component.literal(dimText),
            leftPanelX + leftPanelWidth - padding - dimTextWidth, infoY, finalDimColor, false);
        infoY += 12;

        context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
        infoY += 12;

        Component chunkLabel = LABEL_CHUNK;
        Component blockLabel = LABEL_BLOCK;
        int chunkLabelWidth = this.font.width(chunkLabel);
        int blockLabelWidth = this.font.width(blockLabel);
        int maxLabelWidth = Math.max(chunkLabelWidth, blockLabelWidth);
        int coordStartX = leftPanelX + padding + maxLabelWidth + 4;

        int chunkBlockY = infoY - 3;
        context.drawString(this.font, chunkLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);

        String chunkXStr = "X:" + data.centerChunkX();
        String chunkZStr = "Z:" + data.centerChunkZ();

        context.drawString(this.font, Component.literal(chunkXStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawString(this.font, Component.literal(chunkZStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);

        context.drawString(this.font, blockLabel, leftPanelX + padding, chunkBlockY, leftPanelTextColor, false);

        String xStr = "X:" + blockPos.getX();
        String yStr = "Y:" + data.blockY();
        String zStr = "Z:" + blockPos.getZ();

        context.drawString(this.font, Component.literal(xStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawString(this.font, Component.literal(yStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        chunkBlockY += 12;
        context.drawString(this.font, Component.literal(zStr), coordStartX, chunkBlockY, leftPanelValueColor, false);
        int originalInfoY = chunkBlockY + 12 + 3;
        infoY = originalInfoY;

            context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, dividerColor);
            int sdDividerY = infoY;
            infoY += 12;

            String radiusValue = String.valueOf(data.chunkRadius());
            String radiusSeparator = "/3";
        Component radiusLabel = data.allowMobSpawning() ? LABEL_SD : LABEL_RADIUS;
        int sdY = sdDividerY + 8;
        context.drawString(this.font, radiusLabel,
                leftPanelX + padding, sdY, leftPanelTextColor, false);
            context.drawString(this.font, Component.literal(radiusValue),
                coordStartX, sdY, leftPanelValueColor, false);
            int radiusValueWidth = this.font.width(radiusValue);
            context.drawString(this.font, Component.literal(radiusSeparator),
                coordStartX + radiusValueWidth, sdY, leftPanelValueColor, false);
            infoY = sdY + 12;
    }

    private void drawSidePanel(GuiGraphics context) {
        int panelColor = toGuiFillColor(clientConfig != null ? clientConfig.getPanelColor() : 0xFF2C2C2C);
        int borderColor = toGuiFillColor(clientConfig != null ? clientConfig.getBorderColor() : 0xFF4A4A4A);
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, panelColor);
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY - 1, borderColor);
        context.fill(panelX - 2, panelY + panelHeight + 1, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);
        context.fill(panelX - 2, panelY - 2, panelX - 1, panelY + panelHeight + 2, borderColor);
        context.fill(panelX + panelWidth + 1, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);

        Font renderer = this.font;
        int padding = 8;
        int y = panelY + padding + 4;

        MutableComponent title = Component.literal("Player: " + data.displayName());
        context.drawString(renderer, title.withStyle(net.minecraft.ChatFormatting.BOLD), panelX + padding, y, 0xFFFFFF, false);
        y += 16;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawString(renderer, Component.literal("Status:").withStyle(net.minecraft.ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        Component status = data.enabled()
            ? Component.literal("active")
            : Component.literal("inactive");
        int statusColor = data.enabled()
            ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF)
            : 0xFF5555;
        context.drawString(renderer, status, panelX + padding + 50, y, statusColor, false);
        y += 12;

        context.drawString(renderer, Component.literal("Mode:").withStyle(net.minecraft.ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        Component mode = data.allowMobSpawning()
            ? Component.literal("Fakeplayer (mob spawning)")
            : Component.literal("Chunkplayer");
        int modeColor = data.allowMobSpawning() ? 0x55FF55 : 0x79C0FF;
        context.drawString(renderer, mode, panelX + padding + 50, y, modeColor, false);
        y += 12;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawString(renderer, Component.literal("Dimension:").withStyle(net.minecraft.ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        context.drawString(renderer, Component.literal(data.dimensionKey()), panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        context.drawString(renderer, Component.literal("Position:").withStyle(net.minecraft.ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        context.drawString(renderer, Component.literal(blockPos.getX() + " / " + data.blockY() + " / " + blockPos.getZ()),
            panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        String radiusLabelSide = data.allowMobSpawning() ? "SD:" : "Radius:";
        context.drawString(renderer, Component.literal(radiusLabelSide).withStyle(net.minecraft.ChatFormatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        MutableComponent radiusText = Component.literal(data.chunkRadius() + " / 3");
        context.drawString(renderer, radiusText, panelX + padding + 50, y, 0xFFFFFF, false);
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

            context.drawString(renderer, Component.literal("Legend").withStyle(net.minecraft.ChatFormatting.BOLD),
            panelX + padding, y, 0xFFFFFF, false);
        y += 12;

        int loadedColor = data.allowMobSpawning()
            ? ChunkMapGrid.COLOR_LOADED
            : ChunkMapGrid.COLOR_IN_RANGE;
        drawLegendItem(context, renderer, panelX + padding, y, loadedColor,
                Component.literal("Loaded"));
        y += 14;

        if (data.allowMobSpawning()) {
            drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_LOADED,
                    Component.literal("Simulation Distance"));
            y += 14;
        }

        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_IN_RANGE,
                Component.literal("Within radius"));
        y += 14;

        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_OTHER,
                Component.literal("Other player"));
        }
    }

    private Identifier getPlayerSkinTexture(String playerName) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || playerName == null || playerName.isEmpty()) {
            return FALLBACK_SKIN;
        }

        Identifier entryTexture = resolveSkinFromPlayerInfo(client, playerName);
        if (entryTexture != null && !entryTexture.equals(FALLBACK_SKIN)) {
            return entryTexture;
        }

        return FALLBACK_SKIN;
    }

    private Identifier resolveSkinFromPlayerInfo(Minecraft client, String playerName) {
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return null;
        }

        PlayerInfo entry = null;
        for (var playerEntry : handler.getOnlinePlayers()) {
            if (playerEntry.getProfile().name() != null && playerEntry.getProfile().name().equals(playerName)) {
                entry = playerEntry;
                break;
            }
        }

        if (entry == null) {
            return null;
        }

        try {
            var skin = entry.getSkin();
            if (skin == null || skin.body() == null) {
                return null;
            }
            Identifier texture = skin.body().texturePath();
            return texture != null && !texture.equals(FALLBACK_SKIN) ? texture : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void drawPlayerHead(GuiGraphics context, int x, int y, int size, String playerName) {
        Identifier texture = getPlayerSkinTexture(playerName);
        blitNonAtlas(context, texture, x, y, 8f, 8f, 8, 8, size, size, 64, 64);
        blitNonAtlas(context, texture, x, y, 40f, 8f, 8, 8, size, size, 64, 64);
    }

    private void drawSimpleTooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY, ItemStack.EMPTY);
    }

    private static void blitNonAtlas(
        GuiGraphics g,
        Identifier texture,
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
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, 0xFFFFFFFF);
        } catch (Throwable t) {
            g.blit(texture, x, y, (int) u, (int) v, width, height, textureWidth, textureHeight);
        }
    }

    private void drawSeparator(GuiGraphics context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x4A4A4A);
    }

    private void drawLegendItem(GuiGraphics context, Font renderer, int x, int y, int color, Component text) {
        int squareSize = 6;
        int squareY = y + (renderer.lineHeight - squareSize) / 2;
        context.fill(x, squareY, x + squareSize, squareY + squareSize, 0xFF000000 | color);
        context.fill(x + 1, squareY + 1, x + squareSize - 1, squareY + squareSize - 1, color);
        context.drawString(renderer, text, x + 10, y, 0xFFFFFF, false);
    }

    private void drawChunkloaderPoints(GuiGraphics context, int mouseX, int mouseY) {
        int cellSize = CELL_SIZE;
        int gridInnerLeft = gridLeft + 1;
        int gridInnerTop = gridTop + 1;

        int ownOffsetX = data.fakeplayerChunkX() - data.centerChunkX();
        int ownOffsetZ = data.fakeplayerChunkZ() - data.centerChunkZ();
        int halfMap = (data.mapWidth() - 1) / 2;
        int ownColumn = ownOffsetX + halfMap;
        int ownRow = ownOffsetZ + halfMap;
        int n = data.mapWidth() - 1;

        if (ownColumn >= 0 && ownColumn < data.mapWidth() && ownRow >= 0 && ownRow < data.mapHeight()) {
            int ownInnerChunkX = ((data.fakeplayerBlockX() % 16) + 16) % 16;
            int ownInnerChunkZ = ((data.fakeplayerBlockZ() % 16) + 16) % 16;

            int ownPixelCol, ownPixelRow;
            float ownSubX, ownSubZ;
            switch (mapRotation) {
                case 1 -> {
                    ownPixelCol = n - ownRow; ownPixelRow = ownColumn;
                    ownSubX = (1 - ownInnerChunkZ / 16.0f) * cellSize; ownSubZ = (ownInnerChunkX / 16.0f) * cellSize;
                }
                case 2 -> {
                    ownPixelCol = n - ownColumn; ownPixelRow = n - ownRow;
                    ownSubX = (1 - ownInnerChunkX / 16.0f) * cellSize; ownSubZ = (1 - ownInnerChunkZ / 16.0f) * cellSize;
                }
                case 3 -> {
                    ownPixelCol = ownRow; ownPixelRow = n - ownColumn;
                    ownSubX = (ownInnerChunkZ / 16.0f) * cellSize; ownSubZ = (1 - ownInnerChunkX / 16.0f) * cellSize;
                }
                default -> {
                    ownPixelCol = ownColumn; ownPixelRow = ownRow;
                    ownSubX = (ownInnerChunkX / 16.0f) * cellSize; ownSubZ = (ownInnerChunkZ / 16.0f) * cellSize;
                }
            }

            int ownCellLeft = gridInnerLeft + ownPixelCol * cellSize;
            int ownCellTop = gridInnerTop + ownPixelRow * cellSize;
            int ownPointX = ownCellLeft + (int) ownSubX;
            int ownPointY = ownCellTop + (int) ownSubZ;

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
                drawSimpleTooltip(context, List.of(Component.literal(ownTooltipText)), mouseX, mouseY);
            }
        }

        if (data.otherChunkloaders() == null || data.otherChunkloaders().isEmpty()) {
            return;
        }

        if (clientConfig.isHideOtherDots()) {
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

            int pixelCol, pixelRow;
            float subX, subZ;
            switch (mapRotation) {
                case 1 -> {
                    pixelCol = n - row; pixelRow = column;
                    subX = (1 - innerChunkZ / 16.0f) * cellSize; subZ = (innerChunkX / 16.0f) * cellSize;
                }
                case 2 -> {
                    pixelCol = n - column; pixelRow = n - row;
                    subX = (1 - innerChunkX / 16.0f) * cellSize; subZ = (1 - innerChunkZ / 16.0f) * cellSize;
                }
                case 3 -> {
                    pixelCol = row; pixelRow = n - column;
                    subX = (innerChunkZ / 16.0f) * cellSize; subZ = (1 - innerChunkX / 16.0f) * cellSize;
                }
                default -> {
                    pixelCol = column; pixelRow = row;
                    subX = (innerChunkX / 16.0f) * cellSize; subZ = (innerChunkZ / 16.0f) * cellSize;
                }
            }

            int cellLeft = gridInnerLeft + pixelCol * cellSize;
            int cellTop = gridInnerTop + pixelRow * cellSize;
            int pointX = cellLeft + (int) subX;
            int pointY = cellTop + (int) subZ;

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
                drawSimpleTooltip(context, List.of(Component.literal(tooltipText)), mouseX, mouseY);
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

        ChunkMapGrid(ClientLevel world, ChunkMapData data, int left, int top, int cellSize, int rotation) {
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

            int n = data.mapWidth() - 1;
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

                    int pixelCol, pixelRow;
                    switch (rotation) {
                        case 1 -> { pixelCol = n - row; pixelRow = column; }
                        case 2 -> { pixelCol = n - column; pixelRow = n - row; }
                        case 3 -> { pixelCol = row; pixelRow = n - column; }
                        default -> { pixelCol = column; pixelRow = row; }
                    }
                    int cellLeft = gridInnerLeft + pixelCol * cellSize;
                    int cellTop = gridInnerTop + pixelRow * cellSize;
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
                        gridInnerBottom,
                        rotation
                    ));
                }
            }
        }

        boolean applyMarkerStates(ChunkMapData newData) {
            if (cells.isEmpty()) {
                return false;
            }
            Map<Long, ChunkMapCell> stateByOffset = new HashMap<>();
            for (ChunkMapCell cell : newData.cells()) {
                stateByOffset.put(pack(cell.offsetX(), cell.offsetZ()), cell);
            }
            for (Cell cell : cells) {
                int offsetX = cell.chunkPos.x - newData.centerChunkX();
                int offsetZ = cell.chunkPos.z - newData.centerChunkZ();
                ChunkMapCell state = stateByOffset.get(pack(offsetX, offsetZ));
                if (state != null) {
                    cell.applyMarkerState(state, newData);
                }
            }
            return true;
        }

        void render(GuiGraphics context, int mouseX, int mouseY) {
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
            private static final int COLOR_LOADED_OVERLAY = 0x6655CC55;
            private static final int COLOR_LOADED_OVERLAY_CHUNKPLAYER = 0x665555FF;
            private static final int COLOR_SIMULATION_DISTANCE = 0x6655CC55;
            private static final int COLOR_RANGE_OVERLAY = 0x1A3D7FFF;
            private static final int COLOR_RANGE_OVERLAY_DISABLED = 0x26FF5555;
            private final ChunkPos chunkPos;
            private ChunkMapCell state;
            private final int left;
            private final int top;
            private final int size;
            private final int baseColor;
            private final ChunkTileImage tileImage;
            private ChunkMapData data;
            private final int gridLeft;
            private final int gridTop;
            private final int gridRight;
            private final int gridBottom;

            Cell(ClientLevel world, ChunkPos chunkPos, ChunkMapCell state, int left, int top, int size, int sampleY, ChunkMapData data, int gridLeft, int gridTop, int gridRight, int gridBottom, int rotation) {
                this.chunkPos = chunkPos;
                this.state = state;
                this.left = left;
                this.top = top;
                this.size = size;
                this.baseColor = ((chunkPos.x + chunkPos.z) & 1) == 0 ? 0xFF1F1F1F : 0xFF242424;
                this.tileImage = world != null ? new ChunkTileImage(world, chunkPos, sampleY, rotation) : null;
                this.data = data;
                this.gridLeft = gridLeft;
                this.gridTop = gridTop;
                this.gridRight = gridRight;
                this.gridBottom = gridBottom;
            }

            void applyMarkerState(ChunkMapCell newState, ChunkMapData newData) {
                this.state = newState;
                this.data = newData;
            }

            void render(GuiGraphics context, int mouseX, int mouseY) {
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
                    context.blit(
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
                    context.blit(
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
                        context.blit(
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

            List<Component> buildTooltip() {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("Chunk X:" + chunkPos.x + " Z:" + chunkPos.z));

                if (state.occupiedByOther()) {
                    String loaderName = state.simulatingFakeplayerName();
                    if (loaderName != null && !loaderName.isBlank()) {
                        tooltip.add(Component.literal("Loaded by " + loaderName));
                    } else {
                        tooltip.add(Component.literal("Loaded by another loader"));
                    }
                } else if (state.loaded()) {
                    tooltip.add(Component.literal("Loaded by this player"));
                } else if (state.withinRange()) {
                    if (!data.allowMobSpawning()) {
                        tooltip.add(Component.literal("Inside radius (enable to load)"));
                    } else {
                        tooltip.add(Component.literal("Inside simulation distance (enable to load)"));
                    }
                } else {
                    tooltip.add(Component.literal("Outside of this player"));
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
        topBoxButtons.forEach(this::removeWidget);
        topBoxButtons.clear();

        if (clientConfig == null) {
            clientConfig = ClientConfig.load();
        }

        ChunkMapLayoutPreset layoutPreset = ChunkMapLayoutPreset.fromConfig(clientConfig);
        boolean vertical = layoutPreset.isVerticalButtonBar();

        int buttonWidth = TOPBOX_BUTTON_WIDTH;
        int buttonHeight = TOPBOX_BUTTON_HEIGHT;
        int numButtons = TOPBOX_BUTTON_COUNT;
        int spacing;
        int startX;
        int startY;

        if (vertical) {
            int availableSpace = topBoxHeight - (numButtons * buttonHeight);
            spacing = availableSpace > 0 ? (availableSpace / (numButtons + 1)) : 2;
            spacing = Math.max(2, spacing);
            startX = topBoxX + (topBoxWidth - buttonWidth) / 2;
            startY = topBoxY + spacing;
        } else {
            int totalButtonsWidth = numButtons * buttonWidth;
            int availableSpace = topBoxWidth - totalButtonsWidth;
            spacing = availableSpace / (numButtons + 1);
            startX = topBoxX + spacing;
            startY = topBoxY + (topBoxHeight - buttonHeight) / 2;
        }

        int infoX = vertical ? startX : startX;
        int infoY = vertical ? startY : startY;

        int helpX = vertical ? startX : (startX + buttonWidth + spacing);
        int helpY = vertical ? (startY + (buttonHeight + spacing) * 1) : startY;

        int listX = vertical ? startX : (startX + (buttonWidth + spacing) * 2);
        int listY = vertical ? (startY + (buttonHeight + spacing) * 2) : startY;

        int uiNumber = layoutPreset.ordinal() + 1;
        String uiLabel = uiNumber == 1 ? "UI" : "UI " + uiNumber;
        int uiX = vertical ? startX : (startX + (buttonWidth + spacing) * 3);
        int uiY = vertical ? (startY + (buttonHeight + spacing) * 3) : startY;

        int deleteX = vertical ? startX : (startX + (buttonWidth + spacing) * 4);
        int deleteY = vertical ? (startY + (buttonHeight + spacing) * 4) : startY;

        infoButton = Button.builder(
            Component.literal("Info"),
            btn -> {
                openChildScreen(new ChunkloaderMenuScreen(this));
            })
            .bounds(infoX, infoY, buttonWidth, buttonHeight)
            .build();
        infoButton.setMessage(Component.literal("Info").withStyle(net.minecraft.ChatFormatting.WHITE));
        topBoxButtons.add(infoButton);
        this.addRenderableWidget(infoButton);

        Button helpButton = Button.builder(
            Component.literal("Help"),
            btn -> {
                openChildScreen(new ChunkMapHelpScreen(this));
            })
            .bounds(helpX, helpY, buttonWidth, buttonHeight)
            .build();
        helpButton.setMessage(Component.literal("Help").withStyle(net.minecraft.ChatFormatting.WHITE));
        topBoxButtons.add(helpButton);
        this.addRenderableWidget(helpButton);

        Button listButton = Button.builder(
            Component.literal("List"),
            btn -> {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            })
            .bounds(listX, listY, buttonWidth, buttonHeight)
            .build();
        listButton.setMessage(Component.literal("List").withStyle(net.minecraft.ChatFormatting.WHITE));
        topBoxButtons.add(listButton);
        this.addRenderableWidget(listButton);

        Button uiButton = Button.builder(
            Component.literal(uiLabel),
            btn -> cycleLayoutPreset())
            .bounds(uiX, uiY, buttonWidth, buttonHeight)
            .build();
        uiButton.setMessage(Component.literal(uiLabel).withStyle(net.minecraft.ChatFormatting.WHITE));
        topBoxButtons.add(uiButton);
        this.addRenderableWidget(uiButton);

        Button deleteButton = Button.builder(
            Component.literal("Delete"),
            btn -> {
                openChildScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Component.literal("Delete Player?").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
                    Component.literal("This will permanently delete this player.\nThis action cannot be undone!"),
                    () -> {
                        if (data.fakeplayerName() != null && !data.fakeplayerName().isBlank()) {
                            CustomFakePlayerSkinCache.clearPersistedSkin(data.fakeplayerName());
                        }
                        ChunkloaderNetworking.sendAction(
                            ChunkloaderActionPayload.Action.DELETE,
                            data.fakeplayerChunkX(),
                            data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
                        );
                        this.minecraft.setScreen(null);
                    },
                    null
                ));
            })
            .bounds(deleteX, deleteY, buttonWidth, buttonHeight)
            .build();
        deleteButton.setMessage(Component.literal("Delete").withStyle(net.minecraft.ChatFormatting.RED));
        topBoxButtons.add(deleteButton);
        this.addRenderableWidget(deleteButton);
    }

    private void buildActionButtons() {
        actionButtons.forEach(this::removeWidget);
        actionButtons.clear();
        actionButtonYOffset.clear();
        buttonOriginalActiveState.clear();
        actionHeaderLayouts.clear();
        easterEggLockedSkinButton = null;
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

        if (showEnableButton) {
        Button enableButton = Button.builder(
            Component.literal(enableLabelRaw),
            btn -> {
                ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_ENABLED,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
                );
                if (data.enabled()) {
                    Minecraft.getInstance().setScreen(null);
                }
            })
            .bounds(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.enabled()) {
            enableButton.setMessage((data.allowMobSpawning() ? Component.literal("Disable Fakeplayer") : Component.literal("Disable Chunkplayer")).withStyle(net.minecraft.ChatFormatting.RED));
        } else {
            enableButton.setMessage((data.allowMobSpawning() ? Component.literal("Enable Fakeplayer") : Component.literal("Enable Chunkplayer")).withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        actionButtons.add(enableButton);
        actionButtonYOffset.put(enableButton, cursorY);
        this.addRenderableWidget(enableButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean modeHeaderMatches = actionSearchMatches("Mode");
        String mobLabelRaw = data.allowMobSpawning() ? "Disable mob spawning" : "Enable mob spawning";
        boolean mobButtonMatches = actionSearchMatches(mobLabelRaw);
        String mobTargetLabelRaw = data.mobTarget() ? "Disable mob target" : "Enable mob target";
        boolean mobTargetMatches = data.allowMobSpawning() && actionSearchMatches(mobTargetLabelRaw);

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

        boolean showModeHeader = modeHeaderMatches || mobButtonMatches || mobTargetMatches || showRadiusSection;
        if (showModeHeader) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Mode")));
            cursorY += headerHeight + gap;
        }

        if (modeHeaderMatches || mobButtonMatches) {
        Button mobButton = Button.builder(
            Component.literal(mobLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_MOB_SPAWNING,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
            ))
            .bounds(buttonX, 0, buttonWidth, 20)
            .build();
        if (data.allowMobSpawning()) {
            mobButton.setMessage(Component.literal("Disable mob spawning").withStyle(net.minecraft.ChatFormatting.BLUE));
        } else {
            mobButton.setMessage(Component.literal("Enable mob spawning").withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        actionButtons.add(mobButton);
        actionButtonYOffset.put(mobButton, cursorY);
        this.addRenderableWidget(mobButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        if (data.allowMobSpawning() && (modeHeaderMatches || mobTargetMatches || mobButtonMatches)) {
            Button mobTargetButton = Button.builder(
                Component.literal(mobTargetLabelRaw),
                btn -> ChunkloaderNetworking.sendAction(
                    ChunkloaderActionPayload.Action.TOGGLE_MOB_TARGET,
                    data.fakeplayerChunkX(),
                    data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
                ))
                .bounds(buttonX, 0, buttonWidth, 20)
                .build();
            if (data.mobTarget()) {
                mobTargetButton.setMessage(Component.literal("Disable mob target").withStyle(net.minecraft.ChatFormatting.BLUE));
            } else {
                mobTargetButton.setMessage(Component.literal("Enable mob target").withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            actionButtons.add(mobTargetButton);
            actionButtonYOffset.put(mobTargetButton, cursorY);
            this.addRenderableWidget(mobTargetButton);
            cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean showRadiusButtons = modeHeaderMatches || showRadiusSection;
        if (showRadiusButtons) {
        Button radiusDown = Button.builder(
            Component.literal(radiusDownLabel),
            btn -> {
                if (canDecrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_DECREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            1
                    );
                }
            })
            .bounds(buttonX, radiusY, halfWidth, 20)
            .build();
        if (!canDecrease) {
            radiusDown.active = false;
            radiusDown.setMessage(Component.literal(radiusDownLabel).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        actionButtons.add(radiusDown);
        actionButtonYOffset.put(radiusDown, cursorY);
        this.addRenderableWidget(radiusDown);

        Button radiusUp = Button.builder(
            Component.literal(radiusUpLabel),
            btn -> {
                if (canIncrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_INCREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            1
                    );
                }
            })
            .bounds(buttonX + halfWidth + 4, radiusY, halfWidth, 20)
            .build();
        if (!canIncrease) {
            radiusUp.active = false;
            radiusUp.setMessage(Component.literal(radiusUpLabel).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        actionButtons.add(radiusUp);
        actionButtonYOffset.put(radiusUp, cursorY);
        this.addRenderableWidget(radiusUp);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean renameButtonMatches = actionSearchMatches("Rename");

        boolean hideHeaderMatches = actionSearchMatches("Hide options");
        String hideNameLabelRaw = data.nameVisible() ? "Hide name" : "Show name";
        String hideDotsLabelRaw = clientConfig.isHideOtherDots() ? "Show other dots" : "Hide other dots";
        boolean hideNameMatches = actionSearchMatches(hideNameLabelRaw);
        boolean hideDotsMatches = actionSearchMatches(hideDotsLabelRaw);
        boolean showHideSection = hideHeaderMatches || hideNameMatches || hideDotsMatches;

        if (showHideSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Hide options")));
            cursorY += headerHeight + gap;
        }

        int nameVisibleY = 0;
        if (showHideSection) {
        Button nameVisibleButton = Button.builder(
            Component.literal(hideNameLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_NAME_VISIBLE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
            ))
            .bounds(buttonX, nameVisibleY, buttonWidth, 20)
            .build();
        if (data.nameVisible()) {
            nameVisibleButton.setMessage(Component.literal("Hide name").withStyle(net.minecraft.ChatFormatting.WHITE));
        } else {
            nameVisibleButton.setMessage(Component.literal("Show name").withStyle(net.minecraft.ChatFormatting.WHITE));
        }
        actionButtons.add(nameVisibleButton);
        actionButtonYOffset.put(nameVisibleButton, cursorY);
        this.addRenderableWidget(nameVisibleButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int hideOtherDotsY = 0;
        Button hideOtherDotsButton = Button.builder(
            Component.literal(hideDotsLabelRaw),
            btn -> {
                clientConfig.toggleHideOtherDots();
                buildActionButtons();
                buttonsNeedUpdate = true;
            })
            .bounds(buttonX, hideOtherDotsY, buttonWidth, 20)
            .build();
        if (clientConfig.isHideOtherDots()) {
            hideOtherDotsButton.setMessage(Component.literal("Show other dots").withStyle(net.minecraft.ChatFormatting.WHITE));
        } else {
            hideOtherDotsButton.setMessage(Component.literal("Hide other dots").withStyle(net.minecraft.ChatFormatting.WHITE));
        }
        actionButtons.add(hideOtherDotsButton);
        actionButtonYOffset.put(hideOtherDotsButton, cursorY);
        this.addRenderableWidget(hideOtherDotsButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean visHeaderMatches = actionSearchMatches("Visualization");
        String visLabelRaw = data.visualizeActive() ? "Disable visualization" : "Enable visualization";
        String vis3DLabelRaw = data.visualize3DActive() ? "Disable 3D visualization" : "Enable 3D visualization";
        boolean visButtonMatches = actionSearchMatches(visLabelRaw);
        boolean vis3DButtonMatches = actionSearchMatches(vis3DLabelRaw);
        boolean showVisSection = visHeaderMatches || visButtonMatches || vis3DButtonMatches;

        if (showVisSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Visualization")));
            cursorY += headerHeight + gap;
        }

        int visualizeY = 0;
        if (showVisSection) {
        Button visualizeButton = Button.builder(
            Component.literal(visLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
            ))
            .bounds(buttonX, visualizeY, buttonWidth, 20)
            .build();
        if (data.visualizeActive()) {
            visualizeButton.setMessage(Component.literal("Disable visualization").withStyle(net.minecraft.ChatFormatting.WHITE));
        } else {
            visualizeButton.setMessage(Component.literal("Enable visualization").withStyle(net.minecraft.ChatFormatting.WHITE));
        }
        actionButtons.add(visualizeButton);
        actionButtonYOffset.put(visualizeButton, cursorY);
        this.addRenderableWidget(visualizeButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int visualize3DY = 0;
        Button visualize3DButton = Button.builder(
            Component.literal(vis3DLabelRaw),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE3D,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
                            0
            ))
            .bounds(buttonX, visualize3DY, buttonWidth, 20)
            .build();
        if (data.visualize3DActive()) {
            visualize3DButton.setMessage(Component.literal("Disable 3D visualization").withStyle(net.minecraft.ChatFormatting.WHITE));
        } else {
            visualize3DButton.setMessage(Component.literal("Enable 3D visualization").withStyle(net.minecraft.ChatFormatting.WHITE));
        }
        actionButtons.add(visualize3DButton);
        actionButtonYOffset.put(visualize3DButton, cursorY);
        this.addRenderableWidget(visualize3DButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        boolean settingsHeaderMatches = actionSearchMatches("Settings");
        boolean panelColorMatches = actionSearchMatches("Panel color");
        boolean keybindMatches = actionSearchMatches("Keybinds");
        boolean skinMatches = actionSearchMatches("Skin");
        boolean showSettingsSection = settingsHeaderMatches || panelColorMatches || keybindMatches || skinMatches || renameButtonMatches;

        if (showSettingsSection) {
            actionHeaderLayouts.add(new ActionHeaderLayout(cursorY, headerHeight, Component.literal("Settings")));
            cursorY += headerHeight + gap;
        }

        int renameY = 0;
        if (showSettingsSection) {
        Button renameButton = Button.builder(
            Component.literal("Rename"),
            btn -> {
                openChildScreen(new RenameChunkloaderScreen(
                    this,
                    data.fakeplayerChunkX(),
                    data.fakeplayerChunkZ(),
                    data.dimensionKey(),
                    data.displayName()
                ));
            })
            .bounds(buttonX, renameY, buttonWidth, 20)
            .build();
        renameButton.setMessage(Component.literal("Rename").withStyle(net.minecraft.ChatFormatting.WHITE));
        actionButtons.add(renameButton);
        actionButtonYOffset.put(renameButton, cursorY);
        this.addRenderableWidget(renameButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int panelColorY = 0;
        Button panelColorButton = Button.builder(
            Component.literal("Panel color"),
            btn -> openPanelColorEditor())
            .bounds(buttonX, panelColorY, buttonWidth, 20)
            .build();
        panelColorButton.setMessage(Component.literal("Panel color").withStyle(net.minecraft.ChatFormatting.WHITE));
        actionButtons.add(panelColorButton);
        actionButtonYOffset.put(panelColorButton, cursorY);
        this.addRenderableWidget(panelColorButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int skinY = 0;
        boolean easterEggSkinLocked = data.easterEgg();
        Button skinButton = Button.builder(
            Component.literal("Skin"),
            btn -> {
                if (data.easterEgg()) {
                    return;
                }
                if (clientConfig == null) {
                    clientConfig = ClientConfig.load();
                }
                openChildScreen(new ChunkplayerSkinScreen(this, clientConfig, data.fakeplayerName()));
            })
            .bounds(buttonX, skinY, buttonWidth, 20)
            .build();
        if (easterEggSkinLocked) {
            skinButton.active = false;
            skinButton.setMessage(Component.literal("Skin").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            easterEggLockedSkinButton = skinButton;
        } else {
            skinButton.setMessage(Component.literal("Skin").withStyle(net.minecraft.ChatFormatting.WHITE));
        }
        actionButtons.add(skinButton);
        actionButtonYOffset.put(skinButton, cursorY);
        this.addRenderableWidget(skinButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;

        int keybindY = 0;
        Button keybindButton = Button.builder(
            Component.literal("Keybinds"),
            btn -> {
                openChildScreen(new KeybindConfigScreen(this));
            })
            .bounds(buttonX, keybindY, buttonWidth, 20)
            .build();
        keybindButton.setMessage(Component.literal("Keybinds").withStyle(net.minecraft.ChatFormatting.WHITE));
        actionButtons.add(keybindButton);
        actionButtonYOffset.put(keybindButton, cursorY);
        this.addRenderableWidget(keybindButton);

        cursorY += ACTION_BUTTON_HEIGHT + gap;
        }

        int resetY = footerRowY;
        resetButton = Button.builder(
            Component.literal("Reset to defaults"),
            btn -> {
                openChildScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Component.literal("Reset to Defaults?").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
                    Component.literal("This will reset all settings to default values.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.RESET_TO_DEFAULTS,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            data.dimensionKey(),
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
        resetButton.setMessage(Component.literal("Reset to defaults").withStyle(net.minecraft.ChatFormatting.WHITE));
        this.addRenderableWidget(resetButton);

        actionContentHeight = Math.max(0, cursorY - gap);

        buttonsNeedUpdate = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

