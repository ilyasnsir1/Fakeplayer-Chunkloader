package de.chunkloader.client.screen;

import net.minecraft.client.texture.NativeImage;
import de.chunkloader.client.SkinLayerMask;
import de.chunkloader.client.config.ClientConfig;
import de.chunkloader.client.config.PanelColorTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
final class PanelColorEditorOverlay {
    private static final ButtonTextures VANILLA_BUTTON_TEXTURES = new ButtonTextures(
        Identifier.ofVanilla("widget/button"),
        Identifier.ofVanilla("widget/button_disabled"),
        Identifier.ofVanilla("widget/button_highlighted")
    );
    private static final int PALETTE_COLUMNS = 24;
    private static final int PALETTE_ROWS = 7;
    private static final int PALETTE_CELL_SIZE = 7;
    private static final int PALETTE_WIDTH = PALETTE_COLUMNS * PALETTE_CELL_SIZE;
    private static final int PALETTE_HEIGHT = PALETTE_ROWS * PALETTE_CELL_SIZE;
    private static final int CARD_WIDTH = 248;
    private static final int CARD_HEIGHT = 78;
    private static final int OPACITY_SLIDER_HEIGHT = 6;

    private static final int LAYER_CHEVRON_SIZE = 11;
    private static final int LAYER_ROW_HEIGHT = 12;
    private static final int LAYER_MENU_PAD = 3;
    private final ChunkMapScreen owner;
    private final ClientConfig config;
    private final EnumMap<PanelColorTarget, Integer> draftColors = new EnumMap<>(PanelColorTarget.class);
    private final Map<PanelColorTarget, Integer> draftColorsView = java.util.Collections.unmodifiableMap(draftColors);

    private PanelColorTarget currentTarget = null;
    private int currentPage = 1;
    private boolean opacitySliderDragging;
    private TextFieldWidget hexField;
    private boolean updatingHexFieldFromCode;
    private int focusedControl = -1;
    private Boolean unsavedChangesCache;

    private NativeImageBackedTexture horizontalPaletteTexture;
    private Identifier horizontalPaletteTextureId;
    private NativeImageBackedTexture verticalPaletteTexture;
    private Identifier verticalPaletteTextureId;
    private NativeImageBackedTexture checkerboardTexture;
    private Identifier checkerboardTextureId;
    private NativeImageBackedTexture opacityGradientTexture;
    private Identifier opacityGradientTextureId;
    private int cachedOpacityRgb = Integer.MIN_VALUE;
    private int cachedOpacityWidth = -1;
    private int cachedOpacityHeight = -1;
    private boolean cachedOpacityVertical;
    private boolean texturesInitialized;

