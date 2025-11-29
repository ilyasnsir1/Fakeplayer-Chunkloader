package de.chunkloader.client.screen;

import com.google.common.collect.ImmutableList;
import de.chunkloader.network.ChunkMapCell;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.ChunkloaderActionPayload;
import de.chunkloader.network.ChunkloaderNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ChunkMapScreen extends Screen {

    private static final int CELL_SIZE = 18;
    private static final Identifier GRID_OVERLAY = Identifier.of("chunkloader", "textures/gui/grid_overlay.png");
    private static final Identifier HOVER_OVERLAY = Identifier.of("chunkloader", "textures/gui/cell_overlay.png");
    private static final Identifier FALLBACK_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private ChunkMapData data;
    private ChunkMapGrid grid;
    private int gridWidth;
    private int gridHeight;
    private int gridLeft;
    private int gridTop;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelWidth;
    private int leftPanelHeight;
    private int topBoxX;
    private int topBoxY;
    private int topBoxWidth;
    private int topBoxHeight;
    private final List<ButtonWidget> topBoxButtons = new ArrayList<>();
    private final List<ButtonWidget> actionButtons = new ArrayList<>();
    private ButtonWidget resetButton;
    private ButtonWidget infoButton;
    private ButtonWidget closeButton;

    public ChunkMapScreen(ChunkMapData data) {
        super(Text.literal("Chunk Loader Map"));
        this.data = data;
    }
    
    public void updateData(ChunkMapData newData) {
        this.data = newData;
        if (this.grid != null) {
            ClientWorld world = MinecraftClient.getInstance().world;
            this.grid.close();
            this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE);
        }
        buildActionButtons();
        updateButtonPositions();
    }

    @Override
    protected void init() {
        super.init();
        this.gridWidth = data.mapWidth() * CELL_SIZE + 2;
        this.gridHeight = data.mapHeight() * CELL_SIZE + 2;
        
        this.panelWidth = Math.min(160, 160);
        if (this.panelWidth < 120) {
            this.panelWidth = 120;
        }
        
        this.leftPanelWidth = 100;
        
        int totalWidth = leftPanelWidth + 12 + gridWidth + 12 + panelWidth;
        int startX = (this.width - totalWidth) / 2;
        
        if (startX < 16) {
            startX = 16;
        }
        
        this.leftPanelX = startX;
        this.leftPanelY = (this.height - this.gridHeight) / 2;
        if (this.leftPanelY < 32) {
            this.leftPanelY = 32;
        }
        this.leftPanelHeight = this.gridHeight;
        
        this.gridLeft = this.leftPanelX + this.leftPanelWidth + 12;
        this.gridTop = this.leftPanelY;
        
        this.panelX = this.gridLeft + this.gridWidth + 12;
        this.panelY = this.gridTop;
        this.panelHeight = this.gridHeight;
        
        int buttonWidth = 50;
        int buttonSpacing = 12;
        int padding = 16;
        int numButtons = 4;
        this.topBoxWidth = numButtons * buttonWidth + (numButtons - 1) * buttonSpacing + padding * 2 + 80;
        this.topBoxHeight = 28;
        this.topBoxX = (this.width - this.topBoxWidth) / 2;
        this.topBoxY = 35;
        
        ClientWorld world = MinecraftClient.getInstance().world;
        this.grid = new ChunkMapGrid(world, data, gridLeft, gridTop, CELL_SIZE);
        buildTopBoxButtons();
        buildActionButtons();
        
        int closeButtonWidth = 100;
        int closeButtonHeight = 20;
        int closeButtonX = this.gridLeft + (this.gridWidth - closeButtonWidth) / 2;
        int closeButtonY = this.gridTop + this.gridHeight + 10;
        this.closeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                btn -> this.close())
            .dimensions(closeButtonX, closeButtonY, closeButtonWidth, closeButtonHeight)
            .build()
        );
    }

    @Override
    public void removed() {
        if (this.grid != null) {
            this.grid.close();
        }
        super.removed();
    }

    @Override
    public void close() {
        if (this.grid != null) {
            this.grid.close();
        }
        super.close();
    }

    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        drawMapFrame(context);

        if (this.grid != null) {
            context.enableScissor(gridLeft - 2, gridTop - 2, gridLeft + gridWidth + 2, gridTop + gridHeight + 2);
            grid.render(context, mouseX, mouseY);
            drawChunkloaderPoints(context, mouseX, mouseY);
            context.disableScissor();
        }
        
        drawTopBox(context);
        drawLeftPanel(context);
        drawSidePanel(context);
        
        int padding = 8;
        int innerTop = panelY + padding;
        int innerBottom = panelY + panelHeight - padding;
        int buttonAreaTop = innerTop;
        int buttonAreaBottom = innerBottom;
        int maxButtonHeight = 6 * 26;
        if (buttonAreaBottom - buttonAreaTop < maxButtonHeight) {
            buttonAreaTop = Math.max(panelY + 2, buttonAreaBottom - maxButtonHeight);
        }
        
        int hoverPadding = 2;
        context.enableScissor(panelX, buttonAreaTop - hoverPadding, panelX + panelWidth, buttonAreaBottom + hoverPadding);
        for (ButtonWidget button : actionButtons) {
            if (button != resetButton) {
                button.render(context, mouseX, mouseY, delta);
            }
        }
        context.disableScissor();
        
        for (ButtonWidget button : topBoxButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        
        if (resetButton != null) {
            resetButton.render(context, mouseX, mouseY, delta);
        }
        
        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        ChunkMapGrid.Cell hovered = grid != null ? grid.getHoveredCell(mouseX, mouseY) : null;
        if (hovered != null) {
            context.drawTooltip(textRenderer, hovered.buildTooltip(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private void drawMapFrame(DrawContext context) {
        int framePadding = 6;
        int accentThickness = 2;
        int borderThickness = 4;
        
        int innerLeft = gridLeft;
        int innerTop = gridTop;
        int innerRight = gridLeft + gridWidth;
        int innerBottom = gridTop + gridHeight;
        
        int frameLeft = innerLeft - framePadding;
        int frameTop = innerTop - framePadding;
        int frameRight = innerRight + framePadding;
        int frameBottom = innerBottom + framePadding;
        
        context.fill(frameLeft, frameTop, frameRight, frameBottom, 0xFF111417);
        context.fill(innerLeft - borderThickness, innerTop - borderThickness, innerRight + borderThickness, innerBottom + borderThickness, 0xFF2B2F36);
        context.fill(frameLeft, frameBottom - accentThickness, frameRight, frameBottom, 0xFF0C0D10);
        context.fill(frameRight - accentThickness, frameTop, frameRight, frameBottom, 0xFF0C0D10);
    }
    

    private void updateButtonPositions() {
        int padding = 8;
        int innerTop = panelY + padding;
        int innerBottom = panelY + panelHeight - padding;
        int buttonSpacing = 26;
        int totalButtons = 6;
        int totalButtonHeight = totalButtons * buttonSpacing;
        int baseButtonY = innerBottom - totalButtonHeight;
        if (baseButtonY < innerTop) {
            baseButtonY = innerTop;
        }
        
        int scrollableIndex = 0;
        for (int i = 0; i < actionButtons.size(); i++) {
            ButtonWidget button = actionButtons.get(i);
            if (button != resetButton) {
                int logicalIndex;
                if (scrollableIndex < 2) {
                    logicalIndex = scrollableIndex;
                } else if (scrollableIndex == 2 || scrollableIndex == 3) {
                    logicalIndex = 2;
                } else {
                    logicalIndex = scrollableIndex - 1;
                }
                
                int buttonY = baseButtonY + (logicalIndex * buttonSpacing);
                button.setY(buttonY);
                scrollableIndex++;
            }
        }
    }

    private void drawTopBox(DrawContext context) {
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, 0xFF2B2F36);
        
        int borderColor = 0xFF4A4A4A;
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY - 1, borderColor);
        context.fill(topBoxX - 2, topBoxY + topBoxHeight + 1, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX - 2, topBoxY - 2, topBoxX - 1, topBoxY + topBoxHeight + 2, borderColor);
        context.fill(topBoxX + topBoxWidth + 1, topBoxY - 2, topBoxX + topBoxWidth + 2, topBoxY + topBoxHeight + 2, borderColor);
        
        if (topBoxButtons.size() >= 4) {
            int buttonWidth = 50;
            int numButtons = 4;
            int totalButtonsWidth = numButtons * buttonWidth;
            int availableSpace = topBoxWidth - totalButtonsWidth;
            int spacing = availableSpace / (numButtons + 1);
            int startX = topBoxX + spacing;
            int lineY = topBoxY + 4;
            int lineHeight = topBoxHeight - 8;
            
            for (int i = 1; i < 4; i++) {
                int lineX = startX + buttonWidth * i + spacing * (i - 1) + spacing / 2;
                context.fill(lineX, lineY, lineX + 1, lineY + lineHeight, 0x33FFFFFF);
            }
        }
    }
    
    private void drawLeftPanel(DrawContext context) {
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, 0xFF2B2F36);
        
        int borderColor = 0xFF4A4A4A;
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY - 1, borderColor);
        context.fill(leftPanelX - 2, leftPanelY + leftPanelHeight + 1, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX - 1, leftPanelY + leftPanelHeight + 2, borderColor);
        context.fill(leftPanelX + leftPanelWidth + 1, leftPanelY - 2, leftPanelX + leftPanelWidth + 2, leftPanelY + leftPanelHeight + 2, borderColor);

        int padding = 6;
        int headSize = 24;
        int headY = leftPanelY + 8;
        int nameY = 0;
        
        String ownerName = data != null && data.ownerName() != null ? data.ownerName() : null;

        if (ownerName != null && !ownerName.isEmpty()) {
            int headX = leftPanelX + (leftPanelWidth - headSize) / 2;
            drawPlayerHead(context, headX, headY, headSize, ownerName);

            Text nameText = Text.literal(ownerName);
                int nameWidth = this.textRenderer.getWidth(nameText);
                int nameX = leftPanelX + (leftPanelWidth - nameWidth) / 2;
                nameY = headY + headSize + 4;
                context.drawText(this.textRenderer, nameText, nameX, nameY, 0xFFFFFFFF, false);
            } else {
            nameY = 0;
        }

        if (data == null) {
            return;
        }

        int infoY = nameY > 0 ? nameY + this.textRenderer.fontHeight + 8 : headY + headSize + 8;

        context.fill(leftPanelX + padding, infoY - 4, leftPanelX + leftPanelWidth - padding, infoY - 3, 0x33FFFFFF);
        infoY += 8;

        context.drawText(this.textRenderer, Text.literal("Status:"), leftPanelX + padding, infoY, 0xFFFFFFFF, false);
        String statusText = data.enabled() ? "active" : "inactive";
        int statusColor = data.enabled() ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) : 0xFF5555;
        int statusTextWidth = this.textRenderer.getWidth(statusText);
        context.drawText(this.textRenderer, Text.literal(statusText), 
            leftPanelX + leftPanelWidth - padding - statusTextWidth, infoY, statusColor | 0xFF000000, false);
        infoY += 12;

        context.drawText(this.textRenderer, Text.literal("Dim:").formatted(net.minecraft.util.Formatting.GRAY), 
            leftPanelX + padding, infoY, 0xFFFFFFFF, false);
        String dimName = data.dimensionKey().toLowerCase();
        String dimText;
        int dimColor;
        if (dimName.contains("overworld")) {
            dimText = "Overworld";
            dimColor = 0x55FF55;
        } else if (dimName.contains("nether")) {
            dimText = "Nether";
            dimColor = 0xFF5555;
        } else if (dimName.contains("end")) {
            dimText = "End";
            dimColor = 0xFF55FF;
        } else {
            dimText = "?";
            dimColor = 0xAAAAAA;
        }
        int dimTextWidth = this.textRenderer.getWidth(dimText);
        context.drawText(this.textRenderer, Text.literal(dimText), 
            leftPanelX + leftPanelWidth - padding - dimTextWidth, infoY, dimColor | 0xFF000000, false);
        infoY += 12;

        context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, 0x33FFFFFF);
        infoY += 8;

        String chunkPos = data.centerChunkX() + "," + data.centerChunkZ();
        context.drawText(this.textRenderer, Text.literal("Chunk:").formatted(net.minecraft.util.Formatting.GRAY), 
            leftPanelX + padding, infoY, 0xFFFFFFFF, false);
        int chunkPosWidth = this.textRenderer.getWidth(chunkPos);
        context.drawText(this.textRenderer, Text.literal(chunkPos), 
            leftPanelX + leftPanelWidth - padding - chunkPosWidth, infoY, 0xFFFFFFFF, false);
        infoY += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        String blockPosStr = blockPos.getX() + "," + data.blockY() + "," + blockPos.getZ();
        context.drawText(this.textRenderer, Text.literal("Block:").formatted(net.minecraft.util.Formatting.GRAY), 
            leftPanelX + padding, infoY, 0xFFFFFFFF, false);
        int blockPosWidth = this.textRenderer.getWidth(blockPosStr);
        context.drawText(this.textRenderer, Text.literal(blockPosStr), 
            leftPanelX + leftPanelWidth - padding - blockPosWidth, infoY, 0xFFFFFFFF, false);
        infoY += 12;

            context.fill(leftPanelX + padding, infoY, leftPanelX + leftPanelWidth - padding, infoY + 1, 0x33FFFFFF);
            infoY += 8;
        
            String radiusStr = data.chunkRadius() + "/3";
        String radiusLabel = data.allowMobSpawning() ? "SD:" : "Radius:";
        context.drawText(this.textRenderer, Text.literal(radiusLabel).formatted(net.minecraft.util.Formatting.GRAY),
                leftPanelX + padding, infoY, 0xFFFFFFFF, false);
            int radiusWidth = this.textRenderer.getWidth(radiusStr);
            context.drawText(this.textRenderer, Text.literal(radiusStr), 
                leftPanelX + leftPanelWidth - padding - radiusWidth, infoY, 0xFFFFFFFF, false);
            infoY += 12;
    }

    private void drawSidePanel(DrawContext context) {
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF2B2F36);
        
        int borderColor = 0xFF4A4A4A;
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY - 1, borderColor);
        context.fill(panelX - 2, panelY + panelHeight + 1, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);
        context.fill(panelX - 2, panelY - 2, panelX - 1, panelY + panelHeight + 2, borderColor);
        context.fill(panelX + panelWidth + 1, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, borderColor);

        TextRenderer renderer = this.textRenderer;
        int padding = 8;
        int y = panelY + padding + 4;
        
        MutableText title = Text.literal("Chunkloader: " + data.displayName());
        context.drawText(renderer, title.formatted(net.minecraft.util.Formatting.BOLD), panelX + padding, y, 0xFFFFFF, false);
        y += 16;
        
        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawText(renderer, Text.literal("Status:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Text status = data.enabled()
            ? Text.literal("active")
            : Text.literal("inactive");
        int statusColor = data.enabled() 
            ? (data.allowMobSpawning() ? 0x55FF55 : 0x5555FF) 
            : 0xFF5555;
        context.drawText(renderer, status, panelX + padding + 50, y, statusColor, false);
        y += 12;

        context.drawText(renderer, Text.literal("Mode:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        Text mode = data.allowMobSpawning()
            ? Text.literal("Fakeplayer (mob spawning)")
            : Text.literal("Chunkplayer");
        int modeColor = data.allowMobSpawning() ? 0x55FF55 : 0x79C0FF;
        context.drawText(renderer, mode, panelX + padding + 50, y, modeColor, false);
        y += 12;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        context.drawText(renderer, Text.literal("Dimension:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        context.drawText(renderer, Text.literal(data.dimensionKey()), panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        BlockPos blockPos = new BlockPos(data.centerChunkX() << 4, data.blockY(), data.centerChunkZ() << 4);
        context.drawText(renderer, Text.literal("Position:").formatted(net.minecraft.util.Formatting.GRAY), 
            panelX + padding, y, 0xFFFFFF, false);
        context.drawText(renderer, Text.literal(blockPos.getX() + " / " + data.blockY() + " / " + blockPos.getZ()), 
            panelX + padding + 50, y, 0xFFFFFF, false);
        y += 12;

        String radiusLabelSide = data.allowMobSpawning() ? "SD:" : "Radius:";
        context.drawText(renderer, Text.literal(radiusLabelSide).formatted(net.minecraft.util.Formatting.GRAY),
            panelX + padding, y, 0xFFFFFF, false);
        MutableText radiusText = Text.literal(data.chunkRadius() + " / 3");
        context.drawText(renderer, radiusText, panelX + padding + 50, y, 0xFFFFFF, false);
            y += 12;

        drawSeparator(context, panelX + padding, y, panelWidth - padding * 2);
        y += 8;

        if (!data.allowMobSpawning()) {
        int legendStartY = y;
        int buttonAreaStart = panelY + panelHeight - 80;
        int legendHeight = 12 + 14 + 14 + 14;
        
        if (legendStartY + legendHeight > buttonAreaStart - 10) {
            return;
        }
        
            context.drawText(renderer, Text.literal("Legend").formatted(net.minecraft.util.Formatting.BOLD), 
            panelX + padding, y, 0xFFFFFF, false);
        y += 12;
        
        int loadedColor = data.allowMobSpawning() 
            ? ChunkMapGrid.COLOR_LOADED 
            : ChunkMapGrid.COLOR_IN_RANGE;
        drawLegendItem(context, renderer, panelX + padding, y, loadedColor, 
                Text.literal("Loaded"));
        y += 14;
        
        if (data.allowMobSpawning()) {
            drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_LOADED, 
                    Text.literal("Simulation Distance"));
            y += 14;
        }
        
        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_IN_RANGE, 
                Text.literal("Within radius"));
        y += 14;
        
        drawLegendItem(context, renderer, panelX + padding, y, ChunkMapGrid.COLOR_OTHER, 
                Text.literal("Other chunkloader"));
        }
    }

    private Identifier getPlayerSkinTexture(String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || playerName == null || playerName.isEmpty()) {
            return FALLBACK_SKIN;
        }
        
        Identifier entryTexture = resolveSkinFromPlayerListEntry(client, playerName);
        if (entryTexture != null && !entryTexture.equals(FALLBACK_SKIN)) {
            return entryTexture;
        }
        
        return FALLBACK_SKIN;
    }

    private Identifier resolveSkinFromPlayerListEntry(MinecraftClient client, String playerName) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return null;
        }
        
        PlayerListEntry entry = null;
        for (var playerEntry : handler.getPlayerList()) {
            if (playerEntry.getProfile().name() != null && playerEntry.getProfile().name().equals(playerName)) {
                entry = playerEntry;
                break;
            }
        }
        
        if (entry == null) {
            return null;
        }
        
        try {
            var field45607 = entry.getClass().getDeclaredField("field_45607");
            field45607.setAccessible(true);
            Object supplier = field45607.get(entry);
            if (supplier instanceof java.util.function.Supplier) {
                Object result = ((java.util.function.Supplier<?>) supplier).get();
                if (result != null) {
                    try {
                        var comp1626 = result.getClass().getMethod("comp_1626");
                        Object textureResult = comp1626.invoke(result);
                        if (textureResult != null) {
                            var textureMethods = textureResult.getClass().getMethods();
                            for (var textureMethod : textureMethods) {
                                if (textureMethod.getParameterCount() == 0 && !textureMethod.getName().equals("getClass")) {
                                    try {
                                        Object idResult = textureMethod.invoke(textureResult);
                                        if (idResult instanceof Identifier id && id != null && !id.equals(FALLBACK_SKIN)) {
                return id;
            }
        } catch (Exception ignored) {
        }
                                }
                }
            }
        } catch (Exception ignored) {
        }
                }
            }
        } catch (Exception ignored) {
        }
        
        return null;
    }

    private void drawPlayerHead(DrawContext context, int x, int y, int size, String playerName) {
        Identifier texture = getPlayerSkinTexture(playerName);

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            8,
            8,
            size,
            size,
            8,
            8,
            64,
            64
        );

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            40,
            8,
            size,
            size,
            8,
            8,
            64,
            64
        );
    }
    
    private void drawSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0x4A4A4A);
    }

    private void drawLegendItem(DrawContext context, TextRenderer renderer, int x, int y, int color, Text text) {
        int squareSize = 6;
        int squareY = y + (renderer.fontHeight - squareSize) / 2;
        context.fill(x, squareY, x + squareSize, squareY + squareSize, 0xFF000000 | color);
        context.fill(x + 1, squareY + 1, x + squareSize - 1, squareY + squareSize - 1, color);
        context.drawText(renderer, text, x + 10, y, 0xFFFFFF, false);
    }
    
    private void drawChunkloaderPoints(DrawContext context, int mouseX, int mouseY) {
        int cellSize = CELL_SIZE;
        int gridInnerLeft = gridLeft + 1;
        int gridInnerTop = gridTop + 1;
        
        int ownOffsetX = data.fakeplayerChunkX() - data.centerChunkX();
        int ownOffsetZ = data.fakeplayerChunkZ() - data.centerChunkZ();
        int halfMap = (data.mapWidth() - 1) / 2;
        int ownColumn = ownOffsetX + halfMap;
        int ownRow = ownOffsetZ + halfMap;
        
        if (ownColumn >= 0 && ownColumn < data.mapWidth() && ownRow >= 0 && ownRow < data.mapHeight()) {
            int ownInnerChunkX = ((data.fakeplayerBlockX() % 16) + 16) % 16;
            int ownInnerChunkZ = ((data.fakeplayerBlockZ() % 16) + 16) % 16;
            
            int ownCellLeft = gridInnerLeft + ownColumn * cellSize;
            int ownCellTop = gridInnerTop + ownRow * cellSize;
            float ownPosInCellX = (ownInnerChunkX / 16.0f) * cellSize;
            float ownPosInCellZ = (ownInnerChunkZ / 16.0f) * cellSize;
            
            int ownPointX = ownCellLeft + (int)ownPosInCellX;
            int ownPointY = ownCellTop + (int)ownPosInCellZ;
            
            int ownPointColor = 0xFFFF0000;
            int ownPointSize = 3;
            int ownPointHalfSize = ownPointSize / 2;
            
            context.fill(
                ownPointX - ownPointHalfSize - 1, ownPointY - ownPointHalfSize - 1,
                ownPointX + ownPointHalfSize + 1, ownPointY + ownPointHalfSize + 1,
                0xFF000000
            );
            context.fill(
                ownPointX - ownPointHalfSize, ownPointY - ownPointHalfSize,
                ownPointX + ownPointHalfSize, ownPointY + ownPointHalfSize,
                ownPointColor
            );
            
            if (mouseX >= ownPointX - ownPointHalfSize - 2 && mouseX < ownPointX + ownPointHalfSize + 2 &&
                mouseY >= ownPointY - ownPointHalfSize - 2 && mouseY < ownPointY + ownPointHalfSize + 2) {
                String ownTooltipText = data.fakeplayerName() != null ? data.fakeplayerName() : 
                    String.format("%s at (%d, %d)", data.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer", 
                        data.fakeplayerChunkX(), data.fakeplayerChunkZ());
                context.drawTooltip(textRenderer, Text.literal(ownTooltipText), mouseX, mouseY);
            }
        }
        
        if (data.otherChunkloaders() == null || data.otherChunkloaders().isEmpty()) {
            return;
        }
        
        for (de.chunkloader.network.ChunkloaderPosition pos : data.otherChunkloaders()) {
            int offsetX = pos.chunkX() - data.centerChunkX();
            int offsetZ = pos.chunkZ() - data.centerChunkZ();
            
            int column = offsetX + halfMap;
            int row = offsetZ + halfMap;
            
            if (column < 0 || column >= data.mapWidth() || row < 0 || row >= data.mapHeight()) {
                continue;
            }
            
            int innerChunkX = ((pos.blockX() % 16) + 16) % 16;
            int innerChunkZ = ((pos.blockZ() % 16) + 16) % 16;
            
            int cellLeft = gridInnerLeft + column * cellSize;
            int cellTop = gridInnerTop + row * cellSize;
            float posInCellX = (innerChunkX / 16.0f) * cellSize;
            float posInCellZ = (innerChunkZ / 16.0f) * cellSize;
            
            int pointX = cellLeft + (int)posInCellX;
            int pointY = cellTop + (int)posInCellZ;
            
            int pointColor;
            if (pos.isFakeplayer()) {
                pointColor = 0xFF55FF55;
            } else {
                pointColor = 0xFF5996FF;
            }
            int pointSize = 3;
            int pointHalfSize = pointSize / 2;
            
            context.fill(
                pointX - pointHalfSize - 1, pointY - pointHalfSize - 1,
                pointX + pointHalfSize + 1, pointY + pointHalfSize + 1,
                0xFF000000
            );
            context.fill(
                pointX - pointHalfSize, pointY - pointHalfSize,
                pointX + pointHalfSize, pointY + pointHalfSize,
                pointColor
            );
            
            if (mouseX >= pointX - pointHalfSize - 2 && mouseX < pointX + pointHalfSize + 2 &&
                mouseY >= pointY - pointHalfSize - 2 && mouseY < pointY + pointHalfSize + 2) {
                String tooltipText = pos.name() != null ? pos.name() : 
                    String.format("%s at (%d, %d)", pos.isFakeplayer() ? "Fakeplayer" : "Chunkplayer", pos.chunkX(), pos.chunkZ());
                context.drawTooltip(textRenderer, Text.literal(tooltipText), mouseX, mouseY);
            }
        }
    }

    private static class ChunkMapGrid implements AutoCloseable {

        private static final int COLOR_BACKGROUND = 0xAA000000;
        private static final int COLOR_BORDER = 0xFF3A3A3A;
        static final int COLOR_LOADED = 0x55FF55;
        static final int COLOR_IN_RANGE = 0x5996FF;
        static final int COLOR_OTHER = 0xC8C8C8;

        private static final int BORDER = 1;

        private final List<Cell> cells = new ArrayList<>();
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private final int gridInnerLeft;
        private final int gridInnerTop;
        private final int gridInnerRight;
        private final int gridInnerBottom;

        ChunkMapGrid(ClientWorld world, ChunkMapData data, int left, int top, int cellSize) {
            this.left = left;
            this.top = top;
            this.width = data.mapWidth() * cellSize + 2 * BORDER;
            this.height = data.mapHeight() * cellSize + 2 * BORDER;
            int sampleY = data.blockY();

            this.gridInnerLeft = left + BORDER;
            this.gridInnerTop = top + BORDER;
            this.gridInnerRight = gridInnerLeft + data.mapWidth() * cellSize;
            this.gridInnerBottom = gridInnerTop + data.mapHeight() * cellSize;

            Map<Long, ChunkMapCell> stateByOffset = new HashMap<>();
            for (ChunkMapCell cell : data.cells()) {
                stateByOffset.put(pack(cell.offsetX(), cell.offsetZ()), cell);
            }

            for (int row = 0; row < data.mapHeight(); row++) {
                for (int column = 0; column < data.mapWidth(); column++) {
                    int chunkX = data.topLeftChunkX() + column;
                    int chunkZ = data.topLeftChunkZ() + row;
                    int offsetX = chunkX - data.centerChunkX();
                    int offsetZ = chunkZ - data.centerChunkZ();
                    ChunkMapCell state = stateByOffset.getOrDefault(
                        pack(offsetX, offsetZ),
                        new ChunkMapCell(
                            offsetX,
                            offsetZ,
                            Math.abs(offsetX) <= data.chunkRadius() && Math.abs(offsetZ) <= data.chunkRadius() && data.enabled(),
                            Math.abs(offsetX) <= data.chunkRadius() && Math.abs(offsetZ) <= data.chunkRadius(),
                            false,
                            false,
                            null
                        )
                    );
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    int cellLeft = gridInnerLeft + column * cellSize;
                    int cellTop = gridInnerTop + row * cellSize;
                    this.cells.add(new Cell(
                        world,
                        chunkPos,
                        state,
                        cellLeft,
                        cellTop,
                        cellSize,
                        sampleY,
                        data,
                        gridInnerLeft,
                        gridInnerTop,
                        gridInnerRight,
                        gridInnerBottom
                    ));
                }
            }
        }

        void render(DrawContext context, int mouseX, int mouseY) {
            context.fill(left, top, left + width, top + height, COLOR_BACKGROUND);

            context.fill(left, top, left + width, top + BORDER, COLOR_BORDER);
            context.fill(left, top + height - BORDER, left + width, top + height, COLOR_BORDER);
            context.fill(left, top, left + BORDER, top + height, COLOR_BORDER);
            context.fill(left + width - BORDER, top, left + width, top + height, COLOR_BORDER);

            for (Cell cell : cells) {
                cell.render(context, mouseX, mouseY);
            }
        }

        Cell getHoveredCell(int mouseX, int mouseY) {
            for (Cell cell : cells) {
                if (cell.isHovered(mouseX, mouseY)) {
                    return cell;
                }
            }
            return null;
        }

        @Override
        public void close() {
            for (Cell cell : cells) {
                cell.close();
            }
            cells.clear();
        }

        static long pack(int offsetX, int offsetZ) {
            return ((long)offsetX << 32) | (offsetZ & 0xFFFFFFFFL);
        }

        static class Cell implements AutoCloseable {

            private static final int COLOR_OCCUPIED = 0x88404040;
            private static final int COLOR_LOADED_OVERLAY = 0x6687FF59;
            private static final int COLOR_LOADED_OVERLAY_CHUNKPLAYER = 0x665555FF;
            private static final int COLOR_SIMULATION_DISTANCE = 0x6687FF59;
            private static final int COLOR_RANGE_OVERLAY = 0x4D3D7FFF;
            private static final int COLOR_RANGE_OVERLAY_DISABLED = 0x66FF5555;
            private final ChunkPos chunkPos;
            private final ChunkMapCell state;
            private final int left;
            private final int top;
            private final int size;
            private final int baseColor;
            private final ChunkTileImage tileImage;
            private final ChunkMapData data;
            private final int gridLeft;
            private final int gridTop;
            private final int gridRight;
            private final int gridBottom;

            Cell(ClientWorld world, ChunkPos chunkPos, ChunkMapCell state, int left, int top, int size, int sampleY, ChunkMapData data, int gridLeft, int gridTop, int gridRight, int gridBottom) {
                this.chunkPos = chunkPos;
                this.state = state;
                this.left = left;
                this.top = top;
                this.size = size;
                this.baseColor = ((chunkPos.x + chunkPos.z) & 1) == 0 ? 0xFF1F1F1F : 0xFF242424;
                this.tileImage = world != null ? new ChunkTileImage(world, chunkPos, sampleY) : null;
                this.data = data;
                this.gridLeft = gridLeft;
                this.gridTop = gridTop;
                this.gridRight = gridRight;
                this.gridBottom = gridBottom;
            }

            void render(DrawContext context, int mouseX, int mouseY) {
                int innerLeft = Math.max(gridLeft, left);
                int innerTop = Math.max(gridTop, top);
                int innerRight = Math.min(gridRight, left + size);
                int innerBottom = Math.min(gridBottom, top + size);

                if (innerRight <= innerLeft || innerBottom <= innerTop) {
                    return;
                }

                boolean isSimulatedOnly = state.simulatedByFakeplayer() && !state.loaded() && !state.occupiedByOther();
                
                if (!isSimulatedOnly) {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, 0x33000000);
                }

                Identifier textureId = tileImage != null ? tileImage.getTextureId() : null;
                int textureWidth = innerRight - innerLeft;
                int textureHeight = innerBottom - innerTop;

                if (textureId != null && textureWidth > 0 && textureHeight > 0) {
                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        innerLeft,
                        innerTop,
                        0.0f,
                        0.0f,
                        textureWidth,
                        textureHeight,
                        16,
                        16,
                        16,
                        16
                    );
                } else {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, baseColor);
                }

                int overlayWidth = innerRight - innerLeft;
                int overlayHeight = innerBottom - innerTop;
                if (overlayWidth > 0 && overlayHeight > 0) {
                    int srcX = innerLeft - left;
                    int srcY = innerTop - top;
                    context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        GRID_OVERLAY,
                        innerLeft,
                        innerTop,
                        srcX,
                        srcY,
                        overlayWidth,
                        overlayHeight,
                        18,
                        18
                    );
                }

                if (!data.allowMobSpawning()) {
                if (state.withinRange() && !state.loaded()) {
                        int overlayColor = data.enabled() ? COLOR_RANGE_OVERLAY : COLOR_RANGE_OVERLAY_DISABLED;
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, overlayColor);
                }
                if (state.loaded() && !state.occupiedByOther()) {
                        int loadedColor = data.allowMobSpawning() 
                            ? COLOR_LOADED_OVERLAY 
                            : COLOR_LOADED_OVERLAY_CHUNKPLAYER;
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, loadedColor);
                    }
                }
                
                if (state.simulatedByFakeplayer()) {
                    context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_SIMULATION_DISTANCE);
                }
                
                if (state.occupiedByOther()) {
                    if (data.allowMobSpawning()) {
                        if (!state.simulatedByFakeplayer()) {
                            context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                        }
                    } else {
                        context.fill(innerLeft, innerTop, innerRight, innerBottom, COLOR_OCCUPIED);
                    }
                }

                if (isHovered(mouseX, mouseY)) {
                    int hoverWidth = innerRight - innerLeft;
                    int hoverHeight = innerBottom - innerTop;
                    if (hoverWidth > 0 && hoverHeight > 0) {
                        int srcX = innerLeft - left;
                        int srcY = innerTop - top;
                        context.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            HOVER_OVERLAY,
                            innerLeft,
                            innerTop,
                            srcX,
                            srcY,
                            hoverWidth,
                            hoverHeight,
                            18,
                            18
                        );
                    }
                }
            }

            boolean isHovered(int mouseX, int mouseY) {
                int innerLeft = Math.max(gridLeft, left);
                int innerRight = Math.min(gridRight, left + size);
                int innerTop = Math.max(gridTop, top);
                int innerBottom = Math.min(gridBottom, top + size);

                return mouseX >= innerLeft && mouseX < innerRight &&
                       mouseY >= innerTop && mouseY < innerBottom;
            }

            List<Text> buildTooltip() {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(Text.literal("Chunk X:" + chunkPos.x + " Z:" + chunkPos.z));
                
                if (state.loaded()) {
                    tooltip.add(Text.literal("Loaded by this chunkloader"));
                } else if (state.withinRange()) {
                    if (!data.allowMobSpawning()) {
                        tooltip.add(Text.literal("Inside radius (enable to load)"));
                    } else {
                        tooltip.add(Text.literal("Inside simulation distance (enable to load)"));
                    }
                } else {
                    tooltip.add(Text.literal("Outside of this chunkloader"));
                }
                
                if (state.occupiedByOther() && state.loaded() && !state.simulatedByFakeplayer()) {
                    tooltip.add(Text.literal("Loaded by a different chunkloader"));
                }
                
                return ImmutableList.copyOf(tooltip);
            }

            @Override
            public void close() {
                if (tileImage != null) {
                    tileImage.close();
                }
            }
        }
    }

    private void buildTopBoxButtons() {
        topBoxButtons.forEach(this::remove);
        topBoxButtons.clear();
        
        int buttonWidth = 50;
        int buttonHeight = 20;
        int numButtons = 4;
        int totalButtonsWidth = numButtons * buttonWidth;
        int availableSpace = topBoxWidth - totalButtonsWidth;
        int spacing = availableSpace / (numButtons + 1);
        int startX = topBoxX + spacing;
        int startY = topBoxY + (topBoxHeight - buttonHeight) / 2;
        
        infoButton = ButtonWidget.builder(
            Text.literal("Info"),
            btn -> {
                MinecraftClient.getInstance().setScreen(new ChunkloaderMenuScreen(this));
            })
            .dimensions(startX, startY, buttonWidth, buttonHeight)
            .build();
        infoButton.setMessage(Text.literal("Info").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(infoButton);
        this.addDrawableChild(infoButton);
        
        int listButtonX = startX + buttonWidth + spacing;
        ButtonWidget listButton = ButtonWidget.builder(
            Text.literal("List"),
            btn -> {
                ChunkloaderNetworking.requestDisabledChunkloadersList();
            })
            .dimensions(listButtonX, startY, buttonWidth, buttonHeight)
            .build();
        listButton.setMessage(Text.literal("List").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(listButton);
        this.addDrawableChild(listButton);
        
        int helpButtonX = startX + (buttonWidth + spacing) * 2;
        ButtonWidget helpButton = ButtonWidget.builder(
            Text.literal("Help"),
            btn -> {
                MinecraftClient.getInstance().setScreen(new ChunkMapHelpScreen(this));
            })
            .dimensions(helpButtonX, startY, buttonWidth, buttonHeight)
            .build();
        helpButton.setMessage(Text.literal("Help").formatted(net.minecraft.util.Formatting.WHITE));
        topBoxButtons.add(helpButton);
        this.addDrawableChild(helpButton);
        
        int deleteButtonX = startX + (buttonWidth + spacing) * 3;
        ButtonWidget deleteButton = ButtonWidget.builder(
            Text.literal("Delete"),
            btn -> {
                this.client.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Text.literal("Delete Chunkloader?").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD),
                    Text.literal("This will permanently delete this chunkloader.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                            ChunkloaderActionPayload.Action.DELETE,
                            data.fakeplayerChunkX(),
                            data.fakeplayerChunkZ(),
                            0
                        );
                        this.client.setScreen(null);
                    },
                    null
                ));
            })
            .dimensions(deleteButtonX, startY, buttonWidth, buttonHeight)
            .build();
        deleteButton.setMessage(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED));
        topBoxButtons.add(deleteButton);
        this.addDrawableChild(deleteButton);
    }

    private void buildActionButtons() {
        actionButtons.forEach(this::remove);
        actionButtons.clear();
        if (panelWidth <= 0) {
            return;
        }
        int buttonWidth = panelWidth - 16;
        int buttonX = panelX + 8;
        int padding = 8;
        int innerTop = panelY + padding;
        int innerBottom = panelY + panelHeight - padding;
        int buttonSpacing = 26;
        
        int totalButtons = 6;
        int totalButtonHeight = totalButtons * buttonSpacing;
        int baseButtonY = innerBottom - totalButtonHeight;
        
        if (baseButtonY < innerTop) {
            baseButtonY = innerTop;
        }

        ButtonWidget enableButton = ButtonWidget.builder(
            data.enabled()
                ? (data.allowMobSpawning() ? Text.literal("Disable Fakeplayer") : Text.literal("Disable Chunkplayer"))
                : (data.allowMobSpawning() ? Text.literal("Enable Fakeplayer") : Text.literal("Enable Chunkplayer")),
            btn -> {
                ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_ENABLED,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
                );
                if (data.enabled()) {
                    MinecraftClient.getInstance().setScreen(null);
                }
            })
            .dimensions(buttonX, baseButtonY + buttonSpacing * 0, buttonWidth, 20)
            .build();
        if (data.enabled()) {
            enableButton.setMessage((data.allowMobSpawning() ? Text.literal("Disable Fakeplayer") : Text.literal("Disable Chunkplayer")).formatted(net.minecraft.util.Formatting.RED));
        } else {
            enableButton.setMessage((data.allowMobSpawning() ? Text.literal("Enable Fakeplayer") : Text.literal("Enable Chunkplayer")).formatted(net.minecraft.util.Formatting.GREEN));
        }
        actionButtons.add(enableButton);
        this.addDrawableChild(enableButton);

        ButtonWidget mobButton = ButtonWidget.builder(
            data.allowMobSpawning()
                ? Text.literal("Disable mob spawning")
                : Text.literal("Enable mob spawning"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_MOB_SPAWNING,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, baseButtonY + buttonSpacing * 1, buttonWidth, 20)
            .build();
        if (data.allowMobSpawning()) {
            mobButton.setMessage(Text.literal("Disable mob spawning").formatted(net.minecraft.util.Formatting.BLUE));
        } else {
            mobButton.setMessage(Text.literal("Enable mob spawning").formatted(net.minecraft.util.Formatting.GREEN));
        }
        actionButtons.add(mobButton);
        this.addDrawableChild(mobButton);

        int radiusY = baseButtonY + buttonSpacing * 2;
        int halfWidth = (buttonWidth - 4) / 2;
        boolean isFakePlayer = data.allowMobSpawning();
        boolean canDecrease = data.chunkRadius() > 0;
        boolean canIncrease = data.canIncreaseRadius();
        
        String radiusDownLabel = isFakePlayer ? "SD -1" : "Radius -1";
        ButtonWidget radiusDown = ButtonWidget.builder(
            Text.literal(radiusDownLabel),
            btn -> {
                if (canDecrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_DECREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                        1
                    );
                }
            })
            .dimensions(buttonX, radiusY, halfWidth, 20)
            .build();
        if (!canDecrease) {
            radiusDown.active = false;
            radiusDown.setMessage(Text.literal(radiusDownLabel).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        actionButtons.add(radiusDown);
        this.addDrawableChild(radiusDown);

        String radiusUpLabel = isFakePlayer ? "SD +1" : "Radius +1";
        ButtonWidget radiusUp = ButtonWidget.builder(
            Text.literal(radiusUpLabel),
            btn -> {
                if (canIncrease) {
                    ChunkloaderNetworking.sendAction(
                        ChunkloaderActionPayload.Action.RADIUS_INCREMENT,
                        data.fakeplayerChunkX(),
                        data.fakeplayerChunkZ(),
                        1
                    );
                }
            })
            .dimensions(buttonX + halfWidth + 4, radiusY, halfWidth, 20)
            .build();
        if (!canIncrease) {
            radiusUp.active = false;
            radiusUp.setMessage(Text.literal(radiusUpLabel).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        actionButtons.add(radiusUp);
        this.addDrawableChild(radiusUp);

        int nameVisibleY = baseButtonY + buttonSpacing * 3;
        ButtonWidget nameVisibleButton = ButtonWidget.builder(
            data.nameVisible()
                ? Text.literal("Hide name")
                : Text.literal("Show name"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_NAME_VISIBLE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, nameVisibleY, buttonWidth, 20)
            .build();
        if (data.nameVisible()) {
            nameVisibleButton.setMessage(Text.literal("Hide name").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            nameVisibleButton.setMessage(Text.literal("Show name").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(nameVisibleButton);
        this.addDrawableChild(nameVisibleButton);

        int visualizeY = baseButtonY + buttonSpacing * 4;
        ButtonWidget visualizeButton = ButtonWidget.builder(
            data.visualizeActive()
                ? Text.literal("Disable visualization")
                : Text.literal("Enable visualization"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, visualizeY, buttonWidth, 20)
            .build();
        if (data.visualizeActive()) {
            visualizeButton.setMessage(Text.literal("Disable visualization").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            visualizeButton.setMessage(Text.literal("Enable visualization").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(visualizeButton);
        this.addDrawableChild(visualizeButton);

        int visualize3DY = baseButtonY + buttonSpacing * 5;
        ButtonWidget visualize3DButton = ButtonWidget.builder(
            data.visualize3DActive()
                ? Text.literal("Disable 3D visualization")
                : Text.literal("Enable 3D visualization"),
            btn -> ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.TOGGLE_VISUALIZE3D,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                0
            ))
            .dimensions(buttonX, visualize3DY, buttonWidth, 20)
            .build();
        if (data.visualize3DActive()) {
            visualize3DButton.setMessage(Text.literal("Disable 3D visualization").formatted(net.minecraft.util.Formatting.WHITE));
        } else {
            visualize3DButton.setMessage(Text.literal("Enable 3D visualization").formatted(net.minecraft.util.Formatting.WHITE));
        }
        actionButtons.add(visualize3DButton);
        this.addDrawableChild(visualize3DButton);
        
        int resetY = panelY + panelHeight + 8;
        resetButton = ButtonWidget.builder(
            Text.literal("Reset to defaults"),
            btn -> {
                this.client.setScreen(new ChunkloaderConfirmationScreen(
                    this,
                    Text.literal("Reset to Defaults?").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD),
                    Text.literal("This will reset all settings to default values.\nThis action cannot be undone!"),
                    () -> {
                        ChunkloaderNetworking.sendAction(
                ChunkloaderActionPayload.Action.RESET_TO_DEFAULTS,
                data.fakeplayerChunkX(),
                data.fakeplayerChunkZ(),
                            0
                        );
                    },
                    null
                ));
            })
            .dimensions(buttonX, resetY, buttonWidth, 20)
            .build();
        resetButton.setMessage(Text.literal("Reset to defaults").formatted(net.minecraft.util.Formatting.WHITE));
        this.addDrawableChild(resetButton);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}

