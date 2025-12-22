package de.chunkloader.client.screen;

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
    private final String currentName;
    
    private TextFieldWidget nameField;
    private ButtonWidget saveButton;
    private String warningMessage = null;
    private boolean isClosed = false;
    private boolean hasServerWarning = false;
    
    public RenameChunkloaderScreen(Screen parent, int chunkX, int chunkZ, String currentName) {
        super(Text.literal("Rename Chunkloader"));
        this.parent = parent;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.currentName = currentName != null ? currentName : "";
    }
    
    @Override
    protected void init() {
        super.init();
        
        int fieldWidth = 200;
        int fieldHeight = 20;
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, centerY - 30, fieldWidth, fieldHeight, Text.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setText(currentName);
        nameField.setChangedListener(text -> {
            hasServerWarning = false;
            updateSaveButtonState();
        });
        this.addDrawableChild(nameField);
        
        int buttonWidth = 100;
        int buttonSpacing = 110;
        
        saveButton = ButtonWidget.builder(
                Text.literal("Save"),
                btn -> save())
            .dimensions(centerX - buttonSpacing, centerY + 20, buttonWidth, 20)
            .build();
        updateSaveButtonState();
        this.addDrawableChild(saveButton);
        
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
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
        return name.matches("^[a-zA-Z0-9]+$");
    }
    
    private void updateSaveButtonState() {
        if (saveButton == null) {
            return;
        }
        String newName = nameField.getText().trim();
        boolean isValid = !newName.isEmpty() && !newName.equals(currentName) && isValidName(newName);
        saveButton.active = isValid;
        
        if (!hasServerWarning) {
            if (!newName.isEmpty() && !isValidName(newName)) {
                warningMessage = "Name can only contain letters and numbers";
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
            warningMessage = "Name can only contain letters and numbers";
            updateSaveButtonState();
            return;
        }
        
        warningMessage = null;
        if (saveButton != null) {
            saveButton.active = false;
        }
        
        ChunkloaderNetworking.sendRenameChunkloader(chunkX, chunkZ, newName);
    }
    
    public void handleRenameResponse(RenameChunkloaderResponsePayload payload) {
        if (isClosed) {
            return;
        }
        
        if (payload.success()) {
            isClosed = true;
            this.client.setScreen(null);
        } else {
            hasServerWarning = true;
            warningMessage = payload.message() != null ? payload.message() : "This name is already in use or invalid.";
            if (saveButton != null) {
                saveButton.active = true;
            }
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        Text title = Text.literal("Rename Chunkloader").formatted(Formatting.BOLD);
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

