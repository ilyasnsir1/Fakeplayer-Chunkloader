package de.chunkloader.client.screen;

import de.chunkloader.client.FakePlayerNameCache;
import de.chunkloader.client.FakePlayerVisibilityCache;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.RenameChunkloaderResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class RenameChunkloaderScreen extends Screen {

    private final Screen parent;
    private final int chunkX;
    private final int chunkZ;
    private final String dimension;
    private final String currentName;

    private TextFieldWidget nameField;
    private ButtonWidget saveButtonWidget;
    private String warningMessage = null;
    private boolean isClosed = false;
    private boolean hasServerWarning = false;

    public RenameChunkloaderScreen(Screen parent, int chunkX, int chunkZ, String dimension, String currentName) {
        super(Text.literal("Rename Player"));
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

        nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, centerY - 30, fieldWidth, fieldHeight, Text.literal("Name"));
        nameField.setMaxLength(16);
        nameField.setText(currentName);
        nameField.setChangedListener(text -> {
            hasServerWarning = false;
            updateSaveButtonWidgetState();
        });
        this.addDrawableChild(nameField);

        int buttonWidth = 100;
        int buttonSpacing = 110;

        saveButtonWidget = ButtonWidget.builder(
                Text.literal("Save"),
                btn -> save())
            .dimensions(centerX - buttonSpacing, centerY + 20, buttonWidth, 20)
            .build();
        updateSaveButtonWidgetState();
        this.addDrawableChild(saveButtonWidget);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> {
                    isClosed = true;
                    this.client.setScreen(parent);
                })
            .dimensions(centerX + buttonSpacing - buttonWidth, centerY + 20, buttonWidth, 20)
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

    private void updateSaveButtonWidgetState() {
        if (saveButtonWidget == null) {
            return;
        }
        String newName = nameField.getText().trim();
        boolean isValid = !newName.isEmpty() && !newName.equals(currentName) && isValidName(newName);
        saveButtonWidget.active = isValid;

        if (!hasServerWarning) {
            if (!newName.isEmpty() && !isValidName(newName)) {
                warningMessage = "Name must be 1-16 characters and can only contain letters and numbers";
            } else {
                warningMessage = null;
            }
        }
    }

    private void save() {
        String newName = nameField.getText().trim();
        if (newName.isEmpty() || newName.equals(currentName)) {
            return;
        }

        if (!isValidName(newName)) {
            warningMessage = "Name must be 1-16 characters and can only contain letters and numbers";
            updateSaveButtonWidgetState();
            return;
        }

        warningMessage = null;
        if (saveButtonWidget != null) {
            saveButtonWidget.active = false;
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
            this.client.setScreen(null);
        } else {
            hasServerWarning = true;
            warningMessage = payload.message() != null ? payload.message() : "This name is already in use or invalid.";
            if (saveButtonWidget != null) {
                saveButtonWidget.active = true;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Text title = Text.literal("Rename Player").formatted(Formatting.BOLD);
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, centerX - titleWidth / 2, centerY - 60, 0xFFFFFFFF, false);

        Text currentNameLabel = Text.literal("Current name: ").formatted(Formatting.GRAY)
            .copy().append(Text.literal(currentName).formatted(Formatting.WHITE));
        int labelWidth = this.textRenderer.getWidth(currentNameLabel);
        context.drawText(this.textRenderer, currentNameLabel, centerX - labelWidth / 2, centerY - 50, 0xFFFFFFFF, false);

        nameField.render(context, mouseX, mouseY, delta);

        if (warningMessage != null) {
            Text warning = Text.literal(warningMessage).formatted(Formatting.RED);
            int warningWidth = this.textRenderer.getWidth(warning);
            context.drawText(this.textRenderer, warning, centerX - warningWidth / 2, centerY + 5, 0xFFFFFFFF, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        if (click.button() == 0 && nameField != null && !nameField.isMouseOver(click.x(), click.y())) {
            if (this.getFocused() == nameField) {
                this.setFocused(null);
            }
            nameField.setFocused(false);
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

