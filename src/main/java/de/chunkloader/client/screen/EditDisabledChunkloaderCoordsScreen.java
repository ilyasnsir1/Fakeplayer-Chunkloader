package de.chunkloader.client.screen;

import de.chunkloader.client.network.ChunkloaderClientNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class EditDisabledChunkloaderCoordsScreen extends Screen {

    private final Screen parent;
    private final DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry;

    private EditBox blockXField;
    private EditBox blockYField;
    private EditBox blockZField;
    private Button saveButton;

    private String warningMessage = null;
    private boolean isClosed = false;
    private UpdateDisabledChunkloaderCoordsResponsePayload pendingResponse = null;
    private long updateStartTime = 0;
    private static final long UPDATE_DISPLAY_DURATION_MS = 1500;

    private void drawDimBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    public EditDisabledChunkloaderCoordsScreen(Screen parent, DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry) {
        super(Component.literal("Edit Coordinates"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 100;
        int fieldHeight = 20;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int spacing = 30;

        var font = this.font;

        int labelWidth = Math.max(
                    font.width("Block X:"),
                    Math.max(
                        font.width("Block Y:"),
                        font.width("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
        int fieldX = labelX + labelWidth + 10;

        blockXField = new EditBox(font, fieldX, startY, fieldWidth, fieldHeight, Component.literal("X coordinate"));
        blockXField.setMaxLength(11);
        blockXField.setValue("");
        blockXField.setResponder(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockXField.setValue(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonState();
        });
        this.addWidget(blockXField);

        blockYField = new EditBox(font, fieldX, startY + spacing, fieldWidth, fieldHeight, Component.literal("Y coordinate"));
        blockYField.setMaxLength(11);
        blockYField.setValue("");
        blockYField.setResponder(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockYField.setValue(filtered);
                return;
            }
            updateSaveButtonState();
        });
        this.addWidget(blockYField);

        blockZField = new EditBox(font, fieldX, startY + spacing * 2, fieldWidth, fieldHeight, Component.literal("Z coordinate"));
        blockZField.setMaxLength(11);
        blockZField.setValue("");
        blockZField.setResponder(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockZField.setValue(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonState();
        });
        this.addWidget(blockZField);

        int buttonWidth = 100;
        int buttonSpacing = 110;

        saveButton = Button.builder(
                Component.literal("Save"),
                btn -> save())
            .bounds(centerX - buttonSpacing, startY + spacing * 3 + 20, buttonWidth, 20)
            .build();
        saveButton.active = false;
        this.addRenderableWidget(saveButton);

        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> {
                    isClosed = true;
                    this.minecraft.setScreen(parent);
                })
            .bounds(centerX + buttonSpacing - buttonWidth, startY + spacing * 3 + 20, buttonWidth, 20)
            .build()
        );
    }

    private void updateChunkDisplay() {
    }

    private void updateSaveButtonState() {
        if (saveButton == null) {
            return;
        }
        String blockXText = blockXField.getValue().trim();
        String blockYText = blockYField.getValue().trim();
        String blockZText = blockZField.getValue().trim();

        boolean allFieldsFilled = !blockXText.isEmpty() && !blockYText.isEmpty() && !blockZText.isEmpty();

        if (allFieldsFilled) {
            try {
                Integer.parseInt(blockXText);
                Integer.parseInt(blockYText);
                Integer.parseInt(blockZText);
                saveButton.active = true;
            } catch (NumberFormatException e) {
                saveButton.active = false;
            }
        } else {
            saveButton.active = false;
        }
    }

    private void save() {
        warningMessage = null;
        try {
            String blockXText = blockXField.getValue().trim();
            String blockYText = blockYField.getValue().trim();
            String blockZText = blockZField.getValue().trim();

            if (blockXText.isEmpty() || blockYText.isEmpty() || blockZText.isEmpty()) {
                warningMessage = "All fields must be filled!";
                return;
            }

            int newBlockX = Integer.parseInt(blockXText);
            int newBlockY = Integer.parseInt(blockYText);
            int newBlockZ = Integer.parseInt(blockZText);

            int newChunkX = newBlockX >> 4;
            int newChunkZ = newBlockZ >> 4;

            if (saveButton != null) {
                saveButton.active = false;
            }

            ChunkloaderClientNetworking.sendUpdateDisabledChunkloaderCoords(
                entry.chunkX(), entry.chunkZ(), entry.dimension(),
                newChunkX, newChunkZ,
                newBlockX, newBlockY, newBlockZ
            );

            warningMessage = "Updating...";
            updateStartTime = System.currentTimeMillis();
            pendingResponse = null;
        } catch (NumberFormatException e) {
            warningMessage = "Invalid number format!";
            if (saveButton != null) {
                updateSaveButtonState();
            }
        }
    }

    public void handleUpdateResponse(UpdateDisabledChunkloaderCoordsResponsePayload payload) {
        if (isClosed) {
            return;
        }

        pendingResponse = payload;
    }

    private void processPendingResponse() {
        if (pendingResponse == null || isClosed) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (updateStartTime <= 0) {
            return;
        }

        long elapsed = currentTime - updateStartTime;
        if (elapsed < UPDATE_DISPLAY_DURATION_MS) {
            return;
        }

        UpdateDisabledChunkloaderCoordsResponsePayload response = pendingResponse;
        pendingResponse = null;
        updateStartTime = 0;

        if (response.success()) {
            isClosed = true;
            this.minecraft.setScreen(parent);
            ChunkloaderClientNetworking.requestDisabledChunkloadersList();
        } else {
            warningMessage = response.message() != null ? response.message() : "Failed to update coordinates.";
            if (saveButton != null) {
                saveButton.active = true;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (pendingResponse != null && !isClosed) {
            processPendingResponse();
        }

        drawDimBackground(graphics);

        var font = this.font;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int spacing = 30;
        int fieldWidth = 100;

        String name = entry.name() != null ? entry.name() : "Unnamed";
        ChatFormatting nameColor;
        if (entry.allowMobSpawning()) {
            if (entry.hasWarning()) {
                nameColor = ChatFormatting.YELLOW;
            } else {
                nameColor = ChatFormatting.GREEN;
            }
        } else {
            nameColor = ChatFormatting.BLUE;
        }

        Component infoText = Component.literal("⚠ Enter block coordinates, not your own coordinates!")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        int infoWidth = font.width(infoText);
        graphics.drawString(font, infoText, centerX - infoWidth / 2, startY - 70, 0xFFFFFF00, false);

        Component title = Component.literal("Edit Coordinates: ").withStyle(ChatFormatting.BOLD)
            .append(Component.literal(name).withStyle(nameColor));
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centerX - titleWidth / 2, startY - 30, 0xFFFFFFFF, false);

        int labelWidth = Math.max(
                    font.width("Block X:"),
                    Math.max(
                        font.width("Block Y:"),
                        font.width("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
        int fieldX = labelX + labelWidth + 10;
        int labelY = startY + 5;

        int chunkX = 0;
        int chunkZ = 0;
        try {
            String blockXText = blockXField.getValue().trim();
            String blockZText = blockZField.getValue().trim();
            if (!blockXText.isEmpty() && !blockZText.isEmpty()) {
                int blockX = Integer.parseInt(blockXText);
                int blockZ = Integer.parseInt(blockZText);
                chunkX = blockX >> 4;
                chunkZ = blockZ >> 4;
            } else {
                chunkX = entry.blockX() >> 4;
                chunkZ = entry.blockZ() >> 4;
            }
        } catch (NumberFormatException e) {
            chunkX = entry.blockX() >> 4;
            chunkZ = entry.blockZ() >> 4;
        }

        Component chunkInfo = Component.literal("Chunk: " + chunkX + ", " + chunkZ).withStyle(ChatFormatting.GRAY);
        int chunkInfoWidth = font.width(chunkInfo);
        graphics.drawString(font, chunkInfo, centerX - chunkInfoWidth / 2, startY - 15, 0xFFCCCCCC, false);

        graphics.drawString(font, Component.literal("Block X:"), labelX, labelY, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.literal("Block Y:"), labelX, labelY + spacing, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.literal("Block Z:"), labelX, labelY + spacing * 2, 0xFFCCCCCC, false);

        blockXField.render(graphics, mouseX, mouseY, delta);
        blockYField.render(graphics, mouseX, mouseY, delta);
        blockZField.render(graphics, mouseX, mouseY, delta);

        int fieldHeight = 20;
        int fieldPadding = 4;

        if (blockXField.getValue().isEmpty() && !blockXField.isFocused()) {
            Component placeholder = Component.literal("X coordinate").withStyle(ChatFormatting.GRAY);
            int fieldY = blockXField.getY();
            int textY = fieldY + (fieldHeight - 8) / 2;
            graphics.drawString(font, placeholder, fieldX + fieldPadding, textY, 0xFF808080, false);
        }
        if (blockYField.getValue().isEmpty() && !blockYField.isFocused()) {
            Component placeholder = Component.literal("Y coordinate").withStyle(ChatFormatting.GRAY);
            int fieldY = blockYField.getY();
            int textY = fieldY + (fieldHeight - 8) / 2;
            graphics.drawString(font, placeholder, fieldX + fieldPadding, textY, 0xFF808080, false);
        }
        if (blockZField.getValue().isEmpty() && !blockZField.isFocused()) {
            Component placeholder = Component.literal("Z coordinate").withStyle(ChatFormatting.GRAY);
            int fieldY = blockZField.getY();
            int textY = fieldY + (fieldHeight - 8) / 2;
            graphics.drawString(font, placeholder, fieldX + fieldPadding, textY, 0xFF808080, false);
        }

        if (warningMessage != null) {
            ChatFormatting color = warningMessage.startsWith("Updating") ? ChatFormatting.YELLOW : ChatFormatting.RED;
            Component warning = Component.literal(warningMessage).withStyle(color);
            int warningWidth = font.width(warning);
            graphics.drawString(font, warning, centerX - warningWidth / 2, startY + spacing * 3 + 5, 0xFFFFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            boolean clickedAnyField = (blockXField != null && blockXField.isMouseOver(event.x(), event.y()))
                || (blockYField != null && blockYField.isMouseOver(event.x(), event.y()))
                || (blockZField != null && blockZField.isMouseOver(event.x(), event.y()));

            if (!clickedAnyField) {
                if (this.getFocused() == blockXField || this.getFocused() == blockYField || this.getFocused() == blockZField) {
                    this.setFocused(null);
                }
                if (blockXField != null) {
                    blockXField.setFocused(false);
                }
                if (blockYField != null) {
                    blockYField.setFocused(false);
                }
                if (blockZField != null) {
                    blockZField.setFocused(false);
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    public Screen getParent() {
        return parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
    }
}

