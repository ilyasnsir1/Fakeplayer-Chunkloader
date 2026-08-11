package de.chunkloader.client.screen;

import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class EditDisabledChunkloaderCoordsScreen extends Screen {

    private final Screen parent;
    private final DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry;

    private TextFieldWidget blockXField;
    private TextFieldWidget blockYField;
    private TextFieldWidget blockZField;
    private ButtonWidget saveButtonWidget;

    private String warningMessage = null;
    private boolean isClosed = false;
    private UpdateDisabledChunkloaderCoordsResponsePayload pendingResponse = null;
    private long updateStartTime = 0;
    private static final long UPDATE_DISPLAY_DURATION_MS = 1500;

    public EditDisabledChunkloaderCoordsScreen(Screen parent, DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry) {
        super(Text.literal("Edit Coordinates"));
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

        TextRenderer renderer = this.textRenderer;

        int labelWidth = Math.max(
                    renderer.getWidth("Block X:"),
                    Math.max(
                        renderer.getWidth("Block Y:"),
                        renderer.getWidth("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
        int fieldX = labelX + labelWidth + 10;

        blockXField = new TextFieldWidget(renderer, fieldX, startY, fieldWidth, fieldHeight, Text.literal("X coordinate"));
        blockXField.setMaxLength(11);
        blockXField.setText("");
        blockXField.setPlaceholder(Text.literal("X coordinate").formatted(Formatting.GRAY));
        blockXField.setChangedListener(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockXField.setText(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonWidgetState();
        });
        this.addSelectableChild(blockXField);

        blockYField = new TextFieldWidget(renderer, fieldX, startY + spacing, fieldWidth, fieldHeight, Text.literal("Y coordinate"));
        blockYField.setMaxLength(11);
        blockYField.setText("");
        blockYField.setPlaceholder(Text.literal("Y coordinate").formatted(Formatting.GRAY));
        blockYField.setChangedListener(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockYField.setText(filtered);
                return;
            }
            updateSaveButtonWidgetState();
        });
        this.addSelectableChild(blockYField);

        blockZField = new TextFieldWidget(renderer, fieldX, startY + spacing * 2, fieldWidth, fieldHeight, Text.literal("Z coordinate"));
        blockZField.setMaxLength(11);
        blockZField.setText("");
        blockZField.setPlaceholder(Text.literal("Z coordinate").formatted(Formatting.GRAY));
        blockZField.setChangedListener(text -> {
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockZField.setText(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonWidgetState();
        });
        this.addSelectableChild(blockZField);

        int buttonWidth = 100;
        int buttonSpacing = 110;

        saveButtonWidget = ButtonWidget.builder(
                Text.literal("Save"),
                btn -> save())
            .dimensions(centerX - buttonSpacing, startY + spacing * 3 + 20, buttonWidth, 20)
            .build();
        saveButtonWidget.active = false;
        this.addDrawableChild(saveButtonWidget);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> {
                    isClosed = true;
                    this.client.setScreen(parent);
                })
            .dimensions(centerX + buttonSpacing - buttonWidth, startY + spacing * 3 + 20, buttonWidth, 20)
            .build()
        );

    }

    private void updateChunkDisplay() {
    }

    private void updateSaveButtonWidgetState() {
        if (saveButtonWidget == null) {
            return;
        }
        String blockXText = blockXField.getText().trim();
        String blockYText = blockYField.getText().trim();
        String blockZText = blockZField.getText().trim();

        boolean allFieldsFilled = !blockXText.isEmpty() && !blockYText.isEmpty() && !blockZText.isEmpty();

        if (allFieldsFilled) {
            try {
                Integer.parseInt(blockXText);
                Integer.parseInt(blockYText);
                Integer.parseInt(blockZText);
                saveButtonWidget.active = true;
            } catch (NumberFormatException e) {
                saveButtonWidget.active = false;
            }
        } else {
            saveButtonWidget.active = false;
        }
    }

    private void save() {
        warningMessage = null;
        try {
            String blockXText = blockXField.getText().trim();
            String blockYText = blockYField.getText().trim();
            String blockZText = blockZField.getText().trim();

            if (blockXText.isEmpty() || blockYText.isEmpty() || blockZText.isEmpty()) {
                warningMessage = "All fields must be filled!";
                return;
            }

            int newBlockX = Integer.parseInt(blockXText);
            int newBlockY = Integer.parseInt(blockYText);
            int newBlockZ = Integer.parseInt(blockZText);

            int newChunkX = newBlockX >> 4;
            int newChunkZ = newBlockZ >> 4;

            if (saveButtonWidget != null) {
                saveButtonWidget.active = false;
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
            if (saveButtonWidget != null) {
                updateSaveButtonWidgetState();
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
            this.client.setScreen(parent);
            ChunkloaderNetworking.requestDisabledChunkloadersList();
        } else {
            warningMessage = response.message() != null ? response.message() : "Failed to update coordinates.";
            if (saveButtonWidget != null) {
                saveButtonWidget.active = true;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pendingResponse != null && !isClosed) {
            processPendingResponse();
        }

        context.fill(0, 0, this.width, this.height, 0xC0101010);

        TextRenderer renderer = this.textRenderer;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int spacing = 30;
        int fieldWidth = 100;

        String name = entry.name() != null ? entry.name() : "Unnamed";
        Formatting nameColor;
        if (entry.allowMobSpawning()) {
            if (entry.hasWarning()) {
                nameColor = Formatting.YELLOW;
            } else {
                nameColor = Formatting.GREEN;
            }
        } else {
            nameColor = Formatting.BLUE;
        }

        Text infoText = Text.literal("⚠ Enter block coordinates, not your own coordinates!")
            .formatted(Formatting.YELLOW, Formatting.ITALIC);
        int infoWidth = renderer.getWidth(infoText);
        context.drawText(renderer, infoText, centerX - infoWidth / 2, startY - 70, 0xFFFFFF00, false);

        Text title = Text.literal("Edit Coordinates: ").formatted(Formatting.BOLD)
            .append(Text.literal(name).formatted(nameColor));
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, centerX - titleWidth / 2, startY - 30, 0xFFFFFFFF, false);

        int labelWidth = Math.max(
                    renderer.getWidth("Block X:"),
                    Math.max(
                        renderer.getWidth("Block Y:"),
                        renderer.getWidth("Block Z:")
            )
        ) + 5;
        int totalWidth = labelWidth + 10 + fieldWidth;
        int labelX = centerX - totalWidth / 2;
        int labelY = startY + 5;

        int chunkX = 0;
        int chunkZ = 0;
        try {
            String blockXText = blockXField.getText().trim();
            String blockZText = blockZField.getText().trim();
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

        Text chunkInfo = Text.literal("Chunk: " + chunkX + ", " + chunkZ).formatted(Formatting.GRAY);
        int chunkInfoWidth = renderer.getWidth(chunkInfo);
        context.drawText(renderer, chunkInfo, centerX - chunkInfoWidth / 2, startY - 15, 0xFFCCCCCC, false);

        context.drawText(renderer, Text.literal("Block X:"), labelX, labelY, 0xFFCCCCCC, false);
        context.drawText(renderer, Text.literal("Block Y:"), labelX, labelY + spacing, 0xFFCCCCCC, false);
        context.drawText(renderer, Text.literal("Block Z:"), labelX, labelY + spacing * 2, 0xFFCCCCCC, false);

        blockXField.render(context, mouseX, mouseY, delta);
        blockYField.render(context, mouseX, mouseY, delta);
        blockZField.render(context, mouseX, mouseY, delta);

        if (warningMessage != null) {
            Formatting color = warningMessage.startsWith("Updating") ? Formatting.YELLOW : Formatting.RED;
            Text warning = Text.literal(warningMessage).formatted(color);
            int warningWidth = renderer.getWidth(warning);
            context.drawText(renderer, warning, centerX - warningWidth / 2, startY + spacing * 3 + 5, 0xFFFFFFFF, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    public Screen getParent() {
        return parent;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
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
    public boolean shouldPause() {
        return false;
    }
}

