package de.chunkloader.client.screen;

import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
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

        Font renderer = this.font;

        int labelWidth = Math.max(
                    renderer.width("Block X:"),
                    Math.max(
                        renderer.width("Block Y:"),
                        renderer.width("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
        int fieldX = labelX + labelWidth + 10;

        blockXField = new EditBox(renderer, fieldX, startY, fieldWidth, fieldHeight, Component.literal("X coordinate"));
        blockXField.setMaxLength(11);
        blockXField.setValue("");
        blockXField.setHint(Component.literal("X coordinate").withStyle(ChatFormatting.GRAY));
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

        blockYField = new EditBox(renderer, fieldX, startY + spacing, fieldWidth, fieldHeight, Component.literal("Y coordinate"));
        blockYField.setMaxLength(11);
        blockYField.setValue("");
        blockYField.setHint(Component.literal("Y coordinate").withStyle(ChatFormatting.GRAY));
        blockYField.setResponder(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockYField.setValue(filtered);
                return;
            }
            updateSaveButtonState();
        });
        this.addWidget(blockYField);

        blockZField = new EditBox(renderer, fieldX, startY + spacing * 2, fieldWidth, fieldHeight, Component.literal("Z coordinate"));
        blockZField.setMaxLength(11);
        blockZField.setValue("");
        blockZField.setHint(Component.literal("Z coordinate").withStyle(ChatFormatting.GRAY));
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
                    this.minecraft.gui.setScreen(parent);
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

            ChunkloaderNetworking.sendUpdateDisabledChunkloaderCoords(
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
            this.minecraft.gui.setScreen(parent);
            ChunkloaderNetworking.requestDisabledChunkloadersList();
        } else {
            warningMessage = response.message() != null ? response.message() : "Failed to update coordinates.";
            if (saveButton != null) {
                saveButton.active = true;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (pendingResponse != null && !isClosed) {
            processPendingResponse();
        }

        context.fill(0, 0, this.width, this.height, 0xC0101010);

        Font renderer = this.font;
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
        int infoWidth = renderer.width(infoText);
        context.text(renderer, infoText, centerX - infoWidth / 2, startY - 70, 0xFFFFFF00, false);

        Component title = Component.literal("Edit Coordinates: ").withStyle(ChatFormatting.BOLD)
            .append(Component.literal(name).withStyle(nameColor));
        int titleWidth = renderer.width(title);
        context.text(renderer, title, centerX - titleWidth / 2, startY - 30, 0xFFFFFFFF, false);

        int labelWidth = Math.max(
                    renderer.width("Block X:"),
                    Math.max(
                        renderer.width("Block Y:"),
                        renderer.width("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
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
        int chunkInfoWidth = renderer.width(chunkInfo);
        context.text(renderer, chunkInfo, centerX - chunkInfoWidth / 2, startY - 15, 0xFFCCCCCC, false);

        context.text(renderer, Component.literal("Block X:"), labelX, labelY, 0xFFCCCCCC, false);
        context.text(renderer, Component.literal("Block Y:"), labelX, labelY + spacing, 0xFFCCCCCC, false);
        context.text(renderer, Component.literal("Block Z:"), labelX, labelY + spacing * 2, 0xFFCCCCCC, false);

        blockXField.extractRenderState(context, mouseX, mouseY, delta);
        blockYField.extractRenderState(context, mouseX, mouseY, delta);
        blockZField.extractRenderState(context, mouseX, mouseY, delta);

        if (warningMessage != null) {
            ChatFormatting color = warningMessage.startsWith("Updating") ? ChatFormatting.YELLOW : ChatFormatting.RED;
            Component warning = Component.literal(warningMessage).withStyle(color);
            int warningWidth = renderer.width(warning);
            context.text(renderer, warning, centerX - warningWidth / 2, startY + spacing * 3 + 5, 0xFFFFFFFF, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    public Screen getParent() {
        return parent;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();

            boolean clickedOnField = (blockXField != null && blockXField.isMouseOver(mouseX, mouseY))
                || (blockYField != null && blockYField.isMouseOver(mouseX, mouseY))
                || (blockZField != null && blockZField.isMouseOver(mouseX, mouseY));

            if (!clickedOnField) {
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

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

