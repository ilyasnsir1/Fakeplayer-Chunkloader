package de.chunkloader.client.screen;

import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.payload.UpdateDisabledChunkloaderCoordsResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.ChatComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
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
    private Button cancelButton;
    private Button chatButton;
    private int contentTop;
    private int fieldY;
    private int buttonY;
    private boolean chatOpen;
    private int chatScroll;
    private int chatLineHeight;
    private boolean selectingChat;
    private int anchorIndex = -1;
    private int anchorChar;
    private int focusIndex = -1;
    private int focusChar;
    private final List<ChatRow> chatRows = new ArrayList<>();
    private boolean applyingPaste;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private String draftX;
    private String draftY;
    private String draftZ;
    private String selectedDimension;
    private static final String[] DIMENSION_IDS = {
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end"
    };
    private static final String[] DIMENSION_LABELS = {"Overworld", "Nether", "End"};

    private String warningMessage = null;
    private boolean isClosed = false;
    private UpdateDisabledChunkloaderCoordsResponsePayload pendingResponse = null;
    private long updateStartTime = 0;
    private static final long UPDATE_DISPLAY_DURATION_MS = 1500;

    public EditDisabledChunkloaderCoordsScreen(Screen parent, DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry) {
        super(Component.literal("Edit Coordinates"));
        this.parent = parent;
        this.entry = entry;
        this.selectedDimension = normalizeDimension(entry.dimension());
    }

    @Override
    protected void init() {
        super.init();

        layout();
        int fieldWidth = 72;
        int fieldHeight = 20;
        int gap = 18;
        int rowWidth = fieldWidth * 3 + gap * 2;
        int rowX = (this.width - rowWidth) / 2;
        Font renderer = this.font;

        blockXField = new EditBox(renderer, rowX, fieldY, fieldWidth, fieldHeight, Component.literal("X"));
        blockXField.setCanLoseFocus(true);
        blockXField.setMaxLength(11);
        blockXField.setValue(draftX != null ? draftX : Integer.toString(entry.blockX()));
        blockXField.setHint(Component.literal("X coordinate").withStyle(ChatFormatting.GRAY));
        blockXField.setResponder(text -> {
            if (fillFieldsFromText(text)) {
                return;
            }
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockXField.setValue(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonState();
        });
        this.addWidget(blockXField);

        blockYField = new EditBox(renderer, rowX + fieldWidth + gap, fieldY, fieldWidth, fieldHeight, Component.literal("Y"));
        blockYField.setCanLoseFocus(true);
        blockYField.setMaxLength(11);
        blockYField.setValue(draftY != null ? draftY : Integer.toString(entry.blockY()));
        blockYField.setHint(Component.literal("Y coordinate").withStyle(ChatFormatting.GRAY));
        blockYField.setResponder(text -> {
            if (fillFieldsFromText(text)) {
                return;
            }
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockYField.setValue(filtered);
                return;
            }
            updateSaveButtonState();
        });
        this.addWidget(blockYField);

        blockZField = new EditBox(renderer, rowX + (fieldWidth + gap) * 2, fieldY, fieldWidth, fieldHeight, Component.literal("Z"));
        blockZField.setCanLoseFocus(true);
        blockZField.setMaxLength(11);
        blockZField.setValue(draftZ != null ? draftZ : Integer.toString(entry.blockZ()));
        blockZField.setHint(Component.literal("Z coordinate").withStyle(ChatFormatting.GRAY));
        blockZField.setResponder(text -> {
            if (fillFieldsFromText(text)) {
                return;
            }
            if (!text.matches("^-?\\d*$")) {
                String filtered = text.replaceAll("[^-\\d]", "");
                blockZField.setValue(filtered);
                return;
            }
            updateChunkDisplay();
            updateSaveButtonState();
        });
        this.addWidget(blockZField);

        int buttonWidth = 72;
        int buttonGap = 8;
        int buttonsWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonX = (this.width - buttonsWidth) / 2;

        chatButton = Button.builder(
                Component.literal("Chat"),
                btn -> {
                    chatOpen = !chatOpen;
                    btn.setMessage(Component.literal(chatOpen ? "Hide" : "Chat"));
                    btn.setFocused(false);
                    this.setFocused(null);
                })
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build();
        this.addRenderableWidget(chatButton);

        saveButton = Button.builder(
                Component.literal("Save"),
                btn -> save())
            .bounds(buttonX + buttonWidth + buttonGap, buttonY, buttonWidth, 20)
            .build();
        saveButton.active = false;
        this.addRenderableWidget(saveButton);

        cancelButton = Button.builder(
                Component.literal("Back"),
                btn -> {
                    isClosed = true;
                    this.minecraft.gui.setScreen(parent);
                })
            .bounds(buttonX + (buttonWidth + buttonGap) * 2, buttonY, buttonWidth, 20)
            .build();
        this.addRenderableWidget(cancelButton);

    }

    private void layout() {
        int messageExtra = warningMessage != null ? 16 : 0;
        int blockHeight = 142 + messageExtra;
        contentTop = Math.max(8, (this.height - blockHeight) / 2);
        fieldY = contentTop + 64;
        buttonY = fieldY + 58 + messageExtra;
        positionFields();
        positionButtons();
    }

    private void positionFields() {
        if (blockXField == null || blockYField == null || blockZField == null) {
            return;
        }
        int fieldWidth = 72;
        int gap = 18;
        int rowX = (this.width - (fieldWidth * 3 + gap * 2)) / 2;
        blockXField.setPosition(rowX, fieldY);
        blockYField.setPosition(rowX + fieldWidth + gap, fieldY);
        blockZField.setPosition(rowX + (fieldWidth + gap) * 2, fieldY);
    }

    private void positionButtons() {
        if (chatButton == null || saveButton == null || cancelButton == null) {
            return;
        }
        int buttonWidth = 72;
        int buttonGap = 8;
        int buttonX = (this.width - (buttonWidth * 3 + buttonGap * 2)) / 2;
        chatButton.setPosition(buttonX, buttonY);
        saveButton.setPosition(buttonX + buttonWidth + buttonGap, buttonY);
        cancelButton.setPosition(buttonX + (buttonWidth + buttonGap) * 2, buttonY);
    }

    private boolean fillFieldsFromText(String text) {
        if (applyingPaste || blockXField == null || blockYField == null || blockZField == null) {
            return false;
        }
        Matcher matcher = NUMBER.matcher(text);
        List<String> numbers = new ArrayList<>();
        while (matcher.find() && numbers.size() < 3) {
            numbers.add(matcher.group());
        }
        if (numbers.size() < 2) {
            return false;
        }
        applyingPaste = true;
        blockXField.setValue(numbers.get(0));
        blockYField.setValue(numbers.get(1));
        if (numbers.size() > 2) {
            blockZField.setValue(numbers.get(2));
        }
        applyingPaste = false;
        updateChunkDisplay();
        updateSaveButtonState();
        return true;
    }

    private void clearFieldFocus() {
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

    private boolean insideField(EditBox field, double mouseX, double mouseY) {
        return field != null
            && mouseX >= field.getX()
            && mouseX < field.getX() + field.getWidth()
            && mouseY >= field.getY()
            && mouseY < field.getY() + field.getHeight();
    }

    private boolean isOverField(double mouseX, double mouseY) {
        return insideField(blockXField, mouseX, mouseY)
            || insideField(blockYField, mouseX, mouseY)
            || insideField(blockZField, mouseX, mouseY);
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        return (chatButton != null && chatButton.isMouseOver(mouseX, mouseY))
            || (saveButton != null && saveButton.isMouseOver(mouseX, mouseY))
            || (cancelButton != null && cancelButton.isMouseOver(mouseX, mouseY));
    }

    private void drawAxisLabel(GuiGraphicsExtractor context, Font renderer, String label, EditBox field) {
        int x = field.getX() + (field.getWidth() - renderer.width(label)) / 2;
        context.text(renderer, Component.literal(label), x, field.getY() - 12, 0xFFCCCCCC, false);
    }

    private int playerNameColor() {
        if (entry.easterEggSkinIndex() >= 0) {
            return 0xFFFFAA00;
        }
        if (entry.allowMobSpawning()) {
            return entry.hasWarning() ? 0xFFFFFF55 : 0xFF55FF55;
        }
        return 0xFF5555FF;
    }

    private int dimensionColor(String dimension) {
        String dim = dimension.toLowerCase();
        if (dim.contains("overworld")) {
            return 0xFF55FF55;
        }
        if (dim.contains("nether")) {
            return 0xFFFF5555;
        }
        if (dim.contains("end")) {
            return 0xFFFF55FF;
        }
        return 0xFFCCCCCC;
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
                newBlockX, newBlockY, newBlockZ,
                selectedDimension
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
            if (parent instanceof ActiveChunkloadersScreen) {
                ChunkloaderNetworking.requestActiveChunkloadersList();
            } else {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            }
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
        if (chatButton != null && chatButton.isFocused()) {
            chatButton.setFocused(false);
            if (this.getFocused() == chatButton) {
                this.setFocused(null);
            }
        }
        layout();

        Font renderer = this.font;
        int centerX = this.width / 2;
        int y = contentTop;
        String name = entry.name() != null ? entry.name() : "Unnamed";
        Component nameText = Component.literal(name).withStyle(ChatFormatting.BOLD);
        context.text(renderer, nameText, centerX - renderer.width(nameText) / 2, y, playerNameColor(), false);
        context.fill(centerX - 120, y + 16, centerX + 120, y + 17, 0x33FFFFFF);

        drawAxisLabel(context, renderer, "X", blockXField);
        drawAxisLabel(context, renderer, "Y", blockYField);
        drawAxisLabel(context, renderer, "Z", blockZField);

        blockXField.extractRenderState(context, mouseX, mouseY, delta);
        blockYField.extractRenderState(context, mouseX, mouseY, delta);
        blockZField.extractRenderState(context, mouseX, mouseY, delta);

        int chunkX = entry.blockX() >> 4;
        int chunkZ = entry.blockZ() >> 4;
        try {
            String blockXText = blockXField.getValue().trim();
            String blockZText = blockZField.getValue().trim();
            if (!blockXText.isEmpty() && !blockZText.isEmpty()) {
                chunkX = Integer.parseInt(blockXText) >> 4;
                chunkZ = Integer.parseInt(blockZText) >> 4;
            }
        } catch (NumberFormatException ignored) {
        }

        String chunkLabel = "Chunk " + chunkX + ", " + chunkZ;
        context.text(renderer, Component.literal(chunkLabel), centerX - renderer.width(chunkLabel) / 2, fieldY + 26, 0xFFCCCCCC, false);
        drawDimensionSwitcher(context, renderer, fieldY + 42, mouseX, mouseY);

        String note = "Block coordinates, not your position.";
        context.text(renderer, Component.literal(note), centerX - renderer.width(note) / 2, fieldY - 36, 0xFFFFFF55, false);

        if (warningMessage != null) {
            int warningColor = warningMessage.startsWith("Updating") ? 0xFFFFFF55 : 0xFFFF5555;
            context.text(renderer, Component.literal(warningMessage), centerX - renderer.width(warningMessage) / 2, fieldY + 56, warningColor, false);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        if (chatOpen) {
            drawChatPanel(context, renderer);
        }
    }

    public Screen getParent() {
        return parent;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int dimensionStep = dimensionArrowAt(mouseX, mouseY);
        if (dimensionStep != 0) {
            int index = selectedDimensionIndex();
            int count = DIMENSION_IDS.length;
            selectedDimension = DIMENSION_IDS[(index + dimensionStep + count) % count];
            clearFieldFocus();
            return true;
        }
        if (chatOpen && startChatSelection(mouseX, mouseY)) {
            clearFieldFocus();
            return true;
        }
        boolean outside = !isOverField(mouseX, mouseY) && !isOverButton(mouseX, mouseY);
        if (outside) {
            clearFieldFocus();
        }
        boolean handled = super.mouseClicked(click, doubleClick);
        if (this.getFocused() == chatButton) {
            this.setFocused(null);
        }
        if (chatButton != null) {
            chatButton.setFocused(false);
        }
        if (outside) {
            clearFieldFocus();
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (selectingChat) {
            moveChatSelection(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (selectingChat) {
            moveChatSelection(click.x(), click.y());
            copyChatSelection();
            selectingChat = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = (blockXField != null && blockXField.isFocused())
            || (blockYField != null && blockYField.isFocused())
            || (blockZField != null && blockZField.isFocused());
        if (!typing && this.minecraft != null && this.minecraft.options.keyChat.matches(event)) {
            chatOpen = !chatOpen;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (chatOpen && mouseY >= chatTop()) {
            chatScroll = Math.max(0, chatScroll + (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int chatTop() {
        return buttonY + 32;
    }

    private void drawChatPanel(GuiGraphicsExtractor context, Font renderer) {
        int top = chatTop();
        int bottom = this.height - 10;
        int left = 28;
        int right = this.width - 28;
        if (bottom - top < 24) {
            return;
        }
        context.fill(left, top, right, top + 1, 0x33FFFFFF);

        int textRight = right - 10;
        rebuildChatRows(renderer, textRight - left);
        int lineHeight = renderer.lineHeight + 3;
        chatLineHeight = lineHeight;
        int textTop = top + 8;
        int visible = Math.max(1, (bottom - textTop) / lineHeight);
        int maxScroll = Math.max(0, chatRows.size() - visible);
        if (chatScroll > maxScroll) {
            chatScroll = maxScroll;
        }
        if (chatRows.isEmpty()) {
            String empty = "No messages";
            context.text(renderer, Component.literal(empty), (this.width - renderer.width(empty)) / 2, textTop, 0xFFCCCCCC, false);
            return;
        }
        int shown = Math.min(visible, chatRows.size() - chatScroll);
        int first = Math.max(0, chatRows.size() - shown - chatScroll);
        int textY = textTop;
        for (ChatRow row : chatRows) {
            row.x = -1;
            row.y = -1;
        }
        context.enableScissor(left, textTop - 1, textRight, bottom);
        for (int i = first; i < first + shown; i++) {
            ChatRow row = chatRows.get(i);
            row.x = left;
            row.y = textY;
            drawChatSelection(context, renderer, i, row, lineHeight);
            context.text(renderer, row.visual, left, textY, 0xFFFFFFFF, false);
            textY += lineHeight;
        }
        context.disableScissor();

        if (maxScroll > 0) {
            int trackX = right - 2;
            int trackHeight = bottom - textTop;
            int thumbHeight = Math.max(12, (int) (visible / (float) chatRows.size() * trackHeight));
            int travel = trackHeight - thumbHeight;
            int thumbY = textTop + travel - (int) (chatScroll / (float) maxScroll * travel);
            context.fill(trackX, textTop, trackX + 2, textTop + trackHeight, 0x33000000);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    private void rebuildChatRows(Font renderer, int maxWidth) {
        chatRows.clear();
        List<GuiMessage> messages = chatMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Component content = messages.get(i).content();
            for (FormattedCharSequence line : renderer.split(content, maxWidth)) {
                chatRows.add(new ChatRow(sequenceText(line), line));
            }
        }
    }

    private static String sequenceText(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    private boolean startChatSelection(double mouseX, double mouseY) {
        int[] hit = chatHit(mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        selectingChat = true;
        anchorIndex = focusIndex = hit[0];
        anchorChar = focusChar = hit[1];
        return true;
    }

    private void moveChatSelection(double mouseX, double mouseY) {
        int[] hit = chatHit(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        focusIndex = hit[0];
        focusChar = hit[1];
    }

    private void copyChatSelection() {
        String text = selectedChatText();
        if (text.isEmpty() && anchorIndex >= 0 && anchorIndex < chatRows.size()) {
            text = chatRows.get(anchorIndex).text;
        }
        if (!text.isEmpty() && this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(text);
        }
    }

    private String selectedChatText() {
        if (anchorIndex < 0 || focusIndex < 0 || chatRows.isEmpty()) {
            return "";
        }
        int startLine = anchorIndex;
        int startChar = anchorChar;
        int endLine = focusIndex;
        int endChar = focusChar;
        if (startLine > endLine || (startLine == endLine && startChar > endChar)) {
            int line = startLine;
            int ch = startChar;
            startLine = endLine;
            startChar = endChar;
            endLine = line;
            endChar = ch;
        }
        if (startLine == endLine && startChar == endChar) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startLine; i <= endLine && i < chatRows.size(); i++) {
            String text = chatRows.get(i).text;
            int start = i == startLine ? Math.min(startChar, text.length()) : 0;
            int end = i == endLine ? Math.min(endChar, text.length()) : text.length();
            if (end > start) {
                builder.append(text, start, end);
            }
            if (i < endLine) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private int[] chatHit(double mouseX, double mouseY) {
        for (int i = 0; i < chatRows.size(); i++) {
            ChatRow row = chatRows.get(i);
            if (row.y < 0) {
                continue;
            }
            if (mouseY < row.y || mouseY >= row.y + chatLineHeight) {
                continue;
            }
            return new int[] { i, charIndex(row.text, mouseX - row.x) };
        }
        return null;
    }

    private int charIndex(String text, double relativeX) {
        if (relativeX <= 0) {
            return 0;
        }
        int index = 0;
        while (index < text.length()) {
            int next = text.offsetByCodePoints(index, 1);
            if (this.font.width(text.substring(0, next)) > relativeX) {
                return index;
            }
            index = next;
        }
        return text.length();
    }

    private void drawChatSelection(GuiGraphicsExtractor context, Font renderer, int index, ChatRow row, int lineHeight) {
        if (anchorIndex < 0 || focusIndex < 0) {
            return;
        }
        int startLine = Math.min(anchorIndex, focusIndex);
        int endLine = Math.max(anchorIndex, focusIndex);
        if (index < startLine || index > endLine) {
            return;
        }
        int startChar = index == anchorIndex && index == focusIndex
            ? Math.min(anchorChar, focusChar)
            : index == startLine
                ? (anchorIndex < focusIndex ? anchorChar : focusChar)
                : 0;
        int endChar = index == anchorIndex && index == focusIndex
            ? Math.max(anchorChar, focusChar)
            : index == endLine
                ? (anchorIndex < focusIndex ? focusChar : anchorChar)
                : row.text.length();
        startChar = Math.max(0, Math.min(startChar, row.text.length()));
        endChar = Math.max(startChar, Math.min(endChar, row.text.length()));
        if (endChar == startChar) {
            return;
        }
        int x0 = row.x + renderer.width(row.text.substring(0, startChar));
        int x1 = row.x + renderer.width(row.text.substring(0, endChar));
        context.fill(x0, row.y - 1, x1, row.y + renderer.lineHeight, 0x664466AA);
    }

    private static final class ChatRow {
        private final String text;
        private final FormattedCharSequence visual;
        private int x = -1;
        private int y = -1;

        private ChatRow(String text, FormattedCharSequence visual) {
            this.text = text;
            this.visual = visual;
        }
    }

    private String normalizeDimension(String dimension) {
        if (dimension == null) {
            return DIMENSION_IDS[0];
        }
        String value = dimension.toLowerCase();
        if (value.contains("nether")) {
            return DIMENSION_IDS[1];
        }
        if (value.contains("end")) {
            return DIMENSION_IDS[2];
        }
        return DIMENSION_IDS[0];
    }

    private int selectedDimensionIndex() {
        for (int i = 0; i < DIMENSION_IDS.length; i++) {
            if (DIMENSION_IDS[i].equals(selectedDimension)) {
                return i;
            }
        }
        return 0;
    }

    private int[] dimensionArrowXs(Font renderer) {
        String label = DIMENSION_LABELS[selectedDimensionIndex()];
        int gap = 8;
        int total = renderer.width("<") + gap + renderer.width(label) + gap + renderer.width(">");
        int leftX = this.width / 2 - total / 2;
        int labelX = leftX + renderer.width("<") + gap;
        int rightX = labelX + renderer.width(label) + gap;
        return new int[] { leftX, labelX, rightX };
    }

    private void drawDimensionSwitcher(GuiGraphicsExtractor context, Font renderer, int y, int mouseX, int mouseY) {
        int[] xs = dimensionArrowXs(renderer);
        String label = DIMENSION_LABELS[selectedDimensionIndex()];
        int leftColor = dimensionArrowAt(mouseX, mouseY) < 0 ? 0xFFFFFFFF : 0xFFCCCCCC;
        int rightColor = dimensionArrowAt(mouseX, mouseY) > 0 ? 0xFFFFFFFF : 0xFFCCCCCC;
        context.text(renderer, Component.literal("<"), xs[0], y, leftColor, false);
        context.text(renderer, Component.literal(label), xs[1], y, dimensionColor(label), false);
        context.text(renderer, Component.literal(">"), xs[2], y, rightColor, false);
    }

    private int dimensionArrowAt(double mouseX, double mouseY) {
        int y = fieldY + 42;
        if (mouseY < y - 2 || mouseY >= y + this.font.lineHeight + 2) {
            return 0;
        }
        int[] xs = dimensionArrowXs(this.font);
        int leftW = this.font.width("<");
        int rightW = this.font.width(">");
        if (mouseX >= xs[0] - 4 && mouseX < xs[0] + leftW + 4) {
            return -1;
        }
        if (mouseX >= xs[2] - 4 && mouseX < xs[2] + rightW + 4) {
            return 1;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<GuiMessage> chatMessages() {
        if (this.minecraft == null) {
            return Collections.emptyList();
        }
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            return (List<GuiMessage>) field.get(this.minecraft.gui.hud.getChat());
        } catch (ReflectiveOperationException e) {
            return Collections.emptyList();
        }
    }

    public void onClose() {
        isClosed = true;
        this.minecraft.gui.setScreen(parent);
    }

}

