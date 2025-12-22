package de.chunkloader.client.screen;

import de.chunkloader.client.config.ClientConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class PanelColorScreen extends Screen {
    
    public enum ColorType {
        PANEL("Panel Background", 0xFF2B2F36),
        BORDER("Panel Border", 0xFF4A4A4A),
        DIVIDER("Divider Lines", 0x33FFFFFF),
        FRAME("Chunkmap Frame", 0xFF111417),
        SCROLLBAR_TRACK("Scrollbar Track", 0x33000000),
        SCROLLBAR_THUMB("Scrollbar Thumb", 0xFFAAAAAA),
        LEFT_PANEL_TEXT("Left Panel Labels", 0xCC808080),
        LEFT_PANEL_VALUE("Left Panel Values", 0xFFFFFFFF),
        LEFT_PANEL_NAME("Left Panel Name", 0xFFFFFFFF),
        SEARCHBAR_BACKGROUND("Search Bar Background", 0xFF2B2F36),
        SEARCHBAR_BORDER("Search Bar Border", 0xFF4A4A4A),
        SEARCHBAR_TEXT("Search Bar Text", 0xFFFFFFFF),
        SEARCHBAR_PLACEHOLDER("Search Bar Placeholder", 0xCC808080);
        
        private final String displayName;
        private final int defaultValue;
        
        ColorType(String displayName, int defaultValue) {
            this.displayName = displayName;
            this.defaultValue = defaultValue;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getDefaultValue() {
            return defaultValue;
        }
    }
    
    private final Screen parent;
    private final ClientConfig config;
    
    private ColorType currentColorType = ColorType.PANEL;
    private EditBox colorField;
    private EditBox opacityField;
    private EditBox hiddenField;
    private Button saveButton;
    private Button colorTypeButton;
    private String errorMessage = null;
    private int previewColor = 0xFF2B2F36;
    private int unfocusTicks = 0;

    private static final int PALETTE_COLS = 24;
    private static final int PALETTE_ROWS = 7;
    private static final int PALETTE_CELL_SIZE = 12;

    private static final int OPACITY_SLIDER_HEIGHT = 10;
    private static final int OPACITY_SLIDER_GAP = 6;
    private static final int OPACITY_SLIDER_KNOB_WIDTH = 2;

    private static final int SAFE_PADDING_PX = 10;

    private boolean opacitySliderDragging = false;
    
    private DynamicTexture paletteTexture;
    private ResourceLocation paletteTextureId;
    private DynamicTexture checkerboardTexture;
    private ResourceLocation checkerboardTextureId;
    private boolean texturesInitialized = false;
    
    public PanelColorScreen(Screen parent, ClientConfig config) {
        super(Component.literal("Panel Colors"));
        this.parent = parent;
        this.config = config;
        this.previewColor = getCurrentColor();
    }

    private int getLayoutCenterY() {
        int topOffset = 60;
        int paletteHeight = PALETTE_ROWS * PALETTE_CELL_SIZE;
        int bottomOffset = 75 + paletteHeight + OPACITY_SLIDER_GAP + OPACITY_SLIDER_HEIGHT;

        int minCenterY = SAFE_PADDING_PX + topOffset;
        int maxCenterY = Math.max(minCenterY, this.height - SAFE_PADDING_PX - bottomOffset);

        int desiredCenterY = this.height / 2;
        return Math.max(minCenterY, Math.min(desiredCenterY, maxCenterY));
    }
    
    @Override
    protected void init() {
        super.init();
        initializeTextures();
        
        int fieldWidth = 200;
        int fieldHeight = 20;
        int centerX = this.width / 2;
        int centerY = getLayoutCenterY();
        
        int arrowButtonWidth = 30;
        int colorTypeButtonWidth = fieldWidth - arrowButtonWidth * 2 - 4;
        
        Button leftArrowButton = Button.builder(
            Component.literal("◀"),
            btn -> {
                cycleColorTypeBackward();
                unfocusTicks = 2;
            })
            .bounds(centerX - fieldWidth / 2, centerY - 60, arrowButtonWidth, 20)
            .build();
        this.addRenderableWidget(leftArrowButton);
        
        colorTypeButton = Button.builder(
            Component.literal(currentColorType.getDisplayName()),
            btn -> {
                cycleColorType();
                unfocusTicks = 2;
            })
            .bounds(centerX - fieldWidth / 2 + arrowButtonWidth + 2, centerY - 60, colorTypeButtonWidth, 20)
            .build();
        this.addRenderableWidget(colorTypeButton);
        
        Button rightArrowButton = Button.builder(
            Component.literal("▶"),
            btn -> {
                cycleColorType();
                unfocusTicks = 2;
            })
            .bounds(centerX + fieldWidth / 2 - arrowButtonWidth, centerY - 60, arrowButtonWidth, 20)
            .build();
        this.addRenderableWidget(rightArrowButton);
        
        colorField = new EditBox(this.font, centerX - fieldWidth / 2, centerY - 30, fieldWidth - 60, fieldHeight, Component.literal("Color (RRGGBB)"));
        colorField.setMaxLength(7);
        colorField.setValue("");
        colorField.setResponder(text -> {
            String currentText = colorField.getValue();
            if (currentText.startsWith("#")) {
                if (currentText.length() > 7) {
                    colorField.setValue(currentText.substring(0, 7));
                }
            } else {
                if (currentText.length() > 6) {
                    colorField.setValue(currentText.substring(0, 6));
                }
            }
            updatePreviewColor();
            updateSaveButtonState();
        });
        this.addWidget(colorField);
        
        opacityField = new EditBox(this.font, centerX + fieldWidth / 2 - 55, centerY - 30, 55, fieldHeight, Component.literal("Opacity"));
        opacityField.setMaxLength(3);
        opacityField.setValue("");
        opacityField.setResponder(text -> {
            String opacityStr = text.trim();
            if (!opacityStr.isEmpty()) {
                try {
                    int opacity = Integer.parseInt(opacityStr);
                    if (opacity < 0 || opacity > 255) {
                        errorMessage = "Opacity must be between 0 and 255";
                    } else {
                        if (errorMessage != null && errorMessage.contains("Opacity")) {
                            errorMessage = null;
                        }
                    }
                } catch (NumberFormatException e) {
                    if (!opacityStr.matches("^-?\\d*$")) {
                        errorMessage = "Opacity must be a number between 0 and 255";
                    } else {
                        if (errorMessage != null && errorMessage.contains("Opacity")) {
                            errorMessage = null;
                        }
                    }
                }
            } else {
                if (errorMessage != null && errorMessage.contains("Opacity")) {
                    errorMessage = null;
                }
            }
            updatePreviewColor();
            updateSaveButtonState();
        });
        this.addWidget(opacityField);
        
        int buttonWidth = 100;
        int buttonSpacing = 110;
        
        saveButton = Button.builder(
                Component.literal("Save"),
                btn -> {
                    save();
                    unfocusTicks = 2;
                })
            .bounds(centerX - buttonSpacing, centerY + 20, buttonWidth, 20)
            .build();
        updateSaveButtonState();
        this.addRenderableWidget(saveButton);
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> {
                    unfocusTicks = 2;
                    this.minecraft.setScreen(parent);
                })
            .bounds(centerX + buttonSpacing - buttonWidth, centerY + 20, buttonWidth, 20)
            .build()
        );
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset"),
                btn -> {
                    resetToDefault();
                    unfocusTicks = 2;
                })
            .bounds(centerX - buttonWidth / 2, centerY + 50, buttonWidth, 20)
            .build()
        );
        
        hiddenField = new EditBox(this.font, -1000, -1000, 1, 1, Component.literal(""));
        hiddenField.setVisible(false);
        this.addWidget(hiddenField);
    }
    
    private void cycleColorType() {
        ColorType[] types = ColorType.values();
        int currentIndex = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == currentColorType) {
                currentIndex = i;
                break;
            }
        }
        currentColorType = types[(currentIndex + 1) % types.length];
        updateColorTypeDisplay();
    }
    
    private void cycleColorTypeBackward() {
        ColorType[] types = ColorType.values();
        int currentIndex = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == currentColorType) {
                currentIndex = i;
                break;
            }
        }
        currentColorType = types[(currentIndex - 1 + types.length) % types.length];
        updateColorTypeDisplay();
    }
    
    private void updateColorTypeDisplay() {
        colorTypeButton.setMessage(Component.literal(currentColorType.getDisplayName()));
        
        colorField.setValue("");
        opacityField.setValue("");
        errorMessage = null;
        updatePreviewColor();
        updateSaveButtonState();
    }
    
    private int getCurrentColor() {
        switch (currentColorType) {
            case PANEL: return config.getPanelColor();
            case BORDER: return config.getBorderColor();
            case DIVIDER: return config.getDividerColor();
            case FRAME: return config.getFrameColor();
            case SCROLLBAR_TRACK: return config.getScrollbarTrackColor();
            case SCROLLBAR_THUMB: return config.getScrollbarThumbColor();
            case LEFT_PANEL_TEXT: return config.getLeftPanelTextColor();
            case LEFT_PANEL_VALUE: return config.getLeftPanelValueColor();
            case LEFT_PANEL_NAME: return config.getLeftPanelNameColor();
            case SEARCHBAR_BACKGROUND: return config.getActionSearchBackgroundColor();
            case SEARCHBAR_BORDER: return config.getActionSearchBorderColor();
            case SEARCHBAR_TEXT: return config.getActionSearchTextColor();
            case SEARCHBAR_PLACEHOLDER: return config.getActionSearchPlaceholderColor();
            default: return 0xFF2B2F36;
        }
    }
    
    private void setCurrentColor(int color) {
        switch (currentColorType) {
            case PANEL: config.setPanelColor(color); break;
            case BORDER: config.setBorderColor(color); break;
            case DIVIDER: config.setDividerColor(color); break;
            case FRAME: config.setFrameColor(color); break;
            case SCROLLBAR_TRACK: config.setScrollbarTrackColor(color); break;
            case SCROLLBAR_THUMB: config.setScrollbarThumbColor(color); break;
            case LEFT_PANEL_TEXT: config.setLeftPanelTextColor(color); break;
            case LEFT_PANEL_VALUE: config.setLeftPanelValueColor(color); break;
            case LEFT_PANEL_NAME: config.setLeftPanelNameColor(color); break;
            case SEARCHBAR_BACKGROUND: config.setActionSearchBackgroundColor(color); break;
            case SEARCHBAR_BORDER: config.setActionSearchBorderColor(color); break;
            case SEARCHBAR_TEXT: config.setActionSearchTextColor(color); break;
            case SEARCHBAR_PLACEHOLDER: config.setActionSearchPlaceholderColor(color); break;
        }
    }
    
    private void updatePreviewColor() {
        if (colorField == null || opacityField == null) {
            previewColor = getCurrentColor();
            return;
        }
        
        String colorStr = colorField.getValue().trim();
        String opacityStr = opacityField.getValue().trim();
        
        boolean hasOpacityError = errorMessage != null && errorMessage.contains("Opacity");
        
        int currentColor = getCurrentColor();
        int currentRgb = currentColor & 0x00FFFFFF;
        int currentOpacity = (currentColor >> 24) & 0xFF;
        
        int rgb;
        if (colorStr.isEmpty()) {
            rgb = currentRgb;
        } else {
            try {
                if (colorStr.startsWith("#")) {
                    colorStr = colorStr.substring(1);
                }
                if (colorStr.isEmpty()) {
                    rgb = currentRgb;
                } else {
                    rgb = Integer.parseInt(colorStr, 16);
                    if (rgb < 0 || rgb > 0xFFFFFF) {
                        throw new NumberFormatException("RGB out of range");
                    }
                }
            } catch (NumberFormatException e) {
                if (!hasOpacityError) {
                    errorMessage = "Invalid format. RGB: 000000-FFFFFF";
                }
                previewColor = getCurrentColor();
                return;
            }
        }
        
        int opacity;
        if (opacityStr.isEmpty()) {
            opacity = currentOpacity;
        } else {
            try {
                opacity = Integer.parseInt(opacityStr);
                if (opacity < 0 || opacity > 255) {
                    if (!hasOpacityError) {
                        throw new NumberFormatException("Opacity out of range");
                    } else {
                        opacity = currentOpacity;
                    }
                }
            } catch (NumberFormatException e) {
                if (!hasOpacityError) {
                    errorMessage = "Opacity must be between 0 and 255";
                }
                opacity = currentOpacity;
            }
        }
        
        previewColor = (opacity << 24) | rgb;
        if (!hasOpacityError) {
            errorMessage = null;
        }
    }
    
    private void updateSaveButtonState() {
        if (saveButton == null) {
            return;
        }
        String colorStr = colorField.getValue().trim();
        String opacityStr = opacityField.getValue().trim();
        
        if (colorStr.isEmpty() && opacityStr.isEmpty()) {
            saveButton.active = false;
            return;
        }
        
        boolean isValid = errorMessage == null;
        
        if (!colorStr.isEmpty()) {
            if (colorStr.startsWith("#")) {
                colorStr = colorStr.substring(1);
            }
            if (!colorStr.isEmpty()) {
                try {
                    int rgb = Integer.parseInt(colorStr, 16);
                    if (rgb < 0 || rgb > 0xFFFFFF) {
                        isValid = false;
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                }
            }
        }
        
        if (isValid && !opacityStr.isEmpty()) {
            try {
                int opacity = Integer.parseInt(opacityStr);
                if (opacity < 0 || opacity > 255) {
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                isValid = false;
            }
        }
        
        saveButton.active = isValid;
    }
    
    private void save() {
        String colorStr = colorField.getValue().trim();
        String opacityStr = opacityField.getValue().trim();
        
        if (colorStr.isEmpty() && opacityStr.isEmpty()) {
            return;
        }
        
        try {
            int currentColor = getCurrentColor();
            int currentRgb = currentColor & 0x00FFFFFF;
            int currentOpacity = (currentColor >> 24) & 0xFF;
            
            int rgb;
            if (colorStr.isEmpty()) {
                rgb = currentRgb;
            } else {
                if (colorStr.startsWith("#")) {
                    colorStr = colorStr.substring(1);
                }
                if (colorStr.isEmpty()) {
                    return;
                }
                rgb = Integer.parseInt(colorStr, 16);
                if (rgb < 0 || rgb > 0xFFFFFF) {
                    throw new NumberFormatException("RGB out of range");
                }
            }
            
            int opacity;
            if (opacityStr.isEmpty()) {
                opacity = currentOpacity;
            } else {
                opacity = Integer.parseInt(opacityStr);
                if (opacity < 0 || opacity > 255) {
                    throw new NumberFormatException("Opacity out of range");
                }
            }
            
            int color = (opacity << 24) | rgb;
            setCurrentColor(color);
            config.save();
            previewColor = color;
            
            colorField.setValue("");
            opacityField.setValue("");
            errorMessage = null;
            updatePreviewColor();
            updateSaveButtonState();
            
            unfocusTicks = 2;
        } catch (NumberFormatException e) {
            if (e.getMessage() != null && e.getMessage().contains("Opacity")) {
                errorMessage = "Opacity must be between 0 and 255";
            } else {
                errorMessage = "Invalid format. RGB: 000000-FFFFFF";
            }
        }
    }
    
    private void resetToDefault() {
        int defaultColor = currentColorType.getDefaultValue();
        setCurrentColor(defaultColor);
        config.save();
        previewColor = defaultColor;
        colorField.setValue("");
        opacityField.setValue("");
        errorMessage = null;
        updateSaveButtonState();
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            boolean clickedColorField = colorField != null && colorField.isMouseOver(event.x(), event.y());
            boolean clickedOpacityField = opacityField != null && opacityField.isMouseOver(event.x(), event.y());

            if (!clickedColorField && !clickedOpacityField) {
                if (this.getFocused() == colorField || this.getFocused() == opacityField) {
                    if (hiddenField != null) {
                        this.setFocused(hiddenField);
                    } else {
                        this.setFocused(null);
                    }
                }
                if (colorField != null) {
                    colorField.setFocused(false);
                }
                if (opacityField != null) {
                    opacityField.setFocused(false);
                }
            }
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        
        if (event.button() == 0) {
            int centerX = this.width / 2;
            int centerY = getLayoutCenterY();
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();

            if (isInsideOpacitySlider(mouseX, mouseY, centerX, centerY)) {
                setOpacityFromSlider(mouseX, centerX, centerY);
                opacitySliderDragging = true;
                return true;
            }

            Integer paletteColor = getPaletteColorAt(mouseX, mouseY, centerX, centerY);
            if (paletteColor != null) {
                int rgb = paletteColor & 0x00FFFFFF;
                colorField.setValue(String.format("#%06X", rgb));
                updatePreviewColor();
                updateSaveButtonState();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (super.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }
        
        if (opacitySliderDragging) {
            int centerX = this.width / 2;
            int centerY = getLayoutCenterY();
            setOpacityFromSlider((int) event.x(), centerX, centerY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }
        
        if (opacitySliderDragging) {
            opacitySliderDragging = false;
            return true;
        }
        return false;
    }
    
    @Override
    public void tick() {
        super.tick();
        if (unfocusTicks > 0) {
            unfocusTicks--;
            if (unfocusTicks == 0 && hiddenField != null) {
                this.setFocused(hiddenField);
            }
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawDimBackground(graphics);
        
        int centerX = this.width / 2;
        int centerY = getLayoutCenterY();
        
        Component title = Component.literal("Panel Colors").withStyle(ChatFormatting.BOLD);
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, centerX - titleWidth / 2, centerY - 100, 0xFFFFFFFF, false);
        
        int currentColorValue = getCurrentColor();
        int opacity = (currentColorValue >> 24) & 0xFF;
        int rgb = currentColorValue & 0x00FFFFFF;
        
        Component currentLabel = Component.literal("Current: ").withStyle(ChatFormatting.GRAY)
            .copy().append(Component.literal(String.format("#%06X", rgb)).withStyle(ChatFormatting.WHITE))
            .copy().append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
            .copy().append(Component.literal(String.valueOf(opacity)).withStyle(ChatFormatting.WHITE));
        int currentLabelWidth = this.font.width(currentLabel);
        graphics.drawString(this.font, currentLabel, centerX - currentLabelWidth / 2, centerY - 80, 0xFFFFFFFF, false);
        
        if (errorMessage != null) {
            Component error = Component.literal(errorMessage).withStyle(ChatFormatting.RED);
            int errorWidth = this.font.width(error);
            graphics.drawString(this.font, error, centerX - errorWidth / 2, centerY - 140, 0xFFFFFFFF, false);
        }
        
        renderPalette(graphics, mouseX, mouseY, centerX, centerY);
        renderOpacitySlider(graphics, mouseX, mouseY, centerX, centerY);
        
        if (checkerboardTextureId != null) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                checkerboardTextureId,
                centerX - 50,
                centerY - 5,
                0.0f,
                0.0f,
                100,
                20,
                100,
                20,
                100,
                20,
                0xFFFFFFFF
            );
        }
        int displayedPreviewColor = previewColor;
        Integer hoveredPaletteColor = getPaletteColorAt(mouseX, mouseY, centerX, centerY);
        if (hoveredPaletteColor != null) {
            int opacityFromUi = getOpacityForPalettePreview();
            displayedPreviewColor = (opacityFromUi << 24) | (hoveredPaletteColor & 0x00FFFFFF);
        }

        graphics.fill(centerX - 50, centerY - 5, centerX + 50, centerY + 15, displayedPreviewColor);
        graphics.fill(centerX - 51, centerY - 6, centerX - 50, centerY + 16, 0xFF000000);
        graphics.fill(centerX + 50, centerY - 6, centerX + 51, centerY + 16, 0xFF000000);
        graphics.fill(centerX - 51, centerY - 6, centerX + 51, centerY - 5, 0xFF000000);
        graphics.fill(centerX - 51, centerY + 15, centerX + 51, centerY + 16, 0xFF000000);
        
        colorField.render(graphics, mouseX, mouseY, delta);
        opacityField.render(graphics, mouseX, mouseY, delta);
        
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int cellSize = PALETTE_CELL_SIZE;
        int cols = PALETTE_COLS;
        int rows = PALETTE_ROWS;
        int paletteWidth = cols * cellSize;
        int paletteHeight = rows * cellSize;

        int resetButtonY = centerY + 50;
        int resetButtonHeight = 20;
        int paletteX = centerX - paletteWidth / 2;
        int paletteY = resetButtonY + resetButtonHeight + 5;

        graphics.fill(paletteX - 1, paletteY - 1, paletteX + paletteWidth + 1, paletteY + paletteHeight + 1, 0xFF000000);
        graphics.fill(paletteX, paletteY, paletteX + paletteWidth, paletteY + paletteHeight, 0xCC111417);

        if (paletteTextureId != null) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                paletteTextureId,
                paletteX,
                paletteY,
                0.0f,
                0.0f,
                paletteWidth,
                paletteHeight,
                paletteWidth,
                paletteHeight,
                0xFFFFFFFF
            );
        }

        Integer hovered = getPaletteColorAt(mouseX, mouseY, centerX, centerY);
        if (hovered != null) {
            int col = (mouseX - paletteX) / cellSize;
            int row = (mouseY - paletteY) / cellSize;
            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                int x1 = paletteX + col * cellSize;
                int y1 = paletteY + row * cellSize;
                int x2 = x1 + cellSize;
                int y2 = y1 + cellSize;
                graphics.fill(x1, y1, x1 + 1, y2, 0xFFFFFFFF);
                graphics.fill(x2 - 1, y1, x2, y2, 0xFFFFFFFF);
                graphics.fill(x1, y1, x2, y1 + 1, 0xFFFFFFFF);
                graphics.fill(x1, y2 - 1, x2, y2, 0xFFFFFFFF);
            }
        }
    }

    private void renderOpacitySlider(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int[] geo = getOpacitySliderGeometry(centerX, centerY);
        int x = geo[0];
        int y = geo[1];
        int w = geo[2];
        int h = geo[3];

        int borderColor = config.getBorderColor();
        int trackColor = config.getScrollbarTrackColor();
        int thumbColor = config.getScrollbarThumbColor();
        int dividerColor = config.getDividerColor();

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, borderColor);
        graphics.fill(x, y, x + w, y + h, trackColor);
        graphics.fill(x, y + h / 2, x + w, y + h / 2 + 1, dividerColor);

        int opacity = getOpacityForPalettePreview();
        int knobX = x + (int) Math.round((opacity / 255.0) * (w - 1));
        int knobLeft = Math.max(x, Math.min(x + w - OPACITY_SLIDER_KNOB_WIDTH, knobX - OPACITY_SLIDER_KNOB_WIDTH / 2));
        graphics.fill(knobLeft, y, knobLeft + OPACITY_SLIDER_KNOB_WIDTH, y + h, thumbColor);
    }

    private Integer getPaletteColorAt(int mouseX, int mouseY, int centerX, int centerY) {
        int cellSize = PALETTE_CELL_SIZE;
        int cols = PALETTE_COLS;
        int rows = PALETTE_ROWS;
        int paletteWidth = cols * cellSize;
        int paletteHeight = rows * cellSize;

        int resetButtonY = centerY + 50;
        int resetButtonHeight = 20;
        int paletteX = centerX - paletteWidth / 2;
        int paletteY = resetButtonY + resetButtonHeight + 5;

        if (mouseX < paletteX || mouseX >= paletteX + paletteWidth || mouseY < paletteY || mouseY >= paletteY + paletteHeight) {
            return null;
        }

        int col = (mouseX - paletteX) / cellSize;
        int row = (mouseY - paletteY) / cellSize;
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return null;
        }

        int rgb;
        if (row < 6) {
            float hue = col / (float) cols;
            float saturation = 1.0f;
            float value;
            if (row == 0) {
                value = 1.0f;
            } else if (row == 1) {
                value = 0.85f;
            } else if (row == 2) {
                value = 0.7f;
            } else if (row == 3) {
                value = 0.55f;
            } else if (row == 4) {
                value = 0.4f;
            } else {
                value = 0.25f;
            }
            rgb = hsvToRgb(hue, saturation, value);
        } else if (row == 6) {
            int gray = 255 - (int) Math.round(col * (255.0 / (cols - 1)));
            rgb = (gray << 16) | (gray << 8) | gray;
        } else {
            float hue = col / (float) cols;
            float saturation = 0.5f;
            float value = 0.5f;
            rgb = hsvToRgb(hue, saturation, value);
        }
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private int[] getOpacitySliderGeometry(int centerX, int centerY) {
        int cellSize = PALETTE_CELL_SIZE;
        int cols = PALETTE_COLS;
        int rows = PALETTE_ROWS;
        int paletteWidth = cols * cellSize;
        int paletteHeight = rows * cellSize;

        int resetButtonY = centerY + 50;
        int resetButtonHeight = 20;
        int paletteX = centerX - paletteWidth / 2;
        int paletteY = resetButtonY + resetButtonHeight + 5;

        int x = paletteX;
        int y = paletteY + paletteHeight + OPACITY_SLIDER_GAP;
        int w = paletteWidth;
        int h = OPACITY_SLIDER_HEIGHT;
        return new int[] { x, y, w, h };
    }

    private boolean isInsideOpacitySlider(int mouseX, int mouseY, int centerX, int centerY) {
        int[] geo = getOpacitySliderGeometry(centerX, centerY);
        int x = geo[0];
        int y = geo[1];
        int w = geo[2];
        int h = geo[3];
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void setOpacityFromSlider(int mouseX, int centerX, int centerY) {
        int[] geo = getOpacitySliderGeometry(centerX, centerY);
        int x = geo[0];
        int w = geo[2];

        int clamped = Math.max(0, Math.min(w - 1, mouseX - x));
        int opacity = (int) Math.round((clamped / (double) (w - 1)) * 255.0);
        opacity = Math.max(0, Math.min(255, opacity));

        if (opacityField != null) {
            opacityField.setValue(String.valueOf(opacity));
        }
        updatePreviewColor();
        updateSaveButtonState();
    }

    private int getOpacityForPalettePreview() {
        if (opacityField != null) {
            String opacityStr = opacityField.getValue().trim();
            if (!opacityStr.isEmpty()) {
                try {
                    int opacity = Integer.parseInt(opacityStr);
                    if (opacity >= 0 && opacity <= 255) {
                        return opacity;
                    }
                    return Math.max(0, Math.min(255, opacity));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return (getCurrentColor() >> 24) & 0xFF;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        switch (i % 6) {
            case 0:
                r = v; g = t; b = p;
                break;
            case 1:
                r = q; g = v; b = p;
                break;
            case 2:
                r = p; g = v; b = t;
                break;
            case 3:
                r = p; g = q; b = v;
                break;
            case 4:
                r = t; g = p; b = v;
                break;
            default:
                r = v; g = p; b = q;
                break;
        }
        int ri = (int) Math.round(r * 255.0);
        int gi = (int) Math.round(g * 255.0);
        int bi = (int) Math.round(b * 255.0);
        return (ri << 16) | (gi << 8) | bi;
    }
    
    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
    
    private void initializeTextures() {
        if (texturesInitialized) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
        
        createPaletteTexture(client);
        createCheckerboardTexture(client);
        
        texturesInitialized = true;
    }
    
    private void createPaletteTexture(Minecraft client) {
        int cols = PALETTE_COLS;
        int rows = PALETTE_ROWS;
        int cellSize = PALETTE_CELL_SIZE;
        int width = cols * cellSize;
        int height = rows * cellSize;
        
        NativeImage image = new NativeImage(width, height, false);
        
        Method setPixel = resolveNativeImageSetter(image);
        if (setPixel == null) {
            return;
        }
        
        boolean isABGR = setPixel.getName().equals("setPixelABGR");
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int rgb;
                if (row < 6) {
                    float hue = col / (float) cols;
                    float saturation = 1.0f;
                    float value;
                    if (row == 0) {
                        value = 1.0f;
                    } else if (row == 1) {
                        value = 0.85f;
                    } else if (row == 2) {
                        value = 0.7f;
                    } else if (row == 3) {
                        value = 0.55f;
                    } else if (row == 4) {
                        value = 0.4f;
                    } else {
                        value = 0.25f;
                    }
                    rgb = hsvToRgb(hue, saturation, value);
                } else {
                    int gray = 255 - (int) Math.round(col * (255.0 / (cols - 1)));
                    rgb = (gray << 16) | (gray << 8) | gray;
                }
                
                int argb = 0xFF000000 | (rgb & 0x00FFFFFF);
                int colorToSet = isABGR ? argbToAbgr(argb) : argb;
                
                for (int py = 0; py < cellSize; py++) {
                    for (int px = 0; px < cellSize; px++) {
                        try {
                            setPixel.invoke(image, col * cellSize + px, row * cellSize + py, colorToSet);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        
        paletteTexture = new DynamicTexture(() -> "chunkloader_palette", image);
        trySetFilter(paletteTexture, false, false);
        paletteTextureId = registerDynamicTexture(client.getTextureManager(), paletteTexture);
        tryUpload(paletteTexture);
    }
    
    private void createCheckerboardTexture(Minecraft client) {
        int checkerSize = 4;
        int width = 100;
        int height = 20;
        
        NativeImage image = new NativeImage(width, height, false);
        
        Method setPixel = resolveNativeImageSetter(image);
        if (setPixel == null) {
            return;
        }
        
        boolean isABGR = setPixel.getName().equals("setPixelABGR");
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean isLight = ((x / checkerSize) + (y / checkerSize)) % 2 == 0;
                int color = isLight ? 0xFFFFFFFF : 0xFFCCCCCC;
                int colorToSet = isABGR ? argbToAbgr(color) : color;
                try {
                    setPixel.invoke(image, x, y, colorToSet);
                } catch (Exception ignored) {
                }
            }
        }
        
        checkerboardTexture = new DynamicTexture(() -> "chunkloader_checkerboard", image);
        trySetFilter(checkerboardTexture, false, false);
        checkerboardTextureId = registerDynamicTexture(client.getTextureManager(), checkerboardTexture);
        tryUpload(checkerboardTexture);
    }
    
    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
    
    private Method resolveNativeImageSetter(NativeImage image) {
        Method m = findMethod(image.getClass(), "setPixelRGBA", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(image.getClass(), "setPixelRGBAUnsafe", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(image.getClass(), "setPixelABGR", int.class, int.class, int.class);
        if (m != null) return m;
        m = findMethod(image.getClass(), "setPixel", int.class, int.class, int.class);
        if (m != null) return m;
        return null;
    }
    
    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
    
    private static void tryUpload(DynamicTexture tex) {
        try {
            Method upload = findMethod(DynamicTexture.class, "upload");
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "upload");
            }
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "uploadTexture");
            }
            if (upload == null) {
                upload = findDeclaredNoArg(DynamicTexture.class, "uploadIfDirty");
            }
            if (upload != null) {
                upload.invoke(tex);
            }
        } catch (Throwable ignored) {
        }
    }
    
    private static void trySetFilter(DynamicTexture tex, boolean blur, boolean mipmap) {
        try {
            Method setFilter = findMethod(DynamicTexture.class, "setFilter", boolean.class, boolean.class);
            if (setFilter == null) {
                setFilter = findMethod(DynamicTexture.class, "setFilter", Boolean.class, Boolean.class);
            }
            if (setFilter != null) {
                setFilter.invoke(tex, blur, mipmap);
            }
        } catch (Throwable ignored) {
        }
    }
    
    private static ResourceLocation registerDynamicTexture(TextureManager tm, DynamicTexture tex) {
        String key = "chunkloader_panel_color/" + System.nanoTime();
        
        try {
            Method m = findMethod(tm.getClass(), "register", String.class, tex.getClass());
            if (m == null) {
                Class<?> abstractTexture = Class.forName("net.minecraft.client.renderer.texture.AbstractTexture");
                m = findMethod(tm.getClass(), "register", String.class, abstractTexture);
            }
            if (m == null) {
                m = tm.getClass().getDeclaredMethod("register", String.class, Object.class);
                m.setAccessible(true);
            }
            if (m != null && ResourceLocation.class.isAssignableFrom(m.getReturnType())) {
                Object id = m.invoke(tm, key, tex);
                if (id instanceof ResourceLocation rl) {
                    return rl;
                }
            }
        } catch (Throwable ignored) {
        }
        
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("chunkloader", "panel_color/" + System.nanoTime());
        try {
            tm.register(rl, tex);
        } catch (Throwable ignored) {
        }
        return rl;
    }
    
    
    private static Method findDeclaredNoArg(Class<?> clazz, String name) {
        try {
            Method m = clazz.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
    
    @Override
    public void onClose() {
        cleanupTextures();
        super.onClose();
    }
    
    private void cleanupTextures() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getTextureManager() != null) {
            if (paletteTextureId != null) {
                client.getTextureManager().release(paletteTextureId);
                paletteTextureId = null;
            }
            if (checkerboardTextureId != null) {
                client.getTextureManager().release(checkerboardTextureId);
                checkerboardTextureId = null;
            }
        }
        if (paletteTexture != null) {
            paletteTexture.close();
            paletteTexture = null;
        }
        if (checkerboardTexture != null) {
            checkerboardTexture.close();
            checkerboardTexture = null;
        }
        texturesInitialized = false;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