    private static final SampleStatus[] SAMPLE_STATUSES = {
        new SampleStatus("Select a PNG file or enter a path.", PanelColorTarget.SKIN_TEXT),
        new SampleStatus("Opening file picker...", PanelColorTarget.SKIN_TEXT),
        new SampleStatus("File selection canceled.", PanelColorTarget.SKIN_STATUS_WARNING),
        new SampleStatus("Failed to open file picker.", PanelColorTarget.SKIN_STATUS_WARNING),
        new SampleStatus("Custom skin removed.", PanelColorTarget.SKIN_TEXT),
        new SampleStatus("Preview loaded (Standard arms).", PanelColorTarget.SKIN_STATUS_SUCCESS),
        new SampleStatus("Skin applied for Fakeplayer4.", PanelColorTarget.SKIN_STATUS_SUCCESS),
        new SampleStatus("Failed to load skin PNG: skin must be 64x64 or 64x32.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("Please enter the path to a PNG file.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("The specified path is invalid.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("Please select a PNG file.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("The selected file does not exist.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("Load a valid skin PNG first.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("The selected player is unavailable.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("The saved path is invalid.", PanelColorTarget.SKIN_STATUS_ERROR),
        new SampleStatus("Failed to apply skin: unable to read PNG.", PanelColorTarget.SKIN_STATUS_ERROR)
    };

    private int sampleStatusIndex = 0;
    private long lastStatusClickTime = 0L;

    private SampleStatus getSampleStatus() {
        int index = Math.floorMod(sampleStatusIndex, SAMPLE_STATUSES.length);
        return SAMPLE_STATUSES[index];
    }

    private String getSampleStatusText() {
        return getSampleStatus().text();
    }

    private PanelColorTarget getSampleStatusTarget() {
        return getSampleStatus().target();
    }

    private int getSampleStatusColor(int skinTextColor) {
        return switch (getSampleStatusTarget()) {
            case SKIN_STATUS_SUCCESS -> getDraftColor(PanelColorTarget.SKIN_STATUS_SUCCESS);
            case SKIN_STATUS_ERROR -> getDraftColor(PanelColorTarget.SKIN_STATUS_ERROR);
            case SKIN_STATUS_WARNING -> getDraftColor(PanelColorTarget.SKIN_STATUS_WARNING);
            default -> skinTextColor;
        };
    }

    int getCurrentPage() {
        return currentPage;
    }

    PanelColorEditorOverlay(ChunkMapScreen owner, ClientConfig config) {
        this.owner = owner;
        this.config = config;
        this.draftColors.putAll(config.getStoredPanelColors());
        initHexField();
    }

    private void initHexField() {
        EditorLayout layout = getLayout();
        Bounds hexBounds = layout.hexField();
        hexField = owner.addOverlayWidget(new TextFieldWidget(font(), hexBounds.left(), hexBounds.top(), hexBounds.width(), hexBounds.height(), Text.literal("Hex")));
        hexField.setMaxLength(7);
        hexField.setDrawsBackground(true);
        hexField.setPlaceholder(Text.literal("#RRGGBB").formatted(Formatting.GRAY));
        updateHexFieldValue();
        hexField.setChangedListener(text -> {
            if (updatingHexFieldFromCode) return;
            String s = text == null ? "" : text.trim();
            if (!s.startsWith("#")) {
                String fixed = "#" + s.replaceAll("[^0-9a-fA-F]", "");
                updatingHexFieldFromCode = true;
                int cursorPos = hexField.getCursor();
                hexField.setText(fixed);
                hexField.setSelectionStart(Math.max(1, cursorPos));
                updatingHexFieldFromCode = false;
                s = fixed;
            }
            if (currentTarget == null) {
                return;
            }
            int currentAlpha = alpha(getDraftColor(currentTarget));
            Integer color = parseHexColor(s, currentAlpha);
            if (color != null) {
                putDraftColor(currentTarget, color);
            }
        });
    }

    private void putDraftColor(PanelColorTarget target, int color) {
        draftColors.put(target, color);
        unsavedChangesCache = null;
    }

    private void updateHexFieldValue() {
        if (hexField != null) {
            updatingHexFieldFromCode = true;
            if (currentTarget == null) {
                hexField.setText("");
            } else {
                int color = getDraftColor(currentTarget);
                int rgb = color & 0x00FFFFFF;
                String formatted = String.format("#%06X", rgb);
                hexField.setText(formatted);
            }
            updatingHexFieldFromCode = false;
        }
    }

    private void updateFocusState() {
        if (hexField != null) {
            boolean isHex = (focusedControl == 0);
            boolean wasFocused = hexField.isFocused();
            hexField.setFocused(isHex);
            if (isHex) {
                owner.setFocused(hexField);
                ensureHexPrefixOnFocus();
            } else if (owner.getFocused() == hexField) {
                owner.setFocused(null);
            }
            if (wasFocused && !isHex) {
                clearIncompleteHexOnBlur();
            }
        }
    }

    private void ensureHexPrefixOnFocus() {
        if (hexField == null || updatingHexFieldFromCode) {
            return;
        }
        String value = hexField.getText();
        if (value == null || value.isBlank()) {
            updatingHexFieldFromCode = true;
            hexField.setText("#");
            hexField.setCursor(1, false);
            updatingHexFieldFromCode = false;
        } else if (!value.startsWith("#")) {
            updatingHexFieldFromCode = true;
            hexField.setText("#" + value.replaceAll("[^0-9a-fA-F]", ""));
            hexField.setCursor(Math.min(hexField.getText().length(), Math.max(1, hexField.getCursor() + 1)), false);
            updatingHexFieldFromCode = false;
        }
    }

    private void clearIncompleteHexOnBlur() {
        if (hexField == null) {
            return;
        }
        String value = hexField.getText() == null ? "" : hexField.getText().trim();
        if (value.isEmpty() || "#".equals(value)) {
            updatingHexFieldFromCode = true;
            hexField.setText("");
            updatingHexFieldFromCode = false;
        } else if (currentTarget != null) {
            updateHexFieldValue();
        }
    }

    Map<PanelColorTarget, Integer> getDraftColors() {
        return draftColorsView;
    }

    int getCardRight() {
        return getLayout().cardRight();
    }

    int getCardBottom() {
        return getLayout().cardBottom();
    }

    void render(DrawContext context, int mouseX, int mouseY) {
        ensureTextures();
        EditorLayout layout = getLayout();

        if (currentPage == 2) {
            drawSkinScreenPreview(context, mouseX, mouseY);
        }

        ChunkMapScreen.PanelColorTargetHit hoveredTarget = owner.getPanelColorTargetHit(mouseX, mouseY);
        PanelColorTarget highlightTarget = null;
        if (hoveredTarget != null && hoveredTarget.target().getPage() == currentPage) {
            highlightTarget = hoveredTarget.target();
        } else if (currentTarget != null && currentTarget.getPage() == currentPage) {
            highlightTarget = currentTarget;
        }

        if (highlightTarget != null && currentPage == 1) {
            List<ChunkMapScreen.PanelColorTargetHit> linkedHits = owner.collectLinkedPanelColorHits(highlightTarget);
            if (linkedHits.isEmpty() && hoveredTarget != null) {
                linkedHits = List.of(hoveredTarget);
            }
            for (ChunkMapScreen.PanelColorTargetHit hit : linkedHits) {
                if (hit.target() == PanelColorTarget.DIVIDER) {
                    fillArgb(context, hit.left(), hit.top(), hit.right(), hit.bottom(), 0xFFE9C46A);
                } else if (hit.target() == PanelColorTarget.FRAME) {
                    drawOutline(context, hit.left(), hit.top(), hit.right(), hit.bottom(), 0xFFE9C46A);
                    drawOutline(context, hit.left() + 1, hit.top() + 1, hit.right() - 1, hit.bottom() - 1, 0xFFE9C46A);
                } else {
                    drawOutline(context, hit.left(), hit.top(), hit.right(), hit.bottom(), 0xFFE9C46A);
                }
            }
        }

        Integer currentColor = currentTarget != null ? getDraftColor(currentTarget) : null;
        Text targetLabel = currentTarget != null
            ? Text.literal("Color: " + currentTarget.getDisplayName() + " (A:" + alpha(currentColor) + ")").formatted(Formatting.WHITE)
            : Text.literal("Select a UI element").formatted(Formatting.GRAY);

        if (layout.vertical()) {
            if (currentTarget == null) {
                Bounds hexBounds = layout.hexField();
                int labelX = hexBounds.left() + (hexBounds.width() - font().getWidth(targetLabel)) / 2;
                int labelY = hexBounds.top() - font().fontHeight - 3;
                context.drawText(font(), targetLabel, labelX, labelY, 0xFFAAAAAA, false);
            } else {
                context.drawText(font(), Text.literal("Color: (A:" + alpha(currentColor) + ")").formatted(Formatting.GRAY), layout.cardLeft(), layout.cardTop() + 2, 0xFFAAAAAA, false);
                String[] words = currentTarget.getDisplayName().split(" ");
                int curY = layout.cardTop() + 2 + font().fontHeight + 1;
                for (String word : words) {
                    context.drawText(font(), Text.literal(word).formatted(Formatting.WHITE), layout.cardLeft(), curY, 0xFFFFFFFF, false);
                    curY += font().fontHeight + 1;
                }
            }
        } else {
            int paletteWidth = layout.paletteGridCols() * layout.paletteCellWidth();
            int labelX = layout.paletteLeft() + (paletteWidth - font().getWidth(targetLabel)) / 2;

            int labelY = Math.max(layout.cardTop(), (layout.paletteTop() - font().fontHeight) / 2);
            context.drawText(font(), targetLabel, labelX, labelY, 0xFFFFFFFF, false);
        }

        int arrowW = 30;
        int arrowH = 50;
        int arrowY = (owner.height - arrowH) / 2;
        int leftArrowX = 2;
        int rightArrowX = owner.width - 2 - arrowW;

        boolean leftHovered = mouseX >= leftArrowX && mouseX < leftArrowX + arrowW && mouseY >= arrowY && mouseY < arrowY + arrowH;
        boolean rightHovered = mouseX >= rightArrowX && mouseX < rightArrowX + arrowW && mouseY >= arrowY && mouseY < arrowY + arrowH;

        int leftColor = leftHovered ? 0xFFFFDD55 : 0xBBFFFFFF;
        context.drawText(font(), Text.literal("<").formatted(Formatting.BOLD), leftArrowX + (arrowW - font().getWidth("<")) / 2, arrowY + (arrowH - font().fontHeight) / 2, leftColor, false);

        int rightColor = rightHovered ? 0xFFFFDD55 : 0xBBFFFFFF;
        context.drawText(font(), Text.literal(">").formatted(Formatting.BOLD), rightArrowX + (arrowW - font().getWidth(">")) / 2, arrowY + (arrowH - font().fontHeight) / 2, rightColor, false);

        Bounds helpBounds = getHelpMarkBounds();
        boolean helpHovered = helpBounds.contains(mouseX, mouseY);
        int helpColor = helpHovered ? 0xFFFFDD55 : 0xBBFFFFFF;
        context.drawText(
            font(),
            Text.literal("?"),
            helpBounds.left(),
            helpBounds.top(),
            helpColor,
            false
        );

        renderPalette(context, mouseX, mouseY, layout);
        if (currentColor != null) {
            renderOpacitySlider(context, layout, currentColor);
        }
        renderActionButton(context, layout.saveButton(), "Save", hasUnsavedChanges(), mouseX, mouseY, focusedControl == 1);
        renderActionButton(context, layout.resetButton(), getResetButtonLabel(), canReset(), mouseX, mouseY, focusedControl == 2);
        renderActionButton(context, layout.cancelButton(), "Back", true, mouseX, mouseY, focusedControl == 3);

        if (hexField != null) {
            Bounds hexBounds = layout.hexField();
            hexField.setX(hexBounds.left());
            hexField.setY(hexBounds.top());
            hexField.setWidth(hexBounds.width());
            hexField.setHeight(hexBounds.height());
            hexField.render(context, mouseX, mouseY, 0.0F);
        }
    }

    boolean mouseClicked(Click click) {
        if (click.button() != 0) {
            return true;
        }

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        EditorLayout layout = getLayout();

        if (getHelpMarkBounds().contains(mouseX, mouseY)) {
            MinecraftClient.getInstance().setScreen(new PanelColorHelpScreen(owner));
            return true;
        }

        if (hexField != null && hexField.isMouseOver(mouseX, mouseY)) {
            focusedControl = 0;
            updateFocusState();
            hexField.mouseClicked(click, false);
            return true;
        } else if (hexField != null) {
            if (focusedControl == 0) {
                focusedControl = -1;
            }
            updateFocusState();
        }

        Integer paletteColor = getPaletteColorAt(mouseX, mouseY, layout);
        if (paletteColor != null) {
            if (currentTarget != null) {
                int alpha = alpha(getDraftColor(currentTarget));
                putDraftColor(currentTarget, (alpha << 24) | (paletteColor & 0x00FFFFFF));
                updateHexFieldValue();
            }
            return true;
        }

        if (layout.opacitySlider().contains(mouseX, mouseY)) {
            if (currentTarget != null) {
                setOpacityFromSlider(mouseX, mouseY, layout);
                opacitySliderDragging = true;
            }
            return true;
        }

        if (layout.saveButton().contains(mouseX, mouseY)) {
            if (!hasUnsavedChanges()) {
                return true;
            }
            focusedControl = 1;
            updateFocusState();
            config.savePanelColors(draftColors);
            unsavedChangesCache = false;
            owner.closePanelColorEditor();
            return true;
        }

        if (layout.resetButton().contains(mouseX, mouseY)) {
            if (!canReset()) {
                return true;
            }
            focusedControl = 2;
            updateFocusState();
            performReset();
            return true;
        }

        if (layout.cancelButton().contains(mouseX, mouseY)) {
            focusedControl = 3;
            updateFocusState();
            owner.closePanelColorEditor();
            return true;
        }

        int arrowW = 30;
        int arrowH = 50;
        int arrowY = (owner.height - arrowH) / 2;
        int leftArrowX = 8;
        int rightArrowX = owner.width - 8 - arrowW;

        if ((mouseX >= leftArrowX && mouseX < leftArrowX + arrowW && mouseY >= arrowY && mouseY < arrowY + arrowH) ||
            (mouseX >= rightArrowX && mouseX < rightArrowX + arrowW && mouseY >= arrowY && mouseY < arrowY + arrowH)) {
            currentPage = currentPage == 1 ? 2 : 1;
            currentTarget = null;
            updateHexFieldValue();
            return true;
        }

        if (currentPage == 2) {
            PanelColorTarget skinTarget = getSkinPreviewTargetAt(mouseX, mouseY);
            if (skinTarget != null) {
                int panelHeight = Math.min(370, Math.max(1, owner.height - 24));
                int panelY = (owner.height - panelHeight) / 2;
                int statusY = panelY + panelHeight - 58;
                String statusText = getSampleStatusText();
                int statusW = font().getWidth(statusText);
                int panelX = getPreviewPanelX(getPreviewPanelWidth());

                boolean isStatusClicked = (mouseX >= panelX + 14 && mouseX <= panelX + 18 + statusW + 4 &&
                    mouseY >= statusY - 4 && mouseY <= statusY + 12);

                if (isStatusClicked) {
                    long now = System.currentTimeMillis();
                    if (now - lastStatusClickTime < 350L) {
                        sampleStatusIndex = (sampleStatusIndex + 1) % SAMPLE_STATUSES.length;
                        lastStatusClickTime = 0L;
                        skinTarget = getSampleStatusTarget();
                    } else {
                        lastStatusClickTime = now;
                    }
                }

                currentTarget = skinTarget;
                updateHexFieldValue();
                return true;
            }
        }

        ChunkMapScreen.PanelColorTargetHit targetHit = owner.getPanelColorTargetHit(mouseX, mouseY);
        if (targetHit != null && targetHit.target().getPage() == currentPage) {
            currentTarget = targetHit.target();
            updateHexFieldValue();
        } else {
            currentTarget = null;
            updateHexFieldValue();
        }
        return true;
    }

    boolean mouseDragged(Click click) {
        if (opacitySliderDragging) {
            setOpacityFromSlider((int) click.x(), (int) click.y(), getLayout());
        }
        return true;
    }

    boolean mouseReleased(Click click) {
        opacitySliderDragging = false;
        return true;
    }

    boolean keyPressed(KeyInput keyInput) {
        int key = keyInput.key();
        if (key == 256) {
            owner.closePanelColorEditor();
            return true;
        }

        if (key == 258) {
            boolean shift = keyInput.hasShift();
            if (focusedControl == -1) {
                focusedControl = shift ? 3 : 0;
            } else if (shift) {
                focusedControl--;
                if (focusedControl < 0) {
                    focusedControl = 3;
                }
            } else {
                focusedControl++;
                if (focusedControl > 3) {
                    focusedControl = 0;
                }
            }
            if (focusedControl == 1 && !hasUnsavedChanges()) {
                focusedControl = shift ? 0 : 2;
            }
            if (focusedControl == 2 && !canReset()) {
                focusedControl = shift ? (hasUnsavedChanges() ? 1 : 0) : 3;
            }
            updateFocusState();
            return true;
        }

        if ((key == 257 || key == 32) && focusedControl > 0) {
            if (focusedControl == 1 && hasUnsavedChanges()) {
                config.savePanelColors(draftColors);
                unsavedChangesCache = false;
                owner.closePanelColorEditor();
                return true;
            } else if (focusedControl == 2 && canReset()) {
                performReset();
                return true;
            } else if (focusedControl == 3) {
                owner.closePanelColorEditor();
                return true;
            }
        }

        if (hexField != null && hexField.isFocused()) {
            if (key == 259 && hexField.getCursor() <= 1) {
                return true;
            }
            if (key == 257) {
                if (currentTarget == null) {
                    return true;
                }
                int currentAlpha = alpha(getDraftColor(currentTarget));
                Integer color = parseHexColor(hexField.getText(), currentAlpha);
                if (color != null) {
                    putDraftColor(currentTarget, color);
                }
                focusedControl = hasUnsavedChanges() ? 1 : 2;
                updateFocusState();
                return true;
            }
            return hexField.keyPressed(keyInput);
        }

        if (key == 265 || key == 262) {
            adjustAlpha(1);
            return true;
        } else if (key == 264 || key == 263) {
            adjustAlpha(-1);
            return true;
        }

        return true;
    }

    boolean charTyped(char chr, int modifiers) {
        if (hexField != null && focusedControl == 0) {
            String cur = hexField.getText() == null ? "" : hexField.getText();
            if (chr == 8 || chr == 127) {
                if (!cur.isEmpty()) hexField.setText(cur.substring(0, cur.length() - 1));
                return true;
            }
            if (cur.length() < 7 && "#0123456789abcdefABCDEF".indexOf(chr) >= 0) {
                hexField.setText(cur + chr);
                return true;
            }
        }
        return false;
    }

    private void renderPalette(DrawContext context, int mouseX, int mouseY, EditorLayout layout) {
        int cellW = layout.paletteCellWidth();
        int cellH = layout.paletteCellHeight();
        int pixelWidth = layout.paletteGridCols() * cellW;
        int pixelHeight = layout.paletteGridRows() * cellH;

        fillArgb(
            context,
            layout.paletteLeft() - 1,
            layout.paletteTop() - 1,
            layout.paletteLeft() + pixelWidth + 1,
            layout.paletteTop() + pixelHeight + 1,
            0xFF080A0C
        );

        Identifier textureId = layout.vertical() ? verticalPaletteTextureId : horizontalPaletteTextureId;
        int texW = layout.vertical() ? PALETTE_ROWS : PALETTE_COLUMNS;
        int texH = layout.vertical() ? PALETTE_COLUMNS : PALETTE_ROWS;
        if (textureId != null && pixelWidth > 0 && pixelHeight > 0) {
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                layout.paletteLeft(),
                layout.paletteTop(),
                0.0f,
                0.0f,
                pixelWidth,
                pixelHeight,
                texW,
                texH,
                texW,
                texH
            );
        }

        Integer hoveredColor = getPaletteColorAt(mouseX, mouseY, layout);
        if (hoveredColor != null) {
            int gridCol = (mouseX - layout.paletteLeft()) / cellW;
            int gridRow = (mouseY - layout.paletteTop()) / cellH;
            int left = layout.paletteLeft() + gridCol * cellW;
            int top = layout.paletteTop() + gridRow * cellH;
            drawOutline(context, left, top, left + cellW, top + cellH, 0xFFFFFFFF);
        }
    }

    private void renderOpacitySlider(DrawContext context, EditorLayout layout, int color) {
        Bounds slider = layout.opacitySlider();
        int rgb = color & 0x00FFFFFF;
        ensureOpacityGradientTexture(rgb, slider.width(), slider.height(), layout.vertical());

        blitCheckerboardTiled(
            context,
            slider.left(),
            slider.top(),
            slider.width(),
            slider.height()
        );
        blitTexture(
            context,
            opacityGradientTextureId,
            slider.left(),
            slider.top(),
            slider.width(),
            slider.height(),
            slider.width(),
            slider.height()
        );
        drawOutline(context, slider.left(), slider.top(), slider.right(), slider.bottom(), 0xFF080A0C);

        int currentAlpha = alpha(color);
        if (layout.vertical()) {
            int knobY = slider.top() + Math.round((1.0F - (currentAlpha / 255.0F)) * (slider.height() - 1));
            int kY = clamp(knobY - 1, slider.top(), slider.bottom() - 3);
            fillArgb(context, slider.left() - 1, kY, slider.right() + 1, kY + 3, 0xFFFFFFFF);
            drawOutline(context, slider.left() - 1, kY, slider.right() + 1, kY + 3, 0xFF000000);
        } else {
            int knobX = slider.left() + Math.round((currentAlpha / 255.0F) * (slider.width() - 1));
            int kX = clamp(knobX - 1, slider.left(), slider.right() - 3);
            fillArgb(context, kX, slider.top() - 1, kX + 3, slider.bottom() + 1, 0xFFFFFFFF);
            drawOutline(context, kX, slider.top() - 1, kX + 3, slider.bottom() + 1, 0xFF000000);
        }
    }

    private int getPreviewPanelWidth() {
        int overlayRight = getLayout().cardRight();
        int maxW = 460;
        int availableW = owner.width - overlayRight - 30;
        return Math.min(maxW, Math.max(260, availableW));
    }

    private int getPreviewPanelX(int panelWidth) {
        int overlayRight = getLayout().cardRight();
        int availableW = owner.width - overlayRight - 30;
        return overlayRight + 20 + Math.max(0, (availableW - panelWidth) / 2);
    }

    private void renderVanillaButton(
        DrawContext context,
        int x1,
        int y1,
        int x2,
        int y2,
        String text,
        boolean active,
        int mouseX,
        int mouseY
    ) {
        boolean hovered = active && mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
        Identifier texture = VANILLA_BUTTON_TEXTURES.get(active, hovered);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
        int foreground = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        int textX = x1 + (Math.max(1, x2 - x1) - font().getWidth(text)) / 2;
        int textY = y1 + (Math.max(1, y2 - y1) - font().fontHeight) / 2 + 1;
        context.drawText(font(), Text.literal(text), textX, textY, foreground, false);
    }

    private PanelColorTarget getSkinPreviewTargetAt(int mouseX, int mouseY) {
        if (currentPage != 2) return null;

        int panelWidth = getPreviewPanelWidth();
        int panelHeight = Math.min(370, Math.max(1, owner.height - 24));
        int panelX = getPreviewPanelX(panelWidth);
        int panelY = (owner.height - panelHeight) / 2;

        if (mouseX < panelX || mouseX >= panelX + panelWidth || mouseY < panelY || mouseY >= panelY + panelHeight) {
            return null;
        }

        Text titleComp = Text.literal("Select Skin").formatted(Formatting.BOLD);
        int titleW = font().getWidth(titleComp);
        int titleX = panelX + (panelWidth - titleW) / 2 + 1;
        int titleY = panelY + 12;
        if (mouseX >= titleX - 4 && mouseX <= titleX + titleW + 4 && mouseY >= titleY - 2 && mouseY < titleY + font().fontHeight + 2) {
            return PanelColorTarget.SKIN_TITLE;
        }

        if ((mouseY >= panelY + 29 && mouseY <= panelY + 31) || (mouseY >= panelY + 95 && mouseY <= panelY + 97)) {
            return PanelColorTarget.SKIN_DIVIDER;
        }

        int playerW = font().getWidth("Player: Fakeplayer4");
        if (mouseX >= panelX + 14 && mouseX <= panelX + 18 + playerW + 4 && mouseY >= panelY + 33 && mouseY < panelY + 47) {
            return PanelColorTarget.SKIN_PLAYER_NAME;
        }

        int rowX = panelX + 18;
        int rowWidth = panelWidth - 36;
        int fileFieldY = panelY + 64;
        int chooseWidth = 98;
        int loadWidth = 58;
        int fieldWidth = rowWidth - chooseWidth - loadWidth - 16;

        if (mouseX >= rowX && mouseX < rowX + fieldWidth && mouseY >= fileFieldY && mouseY < fileFieldY + 20) {
            if (mouseX < rowX + 2 || mouseX >= rowX + fieldWidth - 2 || mouseY < fileFieldY + 2 || mouseY >= fileFieldY + 18) {
                return PanelColorTarget.SKIN_SEARCHBAR_BORDER;
            }
            int pathLabelW = font().getWidth("Path to a skin PNG");
            int textW = font().getWidth("/custom.png");

            if (mouseX >= rowX + 2 && mouseX <= rowX + 4 + pathLabelW + 1) {
                return PanelColorTarget.SKIN_SEARCHBAR_PLACEHOLDER;
            }
            if (mouseX > rowX + 4 + pathLabelW + 1 && mouseX <= rowX + 6 + pathLabelW + textW + 3) {
                return PanelColorTarget.SKIN_SEARCHBAR_TEXT;
            }
            return PanelColorTarget.SKIN_SEARCHBAR_BG;
        }

        int previewLeft = panelX + 18;
        int previewTop = panelY + 116;
        int previewRight = panelX + panelWidth - 18;
        int previewBottom = panelY + panelHeight - 88;
        int previewWidth = previewRight - previewLeft;
        int previewHeight = previewBottom - previewTop;

        int pngW = font().getWidth("Skin PNG");
        if (mouseX >= panelX + 14 && mouseX <= panelX + 18 + pngW + 4 && mouseY >= panelY + 48 && mouseY < panelY + 62) {
            return PanelColorTarget.SKIN_TEXT;
        }

        int prevW = font().getWidth("3D Preview");
        if (mouseX >= previewLeft - 6 && mouseX <= previewLeft + prevW + 4 && mouseY >= previewTop - 18 && mouseY <= previewTop - 1) {
            return PanelColorTarget.SKIN_TEXT;
        }

        PanelColorTarget layerTarget = getSkinLayerPreviewTargetAt(mouseX, mouseY, previewLeft, previewTop, previewWidth);
        if (layerTarget != null) {
            return layerTarget;
        }

        int hintW = font().getWidth("No skin PNG loaded yet");
        int hintX1 = previewLeft + (previewWidth - hintW) / 2 - 4;
        int hintX2 = previewLeft + (previewWidth + hintW) / 2 + 4;
        int hintY1 = previewTop + previewHeight / 2 - 8;
        int hintY2 = previewTop + previewHeight / 2 + 8;
        if (mouseX >= hintX1 && mouseX <= hintX2 && mouseY >= hintY1 && mouseY <= hintY2) {
            return PanelColorTarget.SKIN_TEXT;
        }

        int cw = font().getWidth("Drag: rotate  |  Alt/MMB+drag: pan  |  Scroll: zoom  |  Double click: reset");
        int cx1 = previewLeft + (previewWidth - cw) / 2 - 4;
        int cx2 = previewLeft + (previewWidth + cw) / 2 + 4;
        if (mouseX >= cx1 && mouseX <= cx2 && mouseY >= previewBottom + 2 && mouseY <= previewBottom + 20) {
            return PanelColorTarget.SKIN_TEXT;
        }

        int statusY = panelY + panelHeight - 58;
        int statusW = font().getWidth(getSampleStatusText());
        if (mouseX >= panelX + 14 && mouseX <= panelX + 18 + statusW + 4 && mouseY >= statusY - 4 && mouseY <= statusY + 12) {
            return getSampleStatusTarget();
        }

        if (mouseX >= previewLeft && mouseX < previewRight && mouseY >= previewTop && mouseY < previewBottom) {
            return PanelColorTarget.SKIN_VIEWPORT;
        }

        if (mouseX < panelX + 2 || mouseX >= panelX + panelWidth - 2 || mouseY < panelY + 2 || mouseY >= panelY + panelHeight - 2) {
            return PanelColorTarget.SKIN_BORDER;
        }

        return PanelColorTarget.SKIN_PANEL;
    }

    private void drawSkinScreenPreview(DrawContext context, int mouseX, int mouseY) {
        int panelWidth = getPreviewPanelWidth();
        int panelHeight = Math.min(370, Math.max(1, owner.height - 24));
        int panelX = getPreviewPanelX(panelWidth);
        int panelY = (owner.height - panelHeight) / 2;

        int skinPanelColor = getDraftColor(PanelColorTarget.SKIN_PANEL);
        int skinBorderColor = getDraftColor(PanelColorTarget.SKIN_BORDER);
        int skinDividerColor = getDraftColor(PanelColorTarget.SKIN_DIVIDER);
        int skinTitleColor = getDraftColor(PanelColorTarget.SKIN_TITLE);
        int skinPlayerNameColor = getDraftColor(PanelColorTarget.SKIN_PLAYER_NAME);
        int skinPathBgColor = getDraftColor(PanelColorTarget.SKIN_SEARCHBAR_BG);
        int skinPathBorderColor = getDraftColor(PanelColorTarget.SKIN_SEARCHBAR_BORDER);
        int skinPathTextColor = getDraftColor(PanelColorTarget.SKIN_SEARCHBAR_TEXT);
        int skinPathPlaceholderColor = getDraftColor(PanelColorTarget.SKIN_SEARCHBAR_PLACEHOLDER);
        int skinViewportColor = getDraftColor(PanelColorTarget.SKIN_VIEWPORT);
        int skinTextColor = getDraftColor(PanelColorTarget.SKIN_TEXT);

        int modalBg = skinPanelColor;

        fillArgb(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, modalBg);
        drawOutline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, skinBorderColor);

        fillArgb(context, panelX, panelY + 1, panelX + panelWidth, panelY + 30, 0x30000000);
        fillArgb(context, panelX, panelY + 30, panelX + panelWidth, panelY + 31, skinDividerColor);
        fillArgb(context, panelX + 18, panelY + 96, panelX + panelWidth - 18, panelY + 97, skinDividerColor);

        Text title = Text.literal("Select Skin").formatted(Formatting.BOLD);
        int titleX = panelX + (panelWidth - font().getWidth(title)) / 2 + 1;
        int titleY = panelY + 12;
        context.drawText(font(), title, titleX, titleY, skinTitleColor, false);

        context.drawText(font(), Text.literal("Player: Fakeplayer4"), panelX + 18, panelY + 35, skinPlayerNameColor, false);
        context.drawText(font(), Text.literal("Skin PNG"), panelX + 18, panelY + 52, skinTextColor, false);

        int rowX = panelX + 18;
        int rowWidth = panelWidth - 36;
        int fileFieldY = panelY + 64;
        int chooseWidth = 98;
        int loadWidth = 58;
        int fieldWidth = rowWidth - chooseWidth - loadWidth - 16;

        fillArgb(context, rowX, fileFieldY, rowX + fieldWidth, fileFieldY + 20, skinPathBgColor);
        drawOutline(context, rowX, fileFieldY, rowX + fieldWidth, fileFieldY + 20, skinPathBorderColor);
        context.drawText(font(), Text.literal("Path to a skin PNG"), rowX + 4, fileFieldY + 7, skinPathPlaceholderColor, false);
        int pathLabelW = font().getWidth("Path to a skin PNG");
        context.drawText(font(), Text.literal("/custom.png"), rowX + 6 + pathLabelW, fileFieldY + 7, skinPathTextColor, false);

        renderVanillaButton(context, rowX + fieldWidth + 8, fileFieldY, rowX + fieldWidth + 8 + loadWidth, fileFieldY + 20, "Load", true, -999, -999);
        renderVanillaButton(context, rowX + fieldWidth + loadWidth + 16, fileFieldY, rowX + fieldWidth + loadWidth + 16 + chooseWidth, fileFieldY + 20, "Choose PNG", true, -999, -999);

        int previewLeft = panelX + 18;
        int previewTop = panelY + 116;
        int previewRight = panelX + panelWidth - 18;
        int previewBottom = panelY + panelHeight - 88;
        int previewWidth = previewRight - previewLeft;
        int previewHeight = previewBottom - previewTop;

        context.drawText(font(), Text.literal("3D Preview"), previewLeft, previewTop - 13, skinTextColor, false);

        int viewportBg = skinViewportColor;
        fillArgb(context, previewLeft, previewTop, previewRight, previewBottom, viewportBg);

        Text hint = Text.literal("No skin PNG loaded yet");
        context.drawText(font(), hint, previewLeft + (previewWidth - font().getWidth(hint)) / 2, previewTop + previewHeight / 2 - 4, skinTextColor, false);

        drawSkinLayerPreview(context, previewLeft, previewTop, previewWidth);
        Text controlsHint = Text.literal("Drag: rotate  |  Alt/MMB+drag: pan  |  Scroll: zoom  |  Double click: reset");
        context.drawText(font(), controlsHint, previewLeft + (previewWidth - font().getWidth(controlsHint)) / 2, previewBottom + 8, skinTextColor, false);

        int statusY = panelY + panelHeight - 58;
        String statusText = getSampleStatusText();
        int statusW = font().getWidth(statusText);
        int currentStatusColor = getSampleStatusColor(skinTextColor);
        context.drawText(font(), Text.literal(statusText), panelX + 18, statusY, currentStatusColor, false);

        boolean statusHovered = mouseX >= panelX + 14 && mouseX <= panelX + 18 + statusW + 4
            && mouseY >= statusY - 4 && mouseY <= statusY + 12;
        if (statusHovered) {
            String cycleHint = "Double-click to cycle";
            int hintW = font().getWidth(cycleHint);
            int hintX = panelX + 18 + statusW + 10;
            int hintMaxRight = panelX + panelWidth - 18;
            if (hintX + hintW <= hintMaxRight) {
                context.drawText(font(), Text.literal(cycleHint), hintX, statusY, 0x99E9C46A, false);
            }
        }

        int actionWidth = 100;
        int actionGap = 8;
        int actionY = panelY + panelHeight - 36;
        int actionStartX = panelX + (panelWidth - (actionWidth * 3 + actionGap * 2)) / 2;

        renderVanillaButton(context, actionStartX, actionY, actionStartX + actionWidth, actionY + 20, "Apply", false, -999, -999);
        renderVanillaButton(context, actionStartX + actionWidth + actionGap, actionY, actionStartX + actionWidth * 2 + actionGap, actionY + 20, "Remove", false, -999, -999);
        renderVanillaButton(context, actionStartX + (actionWidth + actionGap) * 2, actionY, actionStartX + actionWidth * 3 + actionGap * 2, actionY + 20, "Back", true, -999, -999);

        PanelColorTarget hoveredSkinTarget = getSkinPreviewTargetAt(mouseX, mouseY);
        if (hoveredSkinTarget == null && currentTarget != null && currentTarget.getPage() == 2) {
            hoveredSkinTarget = currentTarget;
        }
        if (hoveredSkinTarget != null) {
            switch (hoveredSkinTarget) {
                case SKIN_TITLE -> {
                    Text tComp = Text.literal("Select Skin").formatted(Formatting.BOLD);
                    int tw = font().getWidth(tComp);
                    int tx = panelX + (panelWidth - tw) / 2 + 1;
                    int ty = panelY + 12;
                    drawOutline(context, tx - 4, ty - 2, tx + tw + 4, ty + font().fontHeight + 2, 0xFFE9C46A);
                }
                case SKIN_DIVIDER -> {
                    fillArgb(context, panelX, panelY + 30, panelX + panelWidth, panelY + 31, 0xFFE9C46A);
                    fillArgb(context, panelX + 18, panelY + 96, panelX + panelWidth - 18, panelY + 97, 0xFFE9C46A);
                }
                case SKIN_PLAYER_NAME -> drawOutline(context, panelX + 15, panelY + 32, panelX + 18 + font().getWidth("Player: Fakeplayer4") + 4, panelY + 46, 0xFFE9C46A);
                case SKIN_SEARCHBAR_BG -> drawOutline(context, rowX + 1, fileFieldY + 1, rowX + fieldWidth - 1, fileFieldY + 19, 0xFFE9C46A);
                case SKIN_SEARCHBAR_BORDER -> drawOutline(context, rowX, fileFieldY, rowX + fieldWidth, fileFieldY + 20, 0xFFE9C46A);
                case SKIN_SEARCHBAR_PLACEHOLDER -> {
                    int plW = font().getWidth("Path to a skin PNG");
                    drawOutline(context, rowX + 2, fileFieldY + 2, rowX + 4 + plW + 1, fileFieldY + 18, 0xFFE9C46A);
                }
                case SKIN_SEARCHBAR_TEXT -> {
                    int plW = font().getWidth("Path to a skin PNG");
                    int txtW = font().getWidth("/custom.png");
                    drawOutline(context, rowX + 5 + plW, fileFieldY + 2, rowX + 6 + plW + txtW + 3, fileFieldY + 18, 0xFFE9C46A);
                }
                case SKIN_VIEWPORT -> drawOutline(context, previewLeft, previewTop, previewRight, previewBottom, 0xFFE9C46A);
                case SKIN_LAYER_CHEVRON_BG -> {
                    LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
                    drawOutline(context, layer.chevronLeft(), layer.chevronTop(), layer.chevronRight(), layer.chevronBottom(), 0xFFE9C46A);
                }
                case SKIN_LAYER_CHEVRON -> {
                    LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
                    drawOutline(
                        context,
                        layer.chevronLeft() + 2,
                        layer.chevronTop() + 2,
                        layer.chevronRight() - 2,
                        layer.chevronBottom() - 2,
                        0xFFE9C46A
                    );
                }
                case SKIN_LAYER_MENU_BG -> {
                    LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
                    drawOutline(context, layer.menuLeft(), layer.menuTop(), layer.menuRight(), layer.menuBottom(), 0xFFE9C46A);
                }
                case SKIN_LAYER_ACTIVE -> {
                    LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
                    for (int i = 0; i < SkinLayerMask.EDITABLE_PARTS.length; i++) {
                        if (i % 2 == 0) {
                            int rowTop = layer.menuTop() + 2 + i * LAYER_ROW_HEIGHT;
                            drawOutline(context, layer.menuLeft() + 1, rowTop, layer.menuRight() - 1, rowTop + LAYER_ROW_HEIGHT, 0xFFE9C46A);
                        }
                    }
                }
                case SKIN_LAYER_INACTIVE -> {
                    LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
                    for (int i = 0; i < SkinLayerMask.EDITABLE_PARTS.length; i++) {
                        if (i % 2 == 1) {
                            int rowTop = layer.menuTop() + 2 + i * LAYER_ROW_HEIGHT;
                            drawOutline(context, layer.menuLeft() + 1, rowTop, layer.menuRight() - 1, rowTop + LAYER_ROW_HEIGHT, 0xFFE9C46A);
                        }
                    }
                }
                case SKIN_BORDER -> drawOutline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFE9C46A);
                case SKIN_STATUS_SUCCESS, SKIN_STATUS_ERROR, SKIN_STATUS_WARNING -> {
                    drawOutline(context, panelX + 15, statusY - 3, panelX + 18 + statusW + 4, statusY + 11, 0xFFE9C46A);
                }
                case SKIN_TEXT -> {
                    int pngW = font().getWidth("Skin PNG");
                    int prevW = font().getWidth("3D Preview");
                    int cw = font().getWidth("Drag: rotate  |  Alt/MMB+drag: pan  |  Scroll: zoom  |  Double click: reset");
                    int hw = font().getWidth("No skin PNG loaded yet");
                    int cx1 = previewLeft + (previewWidth - cw) / 2 - 4;
                    int cx2 = previewLeft + (previewWidth + cw) / 2 + 4;
                    int hx1 = previewLeft + (previewWidth - hw) / 2 - 4;
                    int hx2 = previewLeft + (previewWidth + hw) / 2 + 4;
                    int hy1 = previewTop + previewHeight / 2 - 7;
                    int hy2 = previewTop + previewHeight / 2 + 7;

                    drawOutline(context, panelX + 15, panelY + 49, panelX + 18 + pngW + 4, panelY + 63, 0xFFE9C46A);
                    drawOutline(context, previewLeft - 4, previewTop - 16, previewLeft + prevW + 2, previewTop - 2, 0xFFE9C46A);
                    drawOutline(context, cx1, previewBottom + 5, cx2, previewBottom + 19, 0xFFE9C46A);
                    drawOutline(context, hx1, hy1, hx2, hy2, 0xFFE9C46A);
                    if (getSampleStatusTarget() == PanelColorTarget.SKIN_TEXT) {
                        drawOutline(context, panelX + 15, statusY - 3, panelX + 18 + statusW + 4, statusY + 11, 0xFFE9C46A);
                    }
                }
                case SKIN_PANEL -> drawOutline(context, panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, 0xFFE9C46A);
            }
        }
    }

    private LayerPreviewLayout getLayerPreviewLayout(int previewLeft, int previewTop, int previewWidth) {
        int chevronLeft = previewLeft + LAYER_MENU_PAD;
        int chevronTop = previewTop + LAYER_MENU_PAD;
        int menuLeft = previewLeft + LAYER_MENU_PAD;
        int menuTop = chevronTop + LAYER_CHEVRON_SIZE + 2;
        int menuWidth = Math.min(110, previewWidth - LAYER_MENU_PAD * 2);
        int menuHeight = SkinLayerMask.EDITABLE_PARTS.length * LAYER_ROW_HEIGHT + 4;
        return new LayerPreviewLayout(
            chevronLeft,
            chevronTop,
            chevronLeft + LAYER_CHEVRON_SIZE,
            chevronTop + LAYER_CHEVRON_SIZE,
            menuLeft,
            menuTop,
            menuLeft + menuWidth,
            menuTop + menuHeight
        );
    }

    private PanelColorTarget getSkinLayerPreviewTargetAt(int mouseX, int mouseY, int previewLeft, int previewTop, int previewWidth) {
        LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
        if (mouseX >= layer.chevronLeft() && mouseX < layer.chevronRight()
            && mouseY >= layer.chevronTop() && mouseY < layer.chevronBottom()) {
            boolean onRim = mouseX < layer.chevronLeft() + 2
                || mouseX >= layer.chevronRight() - 2
                || mouseY < layer.chevronTop() + 2
                || mouseY >= layer.chevronBottom() - 2;
            return onRim ? PanelColorTarget.SKIN_LAYER_CHEVRON_BG : PanelColorTarget.SKIN_LAYER_CHEVRON;
        }

        if (mouseX < layer.menuLeft() || mouseX >= layer.menuRight()
            || mouseY < layer.menuTop() || mouseY >= layer.menuBottom()) {
            return null;
        }

        int index = (mouseY - layer.menuTop() - 2) / LAYER_ROW_HEIGHT;
        if (index < 0 || index >= SkinLayerMask.EDITABLE_PARTS.length) {
            return PanelColorTarget.SKIN_LAYER_MENU_BG;
        }

        int rowTop = layer.menuTop() + 2 + index * LAYER_ROW_HEIGHT;
        int rowBottom = rowTop + LAYER_ROW_HEIGHT;
        boolean onMenuChrome = mouseX <= layer.menuLeft() + 1
            || mouseX >= layer.menuRight() - 2
            || mouseY < rowTop
            || mouseY >= rowBottom;
        if (onMenuChrome) {
            return PanelColorTarget.SKIN_LAYER_MENU_BG;
        }
        return index % 2 == 0 ? PanelColorTarget.SKIN_LAYER_ACTIVE : PanelColorTarget.SKIN_LAYER_INACTIVE;
    }

    private void drawSkinLayerPreview(DrawContext context, int previewLeft, int previewTop, int previewWidth) {
        LayerPreviewLayout layer = getLayerPreviewLayout(previewLeft, previewTop, previewWidth);
        int chevronBg = getDraftColor(PanelColorTarget.SKIN_LAYER_CHEVRON_BG);
        int chevronColor = getDraftColor(PanelColorTarget.SKIN_LAYER_CHEVRON);
        int menuBg = getDraftColor(PanelColorTarget.SKIN_LAYER_MENU_BG);
        int activeColor = getDraftColor(PanelColorTarget.SKIN_LAYER_ACTIVE);
        int inactiveColor = getDraftColor(PanelColorTarget.SKIN_LAYER_INACTIVE);

        fillArgb(context, layer.chevronLeft(), layer.chevronTop(), layer.chevronRight(), layer.chevronBottom(), chevronBg);
        drawChevronIcon(context, layer.chevronLeft(), layer.chevronTop(), true, chevronColor);

        fillArgb(context, layer.menuLeft(), layer.menuTop(), layer.menuRight(), layer.menuBottom(), menuBg);

        int checkboxWidth = font().getWidth("[x]");
        int rowY = layer.menuTop() + 2;
        for (int i = 0; i < SkinLayerMask.EDITABLE_PARTS.length; i++) {
            boolean enabled = i % 2 == 0;
            int markColor = enabled ? activeColor : inactiveColor;
            int markX = layer.menuLeft() + 3;
            int markY = rowY + 2;
            drawLayerCheckbox(context, markX, markY, enabled, markColor);
            context.drawText(
                font(),
                Text.literal(SkinLayerMask.label(SkinLayerMask.EDITABLE_PARTS[i])),
                markX + checkboxWidth + 4,
                markY,
                markColor,
                false
            );
            rowY += LAYER_ROW_HEIGHT;
        }
    }

    private void drawChevronIcon(DrawContext context, int boxLeft, int boxTop, boolean openUp, int color) {
        int startX = boxLeft + (LAYER_CHEVRON_SIZE - 5) / 2;
        int startY = boxTop + (LAYER_CHEVRON_SIZE - 3) / 2;
        if (openUp) {
            fillArgb(context, startX + 2, startY, startX + 3, startY + 1, color);
            fillArgb(context, startX + 1, startY + 1, startX + 2, startY + 2, color);
            fillArgb(context, startX + 3, startY + 1, startX + 4, startY + 2, color);
            fillArgb(context, startX, startY + 2, startX + 1, startY + 3, color);
            fillArgb(context, startX + 4, startY + 2, startX + 5, startY + 3, color);
        } else {
            fillArgb(context, startX, startY, startX + 1, startY + 1, color);
            fillArgb(context, startX + 4, startY, startX + 5, startY + 1, color);
            fillArgb(context, startX + 1, startY + 1, startX + 2, startY + 2, color);
            fillArgb(context, startX + 3, startY + 1, startX + 4, startY + 2, color);
            fillArgb(context, startX + 2, startY + 2, startX + 3, startY + 3, color);
        }
    }

    private void drawLayerCheckbox(DrawContext context, int x, int y, boolean enabled, int color) {
        int openWidth = font().getWidth("[");
        int innerWidth = font().getWidth(enabled ? "x" : " ");
        context.drawText(font(), Text.literal("["), x, y, color, false);
        if (enabled) {
            context.drawText(font(), Text.literal("x"), x + openWidth, y - 1, color, false);
        } else {
            context.drawText(font(), Text.literal(" "), x + openWidth, y, color, false);
        }
        context.drawText(font(), Text.literal("]"), x + openWidth + innerWidth, y, color, false);
    }

    private void renderActionButton(
        DrawContext context,
        Bounds bounds,
        String label,
        boolean active,
        int mouseX,
        int mouseY,
        boolean focused
    ) {
        boolean hovered = active && bounds.contains(mouseX, mouseY);
        int background = active ? (hovered ? 0xFF7A7A7A : 0xFF5A5A5A) : 0xFF303030;
        int foreground = active ? 0xFFFFFFFF : 0xFF777777;
        fillArgb(context, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), background);
        int outlineColor = (active && (focused || hovered)) ? 0xFFFFFFFF : 0xFF202020;
        drawOutline(context, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), outlineColor);
        int textX = bounds.left() + (bounds.width() - font().getWidth(label)) / 2;
        int textY = bounds.top() + (bounds.height() - font().fontHeight) / 2 + 1;
        context.drawText(font(), Text.literal(label), textX, textY, foreground, false);
    }

    private void renderColorSwatch(DrawContext context, int x, int y, int width, int height, int color) {
        blitCheckerboardTiled(context, x, y, width, height);
        fillArgb(context, x, y, x + width, y + height, color);
        drawOutline(context, x, y, x + width, y + height, 0xFF080A0C);
    }

    private void setOpacityFromSlider(int mouseX, int mouseY, EditorLayout layout) {
        if (currentTarget == null) {
            return;
        }
        Bounds slider = layout.opacitySlider();
        int alpha;
        if (layout.vertical()) {
            int offset = clamp(mouseY - slider.top(), 0, slider.height() - 1);
            alpha = Math.round((1.0F - (offset / (float) Math.max(1, slider.height() - 1))) * 255.0F);
        } else {
            int offset = clamp(mouseX - slider.left(), 0, slider.width() - 1);
            alpha = Math.round((offset / (float) Math.max(1, slider.width() - 1)) * 255.0F);
        }
        int color = getDraftColor(currentTarget);
        putDraftColor(currentTarget, (alpha << 24) | (color & 0x00FFFFFF));
        updateHexFieldValue();
    }

    private void adjustAlpha(int delta) {
        if (currentTarget == null) {
            return;
        }
        int color = getDraftColor(currentTarget);
        int currentAlpha = alpha(color);
        int newAlpha = clamp(currentAlpha + delta, 0, 255);
        putDraftColor(currentTarget, (newAlpha << 24) | (color & 0x00FFFFFF));
        updateHexFieldValue();
    }

    private static Integer parseHexColor(String text, int currentAlpha) {
        if (text == null) return null;
        String clean = text.trim().replaceAll("[^0-9a-fA-F]", "");
        if (clean.length() == 6) {
            try {
                int rgb = Integer.parseInt(clean, 16);
                return (currentAlpha << 24) | (rgb & 0x00FFFFFF);
            } catch (NumberFormatException ignored) {}
        } else if (clean.length() == 8) {
            try {
                long argb = Long.parseLong(clean, 16);
                return (int) argb;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer getPaletteColorAt(int mouseX, int mouseY, EditorLayout layout) {
        int cellW = layout.paletteCellWidth();
        int cellH = layout.paletteCellHeight();
        int pixelWidth = layout.paletteGridCols() * cellW;
        int pixelHeight = layout.paletteGridRows() * cellH;

        if (
            mouseX < layout.paletteLeft()
                || mouseX >= layout.paletteLeft() + pixelWidth
                || mouseY < layout.paletteTop()
                || mouseY >= layout.paletteTop() + pixelHeight
        ) {
            return null;
        }

        int gridCol = (mouseX - layout.paletteLeft()) / cellW;
        int gridRow = (mouseY - layout.paletteTop()) / cellH;

        int hue = layout.vertical() ? gridRow : gridCol;
        int bright = layout.vertical() ? gridCol : gridRow;
        return 0xFF000000 | getPaletteRgb(hue, bright);
    }

    private int getRawDraftColor(PanelColorTarget target) {
        return draftColors.getOrDefault(target, target.getDefaultColor());
    }

    private boolean isTargetAtDefault(PanelColorTarget target) {
        return getRawDraftColor(target) == target.getDefaultColor();
    }

    private boolean areAllTargetsAtDefault() {
        for (PanelColorTarget target : PanelColorTarget.values()) {
            if (!isTargetAtDefault(target)) {
                return false;
            }
        }
        return true;
    }

    private boolean canReset() {
        if (currentTarget != null) {
            return !isTargetAtDefault(currentTarget);
        }
        return !areAllTargetsAtDefault();
    }

    private String getResetButtonLabel() {
        return currentTarget != null ? "Reset" : "Reset all";
    }

    private void performReset() {
        if (!canReset()) {
            return;
        }
        if (currentTarget != null) {
            putDraftColor(currentTarget, currentTarget.getDefaultColor());
        } else {
            for (PanelColorTarget target : PanelColorTarget.values()) {
                putDraftColor(target, target.getDefaultColor());
            }
        }
        updateHexFieldValue();
        if (focusedControl == 2 && !canReset()) {
            focusedControl = -1;
            updateFocusState();
        }
    }

    private boolean hasUnsavedChanges() {
        if (unsavedChangesCache == null) {
            unsavedChangesCache = !draftColors.equals(config.getStoredPanelColors());
        }
        return unsavedChangesCache;
    }

    private int getDraftColor(PanelColorTarget target) {
        int color = draftColors.getOrDefault(target, target.getDefaultColor());
        if (target == PanelColorTarget.LEFT_PANEL_STATUS && (color == 0 || color == 0xFF55FF55)) {
            var data = owner != null ? owner.getData() : null;
            if (data != null) {
                return data.enabled() ? (data.allowMobSpawning() ? 0xFF55FF55 : 0xFF5555FF) : 0xFFFF5555;
            }
        }
        if (target == PanelColorTarget.LEFT_PANEL_DIM && (color == 0 || color == 0xFF55FF55)) {
            var data = owner != null ? owner.getData() : null;
            if (data != null && data.dimensionKey() != null) {
                String dimName = data.dimensionKey().toLowerCase();
                if (dimName.contains("nether")) {
                    return 0xFFFF5555;
                } else if (dimName.contains("end")) {
                    return 0xFFFF55FF;
                } else if (dimName.contains("overworld")) {
                    return 0xFF55FF55;
                }
            }
        }
        return color;
    }

    void close() {
        cleanupTextures();
    }

    private void ensureTextures() {
        if (texturesInitialized) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }

        NativeImage horizontal = new NativeImage(NativeImage.Format.RGBA, PALETTE_COLUMNS, PALETTE_ROWS, false);
        for (int row = 0; row < PALETTE_ROWS; row++) {
            for (int col = 0; col < PALETTE_COLUMNS; col++) {
                horizontal.setColorArgb(col, row, 0xFF000000 | getPaletteRgb(col, row));
            }
        }
        horizontalPaletteTexture = new NativeImageBackedTexture(() -> "chunkloader_editor_palette_h", horizontal);
        horizontalPaletteTextureId = Identifier.of("chunkloader", "panel_color_editor_palette_h");
        client.getTextureManager().registerTexture(horizontalPaletteTextureId, horizontalPaletteTexture);

        NativeImage vertical = new NativeImage(NativeImage.Format.RGBA, PALETTE_ROWS, PALETTE_COLUMNS, false);
        for (int row = 0; row < PALETTE_COLUMNS; row++) {
            for (int col = 0; col < PALETTE_ROWS; col++) {
                vertical.setColorArgb(col, row, 0xFF000000 | getPaletteRgb(row, col));
            }
        }
        verticalPaletteTexture = new NativeImageBackedTexture(() -> "chunkloader_editor_palette_v", vertical);
        verticalPaletteTextureId = Identifier.of("chunkloader", "panel_color_editor_palette_v");
        client.getTextureManager().registerTexture(verticalPaletteTextureId, verticalPaletteTexture);

        NativeImage checker = new NativeImage(NativeImage.Format.RGBA, 12, 12, false);
        int checkerSize = 3;
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                boolean light = ((x / checkerSize) + (y / checkerSize)) % 2 == 0;
                checker.setColorArgb(x, y, light ? 0xFFFFFFFF : 0xFFB0B0B0);
            }
        }
        checkerboardTexture = new NativeImageBackedTexture(() -> "chunkloader_editor_checker", checker);
        checkerboardTextureId = Identifier.of("chunkloader", "panel_color_editor_checker");
        client.getTextureManager().registerTexture(checkerboardTextureId, checkerboardTexture);

        texturesInitialized = true;
    }

    private void ensureOpacityGradientTexture(int rgb, int width, int height, boolean vertical) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (
            opacityGradientTextureId != null
                && cachedOpacityRgb == rgb
                && cachedOpacityWidth == width
                && cachedOpacityHeight == height
                && cachedOpacityVertical == vertical
        ) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }

        if (
            opacityGradientTexture != null
                && opacityGradientTextureId != null
                && cachedOpacityWidth == width
                && cachedOpacityHeight == height
                && cachedOpacityVertical == vertical
        ) {
            NativeImage image = opacityGradientTexture.getImage();
            if (image != null) {
                fillOpacityGradient(image, rgb, width, height, vertical);
                opacityGradientTexture.upload();
                cachedOpacityRgb = rgb;
                return;
            }
        }

        releaseOpacityGradientTexture(client);

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        fillOpacityGradient(image, rgb, width, height, vertical);

        opacityGradientTexture = new NativeImageBackedTexture(() -> "chunkloader_editor_opacity", image);
        opacityGradientTextureId = Identifier.of("chunkloader", "panel_color_editor_opacity");
        client.getTextureManager().registerTexture(opacityGradientTextureId, opacityGradientTexture);
        cachedOpacityRgb = rgb;
        cachedOpacityWidth = width;
        cachedOpacityHeight = height;
        cachedOpacityVertical = vertical;
    }

    private static void fillOpacityGradient(NativeImage image, int rgb, int width, int height, boolean vertical) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alphaValue;
                if (vertical) {
                    alphaValue = Math.round((1.0F - (y / (float) Math.max(1, height - 1))) * 255.0F);
                } else {
                    alphaValue = Math.round((x / (float) Math.max(1, width - 1)) * 255.0F);
                }
                image.setColorArgb(x, y, (alphaValue << 24) | (rgb & 0x00FFFFFF));
            }
        }
    }

    private void releaseOpacityGradientTexture(MinecraftClient client) {
        if (opacityGradientTextureId != null && client != null && client.getTextureManager() != null) {
            client.getTextureManager().destroyTexture(opacityGradientTextureId);
            opacityGradientTextureId = null;
        }
        if (opacityGradientTexture != null) {
            opacityGradientTexture.close();
            opacityGradientTexture = null;
        }
        cachedOpacityRgb = Integer.MIN_VALUE;
        cachedOpacityWidth = -1;
        cachedOpacityHeight = -1;
    }

    private void cleanupTextures() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            if (horizontalPaletteTextureId != null) {
                client.getTextureManager().destroyTexture(horizontalPaletteTextureId);
                horizontalPaletteTextureId = null;
            }
            if (verticalPaletteTextureId != null) {
                client.getTextureManager().destroyTexture(verticalPaletteTextureId);
                verticalPaletteTextureId = null;
            }
            if (checkerboardTextureId != null) {
                client.getTextureManager().destroyTexture(checkerboardTextureId);
                checkerboardTextureId = null;
            }
        }
        if (horizontalPaletteTexture != null) {
            horizontalPaletteTexture.close();
            horizontalPaletteTexture = null;
        }
        if (verticalPaletteTexture != null) {
            verticalPaletteTexture.close();
            verticalPaletteTexture = null;
        }
        if (checkerboardTexture != null) {
            checkerboardTexture.close();
            checkerboardTexture = null;
        }
        releaseOpacityGradientTexture(client);
        texturesInitialized = false;
    }

    private void blitCheckerboardTiled(DrawContext context, int x, int y, int width, int height) {
        if (checkerboardTextureId == null || width <= 0 || height <= 0) {
            return;
        }
        final int tile = 12;
        for (int offsetY = 0; offsetY < height; offsetY += tile) {
            int tileH = Math.min(tile, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += tile) {
                int tileW = Math.min(tile, width - offsetX);
                blitTextureRegion(
                    context,
                    checkerboardTextureId,
                    x + offsetX,
                    y + offsetY,
                    tileW,
                    tileH,
                    tileW,
                    tileH,
                    tile,
                    tile
                );
            }
        }
    }

    private static void blitTexture(
        DrawContext context,
        Identifier textureId,
        int x,
        int y,
        int width,
        int height,
        int textureWidth,
        int textureHeight
    ) {
        blitTextureRegion(context, textureId, x, y, width, height, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    private static void blitTextureRegion(
        DrawContext context,
        Identifier textureId,
        int x,
        int y,
        int width,
        int height,
        int regionWidth,
        int regionHeight,
        int textureWidth,
        int textureHeight
    ) {
        if (textureId == null || width <= 0 || height <= 0) {
            return;
        }
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x,
            y,
            0.0f,
            0.0f,
            width,
            height,
            regionWidth,
            regionHeight,
            textureWidth,
            textureHeight
        );
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private EditorLayout getLayout() {
        if (currentPage == 2 || owner.useVerticalPaletteLayout()) {
            return getVerticalLayout();
        }
        return getHorizontalLayout();
    }

    private EditorLayout getHorizontalLayout() {
        int cellW = 12;
        int cellH = 12;
        int palettePixelWidth = PALETTE_COLUMNS * cellW;
        int palettePixelHeight = PALETTE_ROWS * cellH;

        int opacityHeight = 12;
        int buttonWidth = 84;
        int buttonHeight = 18;
        int buttonGap = 4;
        int hexHeight = 16;
        int gapBetweenPaletteAndButtons = 14;

        int totalCardWidth = palettePixelWidth + gapBetweenPaletteAndButtons + buttonWidth;
        int cardLeft = owner.width / 2 - totalCardWidth / 2;

        int cardTop = 10;

        int paletteLeft = cardLeft;
        int paletteTop = cardTop + font().fontHeight + 4;

        int buttonLeft = paletteLeft + palettePixelWidth + gapBetweenPaletteAndButtons;
        int hexLeft = buttonLeft;
        int hexTop = paletteTop;

        int b1Top = hexTop + hexHeight + 5;
        int b1Bottom = b1Top + buttonHeight;

        int b2Top = b1Bottom + buttonGap;
        int b2Bottom = b2Top + buttonHeight;

        int b3Top = b2Bottom + buttonGap;
        int b3Bottom = b3Top + buttonHeight;

        int sliderTop = paletteTop + palettePixelHeight + 6;
        int sliderBottom = sliderTop + opacityHeight;

        int cardRight = cardLeft + totalCardWidth;
        int cardBottom = Math.max(sliderBottom, b3Bottom);

        return new EditorLayout(
            cardLeft,
            cardTop,
            cardRight,
            cardBottom,
            paletteLeft,
            paletteTop,
            PALETTE_COLUMNS,
            PALETTE_ROWS,
            cellW,
            cellH,
            false,
            new Bounds(hexLeft, hexTop, hexLeft + buttonWidth, hexTop + hexHeight),
            new Bounds(paletteLeft, sliderTop, paletteLeft + palettePixelWidth, sliderBottom),
            new Bounds(buttonLeft, b1Top, buttonLeft + buttonWidth, b1Bottom),
            new Bounds(buttonLeft, b2Top, buttonLeft + buttonWidth, b2Bottom),
            new Bounds(buttonLeft, b3Top, buttonLeft + buttonWidth, b3Bottom)
        );
    }

    private EditorLayout getVerticalLayout() {
        int paletteGridCols = PALETTE_ROWS;
        int paletteGridRows = PALETTE_COLUMNS;
        int cellSize = Math.max(8, Math.min(13, (owner.height - 150) / 24));
        int cellW = cellSize;
        int cellH = cellSize;

        int palettePixelWidth = paletteGridCols * cellW;
        int palettePixelHeight = paletteGridRows * cellH;

        int opacityWidth = 12;
        int opacityGap = 6;
        int totalPaletteWidth = palettePixelWidth + opacityGap + opacityWidth;

        int hexHeight = 16;
        int buttonHeight = 18;
        int buttonGap = 4;
        int wordCount = currentTarget != null ? currentTarget.getDisplayName().split(" ").length : 0;
        int totalTitleLines = currentTarget != null ? 1 + wordCount : 1;
        int titleHeight = totalTitleLines * (font().fontHeight + 1) + 2;

        int totalHeight = titleHeight + 4
            + hexHeight + 6
            + palettePixelHeight + 8
            + buttonHeight * 3 + buttonGap * 2;

        int cardLeft = 44;
        int cardTop = Math.max(10, (owner.height - totalHeight) / 2);
        int paletteLeft = cardLeft;

        int controlsLeft = paletteLeft - 1;
        int controlsWidth = totalPaletteWidth + 1;

        int hexLeft = controlsLeft;
        int hexTop = cardTop + titleHeight + 3;

        int paletteTop = hexTop + hexHeight + 5;

        int sliderLeft = paletteLeft + palettePixelWidth + opacityGap;
        int sliderTop = paletteTop;
        int sliderRight = sliderLeft + opacityWidth;
        int sliderBottom = paletteTop + palettePixelHeight;

        int b1Top = paletteTop + palettePixelHeight + 8;
        int b1Bottom = b1Top + buttonHeight;

        int b2Top = b1Bottom + buttonGap;
        int b2Bottom = b2Top + buttonHeight;

        int b3Top = b2Bottom + buttonGap;
        int b3Bottom = b3Top + buttonHeight;

        int cardRight = paletteLeft + totalPaletteWidth;
        int cardBottom = b3Bottom;

        return new EditorLayout(
            cardLeft,
            cardTop,
            cardRight,
            cardBottom,
            paletteLeft,
            paletteTop,
            paletteGridCols,
            paletteGridRows,
            cellW,
            cellH,
            true,
            new Bounds(hexLeft, hexTop, hexLeft + controlsWidth, hexTop + hexHeight),
            new Bounds(sliderLeft, sliderTop, sliderRight, sliderBottom),
            new Bounds(controlsLeft, b1Top, controlsLeft + controlsWidth, b1Bottom),
            new Bounds(controlsLeft, b2Top, controlsLeft + controlsWidth, b2Bottom),
            new Bounds(controlsLeft, b3Top, controlsLeft + controlsWidth, b3Bottom)
        );
    }

    private static int getPaletteRgb(int column, int row) {
        if (row < PALETTE_ROWS - 1) {
            float hue = column / (float) PALETTE_COLUMNS;
            float value = switch (row) {
                case 0 -> 1.0F;
                case 1 -> 0.85F;
                case 2 -> 0.7F;
                case 3 -> 0.55F;
                case 4 -> 0.4F;
                default -> 0.25F;
            };
            return hsvToRgb(hue, 1.0F, value);
        }

        int gray = 255 - Math.round(column * (255.0F / (PALETTE_COLUMNS - 1)));
        return (gray << 16) | (gray << 8) | gray;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float red;
        float green;
        float blue;
        int sector = (int) Math.floor(hue * 6.0F);
        float fraction = hue * 6.0F - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - fraction * saturation);
        float t = value * (1.0F - (1.0F - fraction) * saturation);

        switch (sector % 6) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }

        return (Math.round(red * 255.0F) << 16)
            | (Math.round(green * 255.0F) << 8)
            | Math.round(blue * 255.0F);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void fillArgb(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, bottom, color);
    }

    private static void drawOutline(DrawContext context, int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) {
            return;
        }

        fillArgb(context, left, top, right, top + 1, color);
        fillArgb(context, left, bottom - 1, right, bottom, color);
        fillArgb(context, left, top, left + 1, bottom, color);
        fillArgb(context, right - 1, top, right, bottom, color);
    }

    private Bounds getHelpMarkBounds() {
        int markX = 8;
        int markY = 8;
        int markW = Math.max(10, font().getWidth("?"));
        int markH = font().fontHeight;
        return new Bounds(markX, markY, markX + markW, markY + markH);
    }

    private record EditorLayout(
        int cardLeft,
        int cardTop,
        int cardRight,
        int cardBottom,
        int paletteLeft,
        int paletteTop,
        int paletteGridCols,
        int paletteGridRows,
        int paletteCellWidth,
        int paletteCellHeight,
        boolean vertical,
        Bounds hexField,
        Bounds opacitySlider,
        Bounds saveButton,
        Bounds resetButton,
        Bounds cancelButton
    ) {
    }

    private record Bounds(int left, int top, int right, int bottom) {
        private int width() {
            return right - left;
        }

        private int height() {
            return bottom - top;
        }

        private boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private record LayerPreviewLayout(
        int chevronLeft,
        int chevronTop,
        int chevronRight,
        int chevronBottom,
        int menuLeft,
        int menuTop,
        int menuRight,
        int menuBottom
    ) {
    }

    private record SampleStatus(String text, PanelColorTarget target) {
    }
}
