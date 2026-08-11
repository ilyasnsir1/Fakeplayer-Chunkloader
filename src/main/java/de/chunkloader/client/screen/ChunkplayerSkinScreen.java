package de.chunkloader.client.screen;

import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.client.CustomFakePlayerSkinCache;
import de.chunkloader.client.SkinLayerMask;
import de.chunkloader.client.config.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import de.chunkloader.client.SkinModelType;
import de.chunkloader.client.SkinFilePicker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class ChunkplayerSkinScreen extends Screen {
    private static final int SCREEN_MARGIN = 12;
    private static final int PANEL_MAX_WIDTH = 460;
    private static final int PANEL_MAX_HEIGHT = 370;
    private static final float PREVIEW_MODEL_HEIGHT = 2.125F;
    private static final float PREVIEW_MODEL_WIDTH = 1.5F;
    private static final float PREVIEW_FIT_MARGIN = 0.97F;
    private static final float PREVIEW_Y_PIVOT = -1.0625F;

    private static final float PREVIEW_VISUAL_CENTER_BIAS = 1.601F + PREVIEW_Y_PIVOT + 0.10F;

    private static final float PREVIEW_CLAMP_HEIGHT = 2.0F;
    private static final float PREVIEW_CLAMP_WIDTH = 1.2F;

    private static final float PREVIEW_EDGE_PAD_PX = 4.0F;
    private static final float MIN_PREVIEW_ZOOM = 0.6F;
    private static final float MAX_PREVIEW_ZOOM = 2.25F;
    private static final float PREVIEW_ROTATION_SENSITIVITY = 2.5F;
    private static final float PREVIEW_PITCH_LIMIT = 50.0F;

    private static final int LAYER_CHEVRON_SIZE = 11;
    private static final int LAYER_ROW_HEIGHT = 12;
    private static final int LAYER_MENU_PAD = 3;
    private static final UUID PREVIEW_CACHE_UUID = UUID.nameUUIDFromBytes(
        "chunkloader:skin-preview".getBytes(StandardCharsets.UTF_8)
    );

    private final Screen parent;
    private final ClientConfig clientConfig;
    private final String targetPlayerName;

    private EditBox pathField;
    private Button loadButton;
    private Button chooseFileButton;
    private Button applyButton;
    private Button removeButton;
    private Button backButton;

    private CustomFakePlayerSkinCache.CustomSkin previewSkin;
    private PlayerModel widePreviewModel;
    private PlayerModel slimPreviewModel;
    private String selectedPath;
    private boolean previewDragging;
    private boolean previewPanning;
    private float previewPitch = -5.0F;
    private float previewYaw = 30.0F;
    private float previewScale = 70.0F;
    private float previewOffsetX;
    private float previewOffsetY;
    private boolean fileDialogOpen;
    private boolean layersMenuOpen;
    private int previewLayerMask = SkinLayerMask.DEFAULT_MASK;
    private String statusMessage = "Select a PNG file or enter a path.";
    private int statusColor = 0xFFCCCCCC;

    public ChunkplayerSkinScreen(Screen parent, ClientConfig clientConfig, String targetPlayerName) {
        super(Component.literal("Chunkplayer Skin"));
        this.parent = parent;
        this.clientConfig = clientConfig != null ? clientConfig : ClientConfig.load();
        this.targetPlayerName = targetPlayerName;
        this.selectedPath = this.clientConfig.getCustomSkinPath(targetPlayerName);

        this.previewLayerMask = this.clientConfig.getCustomSkinLayers(targetPlayerName);
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelWidth = getPanelWidth();
        int rowX = panelX + 18;
        int rowWidth = panelWidth - 36;
        int fileFieldY = panelY + 64;
        int chooseWidth = 98;
        int loadWidth = 58;
        int fieldWidth = rowWidth - chooseWidth - loadWidth - 16;

        pathField = new EditBox(this.font, rowX + 4, fileFieldY + 7, fieldWidth - 8, 12, Component.literal("Skin PNG"));
        pathField.setMaxLength(512);
        pathField.setBordered(false);
        pathField.setTextShadow(false);
        if (clientConfig != null) {
            pathField.setTextColor(clientConfig.getSkinSearchbarTextColor());
        }
        pathField.setResponder(text -> updateLoadButtonState());
        setPathFieldValue(selectedPath != null ? selectedPath : "");
        addRenderableWidget(pathField);

        loadButton = Button.builder(
            Component.literal("Load"),
            button -> loadSkinFromPath(pathField.getValue())
        ).bounds(rowX + fieldWidth + 8, fileFieldY, loadWidth, 20).build();
        addRenderableWidget(loadButton);

        chooseFileButton = Button.builder(
            Component.literal("Choose PNG"),
            button -> openFilePicker()
        ).bounds(rowX + fieldWidth + loadWidth + 16, fileFieldY, chooseWidth, 20).build();
        addRenderableWidget(chooseFileButton);

        int actionWidth = 100;
        int actionGap = 8;
        int actionY = panelY + getPanelHeight() - 36;
        int actionStartX = panelX + (panelWidth - (actionWidth * 3 + actionGap * 2)) / 2;

        applyButton = Button.builder(
            Component.literal("Apply"),
            button -> applySkin()
        ).bounds(actionStartX, actionY, actionWidth, 20).build();
        addRenderableWidget(applyButton);

        removeButton = Button.builder(
            Component.literal("Remove"),
            button -> removeSkin()
        ).bounds(actionStartX + actionWidth + actionGap, actionY, actionWidth, 20).build();
        addRenderableWidget(removeButton);

        backButton = Button.builder(
            Component.literal("Back"),
            button -> this.minecraft.setScreen(parent)
        ).bounds(actionStartX + (actionWidth + actionGap) * 2, actionY, actionWidth, 20).build();
        addRenderableWidget(backButton);

        updateLoadButtonState();
        updateActionButtonState();

        if (selectedPath != null && !selectedPath.isBlank()) {
            loadSkinFromPath(selectedPath);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        PreviewBounds previewBounds = getPreviewBounds();

        drawModalPanel(context, panelX, panelY, panelWidth, panelHeight);

        Font renderer = this.font;
        Component title = Component.literal("Select Skin").withStyle(ChatFormatting.BOLD);
        int titleColor = clientConfig != null ? clientConfig.getSkinTitleColor() : 0xFFFFFFFF;
        int titleX = panelX + (panelWidth - renderer.width(title)) / 2 + 1;
        int titleY = panelY + 12;
        context.drawString(renderer, title, titleX, titleY, titleColor, false);

        int textColor = clientConfig != null ? clientConfig.getSkinTextColor() : 0xCC808080;
        int valueColor = clientConfig != null ? clientConfig.getSkinPlayerNameColor() : 0xFFFFFFFF;
        String playerLabel = targetPlayerName == null || targetPlayerName.isBlank()
            ? "Player: Unavailable"
            : "Player: " + targetPlayerName;
        context.drawString(renderer, Component.literal(playerLabel), panelX + 18, panelY + 35, valueColor, false);
        context.drawString(renderer, Component.literal("Skin PNG"), panelX + 18, panelY + 52, textColor, false);
        context.drawString(renderer, Component.literal("3D Preview"), previewBounds.left(), previewBounds.top() - 13, textColor, false);

        int rowX = panelX + 18;
        int rowWidth = panelWidth - 36;
        int fileFieldY = panelY + 64;
        int chooseWidth = 98;
        int loadWidth = 58;
        int fieldWidth = rowWidth - chooseWidth - loadWidth - 16;

        int pathBgColor = clientConfig != null ? clientConfig.getSkinSearchbarBgColor() : 0xFF0A0D10;
        int pathBorderColor = clientConfig != null ? clientConfig.getSkinSearchbarBorderColor() : 0xFF4A4A4A;
        int currentBorderColor = (pathField != null && pathField.isFocused()) ? 0xFFFFFFFF : pathBorderColor;
        int pathPlaceholderColor = clientConfig != null ? clientConfig.getSkinSearchbarPlaceholderColor() : 0xCC808080;

        context.fill(rowX, fileFieldY, rowX + fieldWidth, fileFieldY + 20, pathBgColor);
        context.fill(rowX, fileFieldY, rowX + fieldWidth, fileFieldY + 1, currentBorderColor);
        context.fill(rowX, fileFieldY + 19, rowX + fieldWidth, fileFieldY + 20, currentBorderColor);
        context.fill(rowX, fileFieldY, rowX + 1, fileFieldY + 20, currentBorderColor);
        context.fill(rowX + fieldWidth - 1, fileFieldY, rowX + fieldWidth, fileFieldY + 20, currentBorderColor);

        if (pathField != null && !pathField.isFocused() && pathField.getValue().isEmpty()) {
            context.drawString(renderer, Component.literal("Path to a skin PNG"), rowX + 4, fileFieldY + 7, pathPlaceholderColor, false);
        }

        drawPreviewPanel(context, previewBounds);
        drawPreview(context, previewBounds);
        drawLayerControls(context, previewBounds);

        Component controlsHint = Component.literal("Drag: rotate  |  Alt/MMB+drag: pan  |  Scroll: zoom  |  Double click: reset");
        context.drawString(
            renderer,
            controlsHint,
            previewBounds.left() + (previewBounds.width() - renderer.width(controlsHint)) / 2,
            previewBounds.bottom() + 8,
            textColor,
            false
        );

        int statusY = panelY + panelHeight - 58;
        String visibleStatus = truncateStatus(renderer, statusMessage, panelWidth - 36);
        int currentStatusColor;
        if (statusColor == 0xFF55FF55) {
            currentStatusColor = clientConfig != null ? clientConfig.getSkinStatusSuccessColor() : 0xFF55FF55;
        } else if (statusColor == 0xFFFF7777) {
            currentStatusColor = clientConfig != null ? clientConfig.getSkinStatusErrorColor() : 0xFFFF7777;
        } else if (statusColor == 0xFFFFCC66) {
            currentStatusColor = clientConfig != null ? clientConfig.getSkinStatusWarningColor() : 0xFFFFCC66;
        } else {
            currentStatusColor = textColor;
        }
        context.drawString(renderer, Component.literal(visibleStatus), panelX + 18, statusY, currentStatusColor, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (click.button() == 0) {
            if (pathField != null && !pathField.isMouseOver(mouseX, mouseY)) {
                if (this.getFocused() == pathField) {
                    this.setFocused(null);
                }
                pathField.setFocused(false);
            }
            if (handleLayerControlClick(mouseX, mouseY)) {
                return true;
            }
        }

        if (previewSkin != null && getPreviewBounds().contains(mouseX, mouseY)) {
            if (isOverLayerControls(mouseX, mouseY)) {
                return true;
            }
            if (click.button() == 0 && doubleClick) {
                resetPreviewCamera();
                previewDragging = false;
                previewPanning = false;
                return true;
            }
            if (click.button() == 0 || click.button() == 2) {
                previewDragging = true;
                previewPanning = click.button() == 2 || click.hasAltDown();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (previewDragging) {
            if (previewPanning) {
                previewOffsetX += (float) deltaX;
                previewOffsetY += (float) deltaY;
                clampPreviewOffsets(getPreviewBounds());
            } else {
                previewYaw = wrapDegrees(previewYaw + (float) deltaX * PREVIEW_ROTATION_SENSITIVITY);
                previewPitch = clamp(
                    previewPitch - (float) deltaY * PREVIEW_ROTATION_SENSITIVITY,
                    -PREVIEW_PITCH_LIMIT,
                    PREVIEW_PITCH_LIMIT
                );
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (previewDragging) {
            previewDragging = false;
            previewPanning = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        PreviewBounds bounds = getPreviewBounds();
        if (previewSkin != null && bounds.contains(mouseX, mouseY)) {
            zoomPreviewAt(bounds, (float) mouseX, (float) mouseY, (float) verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        CustomFakePlayerSkinCache.clearPreviewSkin(PREVIEW_CACHE_UUID);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openFilePicker() {
        if (fileDialogOpen) {
            return;
        }

        fileDialogOpen = true;
        updateLoadButtonState();
        setStatus("Opening file picker...", 0xFFCCCCCC);

        final String preferredPath = pathField.getValue().trim();
        Thread pickerThread = new Thread(() -> {
            String selectedFilePath = null;
            String cancelMessage = "File selection canceled.";
            try {
                selectedFilePath = SkinFilePicker.openPngDialog(preferredPath);
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                org.slf4j.LoggerFactory.getLogger("chunkloader").warn("Unable to open the custom skin file picker", e);
                cancelMessage = "Failed to open file picker.";
            }
            final String path = selectedFilePath;
            final String message = cancelMessage;
            Minecraft.getInstance().execute(() -> finishFilePicker(path, message));
        }, "chunkloader-skin-file-picker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private void finishFilePicker(String selectedFilePath, String pickerMessage) {
        fileDialogOpen = false;
        updateLoadButtonState();
        if (minecraft.screen != this) {
            return;
        }
        if (selectedFilePath == null) {
            setStatus(pickerMessage, 0xFFFFCC66);
            return;
        }

        setPathFieldValue(selectedFilePath);
        loadSkinFromPath(selectedFilePath);
    }

    private void setPathFieldValue(String path) {
        if (pathField == null) {
            return;
        }
        pathField.setValue(path == null ? "" : path);

        pathField.moveCursorToStart(false);
    }

    private void loadSkinFromPath(String pathText) {
        Path skinPath = validateSkinPath(pathText);
        if (skinPath == null) {
            return;
        }

        try {
            previewSkin = CustomFakePlayerSkinCache.setPreviewSkin(PREVIEW_CACHE_UUID, skinPath);
            selectedPath = skinPath.toString();
            setPathFieldValue(selectedPath);
            resetPreviewCamera();
            String modelName = previewSkin.model() == SkinModelType.SLIM ? "Slim" : "Standard";
            setStatus("Preview loaded (" + modelName + " arms).", 0xFF55FF55);
            updateLoadButtonState();
            updateActionButtonState();
        } catch (IOException e) {
            setStatus("Failed to load skin PNG: " + e.getMessage(), 0xFFFF7777);
        }
    }

    private Path validateSkinPath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            setStatus("Please enter the path to a PNG file.", 0xFFFF7777);
            return null;
        }

        Path skinPath;
        try {
            skinPath = Path.of(pathText.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            setStatus("The specified path is invalid.", 0xFFFF7777);
            return null;
        }

        if (!skinPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            setStatus("Please select a PNG file.", 0xFFFF7777);
            return null;
        }
        if (!Files.isRegularFile(skinPath)) {
            setStatus("The selected file does not exist.", 0xFFFF7777);
            return null;
        }
        return skinPath;
    }

    private void applySkin() {
        if (targetPlayerName == null || targetPlayerName.isBlank()) {
            setStatus("The selected player is unavailable.", 0xFFFF7777);
            return;
        }
        if (selectedPath == null || selectedPath.isBlank()) {
            setStatus("Load a valid skin PNG first.", 0xFFFF7777);
            return;
        }

        try {
            Path skinPath = Path.of(selectedPath);
            byte[] pngBytes = Files.readAllBytes(skinPath);
            if (pngBytes.length > de.chunkloader.config.CustomFakePlayerSkinStore.MAX_PNG_BYTES) {
                setStatus("Skin file too large (max 128 KiB).", 0xFFFF7777);
                return;
            }
            CustomFakePlayerSkinCache.setSkin(targetPlayerName, skinPath, previewLayerMask);
            clientConfig.setCustomSkinPath(targetPlayerName, skinPath.toString());
            clientConfig.setCustomSkinLayers(targetPlayerName, previewLayerMask);
            String model = previewSkin != null && previewSkin.model() == SkinModelType.SLIM ? "slim" : "wide";
            de.chunkloader.client.network.ChunkloaderClientNetworking.sendApplyCustomSkin(
                targetPlayerName,
                previewLayerMask,
                model,
                pngBytes
            );
            setStatus("Skin applied for " + targetPlayerName + ".", 0xFF55FF55);
            updateActionButtonState();
        } catch (InvalidPathException e) {
            setStatus("The saved path is invalid.", 0xFFFF7777);
        } catch (IOException e) {
            setStatus("Failed to apply skin: " + e.getMessage(), 0xFFFF7777);
        }
    }

    private void removeSkin() {
        if (targetPlayerName == null || targetPlayerName.isBlank()) {
            return;
        }

        CustomFakePlayerSkinCache.removeSkin(targetPlayerName);
        CustomFakePlayerSkinCache.clearPreviewSkin(PREVIEW_CACHE_UUID);
        previewSkin = null;
        layersMenuOpen = false;
        previewLayerMask = SkinLayerMask.DEFAULT_MASK;
        clientConfig.setCustomSkinPath(targetPlayerName, null);
        selectedPath = null;
        setPathFieldValue("");
        resetPreviewCamera();
        de.chunkloader.client.network.ChunkloaderClientNetworking.sendClearCustomSkin(targetPlayerName);
        setStatus("Custom skin removed.", 0xFFCCCCCC);
        updateLoadButtonState();
        updateActionButtonState();
    }

    private void drawPreview(GuiGraphics context, PreviewBounds bounds) {
        if (previewSkin == null) {
            Component hint = Component.literal("No skin PNG loaded yet");
            int hintColor = clientConfig != null ? clientConfig.getSkinTextColor() : 0xCC808080;
            context.drawString(
                font,
                hint,
                bounds.left() + (bounds.width() - font.width(hint)) / 2,
                bounds.top() + bounds.height() / 2 - 4,
                hintColor,
                false
            );
            return;
        }

        PlayerModel model = getPreviewModel(previewSkin.model());
        SkinLayerMask.applyToModel(model, previewLayerMask);
        int[] drawRect = getPreviewDrawRect(bounds);
        context.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        context.submitSkinRenderState(
            model,
            previewSkin.textureId(),
            previewScale,
            previewPitch,
            previewYaw,
            PREVIEW_Y_PIVOT,
            drawRect[0],
            drawRect[1],
            drawRect[2],
            drawRect[3]
        );
        context.disableScissor();
    }

    private void drawLayerControls(GuiGraphics context, PreviewBounds bounds) {
        if (previewSkin == null) {
            return;
        }

        Font renderer = this.font;
        int chevronBg = clientConfig != null ? clientConfig.getSkinLayerChevronBgColor() : 0x99000000;
        int chevronColor = clientConfig != null ? clientConfig.getSkinLayerChevronColor() : 0xFFFFFFFF;
        int menuBg = clientConfig != null ? clientConfig.getSkinLayerMenuBgColor() : 0xCC0A0D10;
        int activeColor = clientConfig != null ? clientConfig.getSkinLayerActiveColor() : 0xFFFFFFFF;
        int inactiveColor = clientConfig != null ? clientConfig.getSkinLayerInactiveColor() : 0xCC808080;
        int chevronLeft = bounds.left() + LAYER_MENU_PAD;
        int chevronTop = bounds.top() + LAYER_MENU_PAD;
        context.fill(chevronLeft, chevronTop, chevronLeft + LAYER_CHEVRON_SIZE, chevronTop + LAYER_CHEVRON_SIZE, chevronBg);
        drawChevronIcon(context, chevronLeft, chevronTop, layersMenuOpen, chevronColor);

        if (!layersMenuOpen) {
            return;
        }

        int menuLeft = bounds.left() + LAYER_MENU_PAD;
        int menuTop = chevronTop + LAYER_CHEVRON_SIZE + 2;
        int menuWidth = Math.min(110, bounds.width() - LAYER_MENU_PAD * 2);
        int menuHeight = SkinLayerMask.EDITABLE_PARTS.length * LAYER_ROW_HEIGHT + 4;
        context.fill(menuLeft, menuTop, menuLeft + menuWidth, menuTop + menuHeight, menuBg);

        int checkboxWidth = renderer.width("[x]");
        int rowY = menuTop + 2;
        for (PlayerModelPart part : SkinLayerMask.EDITABLE_PARTS) {
            boolean enabled = SkinLayerMask.isShown(previewLayerMask, part);
            int markColor = enabled ? activeColor : inactiveColor;
            int markX = menuLeft + 3;
            int markY = rowY + 2;
            drawLayerCheckbox(context, renderer, markX, markY, enabled, markColor);
            context.drawString(
                renderer,
                Component.literal(SkinLayerMask.label(part)),
                markX + checkboxWidth + 4,
                markY,
                markColor,
                false
            );
            rowY += LAYER_ROW_HEIGHT;
        }
    }

    private void drawChevronIcon(GuiGraphics context, int boxLeft, int boxTop, boolean openUp, int color) {
        int startX = boxLeft + (LAYER_CHEVRON_SIZE - 5) / 2;
        int startY = boxTop + (LAYER_CHEVRON_SIZE - 3) / 2;
        if (openUp) {
            fillPixel(context, startX + 2, startY, color);
            fillPixel(context, startX + 1, startY + 1, color);
            fillPixel(context, startX + 3, startY + 1, color);
            fillPixel(context, startX, startY + 2, color);
            fillPixel(context, startX + 4, startY + 2, color);
        } else {
            fillPixel(context, startX, startY, color);
            fillPixel(context, startX + 4, startY, color);
            fillPixel(context, startX + 1, startY + 1, color);
            fillPixel(context, startX + 3, startY + 1, color);
            fillPixel(context, startX + 2, startY + 2, color);
        }
    }

    private static void fillPixel(GuiGraphics context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
    }

    private void drawLayerCheckbox(GuiGraphics context, Font renderer, int x, int y, boolean enabled, int color) {
        int openWidth = renderer.width("[");
        int innerWidth = renderer.width(enabled ? "x" : " ");
        context.drawString(renderer, Component.literal("["), x, y, color, false);
        if (enabled) {
            context.drawString(renderer, Component.literal("x"), x + openWidth, y - 1, color, false);
        } else {
            context.drawString(renderer, Component.literal(" "), x + openWidth, y, color, false);
        }
        context.drawString(renderer, Component.literal("]"), x + openWidth + innerWidth, y, color, false);
    }

    private boolean handleLayerControlClick(double mouseX, double mouseY) {
        if (previewSkin == null) {
            return false;
        }

        PreviewBounds bounds = getPreviewBounds();
        int chevronLeft = bounds.left() + LAYER_MENU_PAD;
        int chevronTop = bounds.top() + LAYER_MENU_PAD;
        if (mouseX >= chevronLeft && mouseX < chevronLeft + LAYER_CHEVRON_SIZE
            && mouseY >= chevronTop && mouseY < chevronTop + LAYER_CHEVRON_SIZE) {
            layersMenuOpen = !layersMenuOpen;
            return true;
        }

        if (!layersMenuOpen) {
            return false;
        }

        int menuLeft = bounds.left() + LAYER_MENU_PAD;
        int menuTop = chevronTop + LAYER_CHEVRON_SIZE + 2;
        int menuWidth = Math.min(110, bounds.width() - LAYER_MENU_PAD * 2);
        int menuHeight = SkinLayerMask.EDITABLE_PARTS.length * LAYER_ROW_HEIGHT + 4;
        if (mouseX < menuLeft || mouseX >= menuLeft + menuWidth || mouseY < menuTop || mouseY >= menuTop + menuHeight) {
            return false;
        }

        int index = (int) ((mouseY - menuTop - 2) / LAYER_ROW_HEIGHT);
        if (index < 0 || index >= SkinLayerMask.EDITABLE_PARTS.length) {
            return true;
        }

        previewLayerMask = SkinLayerMask.toggle(previewLayerMask, SkinLayerMask.EDITABLE_PARTS[index]);
        updateActionButtonState();
        return true;
    }

    private boolean isOverLayerControls(double mouseX, double mouseY) {
        if (previewSkin == null) {
            return false;
        }

        PreviewBounds bounds = getPreviewBounds();
        int chevronLeft = bounds.left() + LAYER_MENU_PAD;
        int chevronTop = bounds.top() + LAYER_MENU_PAD;
        if (mouseX >= chevronLeft && mouseX < chevronLeft + LAYER_CHEVRON_SIZE
            && mouseY >= chevronTop && mouseY < chevronTop + LAYER_CHEVRON_SIZE) {
            return true;
        }
        if (!layersMenuOpen) {
            return false;
        }

        int menuLeft = bounds.left() + LAYER_MENU_PAD;
        int menuTop = chevronTop + LAYER_CHEVRON_SIZE + 2;
        int menuWidth = Math.min(110, bounds.width() - LAYER_MENU_PAD * 2);
        int menuHeight = SkinLayerMask.EDITABLE_PARTS.length * LAYER_ROW_HEIGHT + 4;
        return mouseX >= menuLeft && mouseX < menuLeft + menuWidth
            && mouseY >= menuTop && mouseY < menuTop + menuHeight;
    }

    private PlayerModel getPreviewModel(SkinModelType model) {
        if (model == SkinModelType.SLIM) {
            if (slimPreviewModel == null) {
                slimPreviewModel = new PlayerModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM),
                    true
                );
            }
            return slimPreviewModel;
        }

        if (widePreviewModel == null) {
            widePreviewModel = new PlayerModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
                false
            );
        }
        return widePreviewModel;
    }

    private void resetPreviewCamera() {
        previewPitch = -5.0F;
        previewYaw = 30.0F;
        previewScale = getFitScale(getPreviewBounds());
        previewOffsetX = 0.0F;
        previewOffsetY = getDefaultPreviewOffsetY();
    }

    private float getDefaultPreviewOffsetY() {

        return -PREVIEW_VISUAL_CENTER_BIAS * previewScale - 2.0F;
    }

    private float getFitScale(PreviewBounds bounds) {
        int height = Math.max(1, bounds.height());
        return PREVIEW_FIT_MARGIN * height / PREVIEW_MODEL_HEIGHT;
    }

    private float getMinPreviewScale(PreviewBounds bounds) {
        return getFitScale(bounds) * MIN_PREVIEW_ZOOM;
    }

    private float getMaxPreviewScale(PreviewBounds bounds) {
        return getFitScale(bounds) * MAX_PREVIEW_ZOOM;
    }

    private void zoomPreviewAt(PreviewBounds bounds, float mouseX, float mouseY, float scrollAmount) {
        float oldScale = previewScale;
        float fit = getFitScale(bounds);
        float newScale = clamp(
            oldScale + scrollAmount * (fit * 0.07F),
            getMinPreviewScale(bounds),
            getMaxPreviewScale(bounds)
        );
        if (newScale == oldScale) {
            return;
        }

        float scaleRatio = newScale / oldScale;
        float baseX = (bounds.left() + bounds.right()) / 2.0F;
        float baseY = (bounds.top() + bounds.bottom()) / 2.0F;
        float currentOriginX = baseX + previewOffsetX;
        float currentOriginY = baseY + previewOffsetY;
        previewOffsetX = mouseX - baseX - (mouseX - currentOriginX) * scaleRatio;
        previewOffsetY = mouseY - baseY - (mouseY - currentOriginY) * scaleRatio;
        previewScale = newScale;
        clampPreviewOffsets(bounds);
    }

    private void clampPreviewOffsets(PreviewBounds bounds) {

        float homeY = getDefaultPreviewOffsetY();
        float modelHalfW = PREVIEW_CLAMP_WIDTH * previewScale * 0.5F;
        float extentH = PREVIEW_CLAMP_HEIGHT * previewScale * 0.5F + PREVIEW_EDGE_PAD_PX;
        float viewHalfW = bounds.width() * 0.5F;
        float viewHalfH = bounds.height() * 0.5F;
        float maxOffsetX = Math.abs(viewHalfW - modelHalfW);
        float maxOffsetY = Math.abs(viewHalfH - extentH);
        previewOffsetX = clamp(previewOffsetX, -maxOffsetX, maxOffsetX);
        previewOffsetY = clamp(previewOffsetY, homeY - maxOffsetY, homeY + maxOffsetY);
    }

    private int[] getPreviewDrawRect(PreviewBounds bounds) {
        float pad = previewScale * 0.75F;
        int halfW = Math.max(bounds.width() / 2, Math.round(PREVIEW_MODEL_WIDTH * previewScale * 0.5F + pad));
        int halfH = Math.max(bounds.height() / 2, Math.round(PREVIEW_MODEL_HEIGHT * previewScale * 0.5F + pad));
        int centerX = (bounds.left() + bounds.right()) / 2 + Math.round(previewOffsetX);
        int centerY = (bounds.top() + bounds.bottom()) / 2 + Math.round(previewOffsetY);
        return new int[] {
            centerX - halfW,
            centerY - halfH,
            centerX + halfW,
            centerY + halfH
        };
    }

    private void updateLoadButtonState() {
        if (loadButton != null && pathField != null) {
            String pathText = pathField.getValue().trim();
            boolean pathChanged = selectedPath == null || selectedPath.isBlank()
                || !pathText.equals(selectedPath.trim());
            loadButton.active = !pathText.isEmpty() && pathChanged && !fileDialogOpen;
        }
        if (chooseFileButton != null) {
            chooseFileButton.active = !fileDialogOpen;
        }
    }

    private void updateActionButtonState() {
        boolean hasTarget = targetPlayerName != null && !targetPlayerName.isBlank();
        boolean hasPreview = selectedPath != null && !selectedPath.isBlank() && previewSkin != null;
        if (applyButton != null) {
            applyButton.active = hasTarget && hasPreview && !isPreviewAlreadyApplied();
        }
        if (removeButton != null) {
            removeButton.active = hasTarget && clientConfig.getCustomSkinPath(targetPlayerName) != null;
        }
    }

    private boolean isPreviewAlreadyApplied() {
        if (targetPlayerName == null || targetPlayerName.isBlank() || selectedPath == null || selectedPath.isBlank()) {
            return false;
        }
        String appliedPath = clientConfig.getCustomSkinPath(targetPlayerName);
        if (appliedPath == null || appliedPath.isBlank()) {
            return false;
        }
        if (SkinLayerMask.sanitize(previewLayerMask) != clientConfig.getCustomSkinLayers(targetPlayerName)) {
            return false;
        }
        try {
            return Path.of(selectedPath).toAbsolutePath().normalize()
                .equals(Path.of(appliedPath).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return selectedPath.equals(appliedPath);
        }
    }

    private void drawDimBackground(GuiGraphics context) {
        context.fill(0, 0, width, height, 0xC0101010);
    }

    private void drawModalPanel(GuiGraphics context, int x, int y, int panelWidth, int panelHeight) {
        int panelColor = clientConfig != null ? clientConfig.getSkinPanelColor() : 0xFF2C2C2C;
        int borderColor = clientConfig != null ? clientConfig.getSkinBorderColor() : 0xFF4A4A4A;
        int dividerColor = clientConfig != null ? clientConfig.getSkinDividerColor() : 0x33FFFFFF;

        int modalBackground = panelColor;

        context.fill(x, y, x + panelWidth, y + panelHeight, modalBackground);

        context.fill(x, y, x + panelWidth, y + 1, borderColor);
        context.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, borderColor);
        context.fill(x, y, x + 1, y + panelHeight, borderColor);
        context.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, borderColor);

        context.fill(x, y + 1, x + panelWidth, y + 30, 0x30000000);
        context.fill(x, y + 30, x + panelWidth, y + 31, dividerColor);

        context.fill(x + 18, y + 96, x + panelWidth - 18, y + 97, dividerColor);
    }

    private void drawPreviewPanel(GuiGraphics context, PreviewBounds bounds) {
        int viewportColor = clientConfig != null ? clientConfig.getSkinViewportColor() : 0xFF111417;

        int viewportBg = viewportColor;
        context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), viewportBg);

        if (previewSkin == null) {
            Font renderer = this.font;
            Component hint = Component.literal("No skin PNG loaded yet");
            int textColor = clientConfig != null ? clientConfig.getSkinTextColor() : 0xCC808080;
            context.drawString(renderer, hint, bounds.left() + (bounds.width() - renderer.width(hint)) / 2, bounds.top() + bounds.height() / 2 - 4, textColor, false);
        }
    }

    private void setStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
    }

    private String truncateStatus(Font renderer, String message, int maxWidth) {
        if (renderer.width(message) <= maxWidth) {
            return message;
        }

        String ellipsis = "...";
        int end = message.length();
        int maxTextWidth = Math.max(0, maxWidth - renderer.width(ellipsis));
        while (end > 0 && renderer.width(message.substring(0, end)) > maxTextWidth) {
            end--;
        }
        return message.substring(0, end) + ellipsis;
    }

    private PreviewBounds getPreviewBounds() {
        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        return new PreviewBounds(
            panelX + 18,
            panelY + 116,
            panelX + panelWidth - 18,
            panelY + panelHeight - 88
        );
    }

    private int getPanelWidth() {
        return Math.min(PANEL_MAX_WIDTH, Math.max(1, width - SCREEN_MARGIN * 2));
    }

    private int getPanelHeight() {
        return Math.min(PANEL_MAX_HEIGHT, Math.max(1, height - SCREEN_MARGIN * 2));
    }

    private int getPanelX() {
        return (width - getPanelWidth()) / 2;
    }

    private int getPanelY() {
        return (height - getPanelHeight()) / 2;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private record PreviewBounds(int left, int top, int right, int bottom) {
        private boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        private int width() {
            return right - left;
        }

        private int height() {
            return bottom - top;
        }
    }
}
