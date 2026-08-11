package de.chunkloader.client.screen;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
public class RenameChunkloaderScreen extends Screen {

    private final Screen parent;
    private final int chunkX;
    private final int chunkZ;
    private final String dimension;
    private final String currentName;

    private EditBox nameField;
    private Button saveButton;
    private String warningMessage = null;
    private boolean isClosed = false;
    private boolean hasServerWarning = false;

    public RenameChunkloaderScreen(Screen parent, int chunkX, int chunkZ, String dimension, String currentName) {
        super(Component.literal("Rename Player"));
        this.parent = parent;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension != null && !dimension.isBlank() ? dimension : "minecraft:overworld";
        this.currentName = currentName != null ? currentName : "";
    }
    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 200;
        int fieldHeight = 20;
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        nameField = new EditBox(this.font, centerX - fieldWidth / 2, centerY - 30, fieldWidth, fieldHeight, Component.literal("Name"));
        nameField.setMaxLength(16);
        nameField.setValue(currentName);
        nameField.setResponder(text -> {
            hasServerWarning = false;
            updateSaveButtonState();
        });
        this.addRenderableWidget(nameField);

        int buttonWidth = 100;
        int buttonSpacing = 110;

        saveButton = Button.builder(
                Component.literal("Save"),
                btn -> save())
            .bounds(centerX - buttonSpacing, centerY + 20, buttonWidth, 20)
            .build();
        updateSaveButtonState();
        this.addRenderableWidget(saveButton);

        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> {
                    isClosed = true;
                    this.minecraft.gui.setScreen(parent);
                })
            .bounds(centerX + buttonSpacing - buttonWidth, centerY + 20, buttonWidth, 20)
            .build()
        );

        this.setInitialFocus(nameField);
    }

    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() > 16) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9]+$");
    }

    private void updateSaveButtonState() {
        if (saveButton == null) {
            return;
        }
        String newName = nameField.getValue().trim();
        boolean isValid = !newName.isEmpty() && !newName.equals(currentName) && isValidName(newName);
        saveButton.active = isValid;

        if (!hasServerWarning) {
            if (!newName.isEmpty() && !isValidName(newName)) {
                warningMessage = "Name must be 1-16 characters and can only contain letters and numbers";
            } else {
                warningMessage = null;
            }
        }
    }

    private void save() {
        String newName = nameField.getValue().trim();
        if (newName.isEmpty() || newName.equals(currentName)) {
            return;
        }

        if (!isValidName(newName)) {
            warningMessage = "Name must be 1-16 characters and can only contain letters and numbers";
            updateSaveButtonState();
            return;
        }

        warningMessage = null;
        if (saveButton != null) {
            saveButton.active = false;
        }

        ChunkloaderNetworking.sendRenameChunkloader(chunkX, chunkZ, dimension, newName);
    }

    public void handleRenameResponse(RenameChunkloaderResponsePayload payload) {
        if (isClosed) {
            return;
        }

        if (payload.success()) {
            FakePlayerNameCache.clear();
            FakePlayerVisibilityCache.clear();
            isClosed = true;
            this.minecraft.gui.setScreen(null);
        } else {
            hasServerWarning = true;
            warningMessage = payload.message() != null ? payload.message() : "This name is already in use or invalid.";
            if (saveButton != null) {
                saveButton.active = true;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Component title = Component.literal("Rename Player").withStyle(ChatFormatting.BOLD);
        int titleWidth = this.font.width(title);
        context.text(this.font, title, centerX - titleWidth / 2, centerY - 60, 0xFFFFFFFF, false);

        Component currentNameLabel = Component.literal("Current name: ").withStyle(ChatFormatting.GRAY)
            .copy().append(Component.literal(currentName).withStyle(ChatFormatting.WHITE));
        int labelWidth = this.font.width(currentNameLabel);
        context.text(this.font, currentNameLabel, centerX - labelWidth / 2, centerY - 50, 0xFFFFFFFF, false);

        nameField.extractRenderState(context, mouseX, mouseY, delta);

        if (warningMessage != null) {
            Component warning = Component.literal(warningMessage).withStyle(ChatFormatting.RED);
            int warningWidth = this.font.width(warning);
            context.text(this.font, warning, centerX - warningWidth / 2, centerY + 5, 0xFFFFFFFF, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawDimBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 0 && nameField != null && !nameField.isMouseOver(click.x(), click.y())) {
            if (this.getFocused() == nameField) {
                this.setFocused(null);
            }
            nameField.setFocused(false);
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

