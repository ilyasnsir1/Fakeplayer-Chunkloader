package de.chunkloader.client.screen;

import de.chunkloader.network.payload.DisabledChunkloadersListPayload;
import de.chunkloader.network.ChunkloaderNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class DisabledChunkloadersScreen extends Screen {
    
    private List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 40;
    private int contentTop;
    private int contentBottom;
    private final Screen parentScreen;
    
    public DisabledChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders) {
        this(disabledChunkloaders, null);
    }
    
    public DisabledChunkloadersScreen(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders, Screen parentScreen) {
        super(Text.literal("Disabled Fakeplayer/Chunkplayer"));
        this.parentScreen = parentScreen;
        this.disabledChunkloaders = new ArrayList<>();
        if (disabledChunkloaders != null) {
            this.disabledChunkloaders.addAll(disabledChunkloaders);
        }
    }
    
    public void updateDisabledChunkloaders(List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> disabledChunkloaders) {
        this.disabledChunkloaders = new ArrayList<>();
        if (disabledChunkloaders != null) {
            this.disabledChunkloaders.addAll(disabledChunkloaders);
        }
        this.scrollOffset = 0;
        this.clearChildren();
        this.init();
    }
    
    @Override
    protected void init() {
        super.init();
        
        contentTop = 50;
        contentBottom = this.height - 60;
        
        int buttonWidth = 100;
        int buttonSpacing = 10;
        int totalButtonsWidth = parentScreen != null ? buttonWidth * 2 + buttonSpacing : buttonWidth;
        int buttonX = (this.width - totalButtonsWidth) / 2;
        int buttonY = this.height - 30;

        if (parentScreen != null) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.client.setScreen(parentScreen))
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build()
            );
            buttonX += buttonWidth + buttonSpacing;
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                btn -> this.client.setScreen(null))
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
        
        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;
        
        int maxScroll = Math.max(0, disabledChunkloaders.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        
        for (int i = 0; i < itemsThatFitFully && (scrollOffset + i) < disabledChunkloaders.size(); i++) {
            int index = scrollOffset + i;
            if (index < 0 || index >= disabledChunkloaders.size()) {
                continue;
            }
            DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry = disabledChunkloaders.get(index);
            int itemY = contentTop + i * ITEM_HEIGHT;
            
            if (itemY < contentTop || itemY + ITEM_HEIGHT > contentBottom) {
                continue;
            }
            
            TextRenderer textRenderer = this.textRenderer;
            int maxTextWidth = 0;
            for (var e : disabledChunkloaders) {
                String eName = e.name() != null ? e.name() : "Unnamed";
                String ePosText = String.format("Chunk: %d, %d | Block: %d, %d, %d", 
                    e.chunkX(), e.chunkZ(), e.blockX(), e.blockY(), e.blockZ());
                String eDimText = "Dimension: " + e.dimension().replace("minecraft:", "");
                int textWidth = Math.max(
                    textRenderer.getWidth(eName),
                    Math.max(
                        textRenderer.getWidth(ePosText),
                        textRenderer.getWidth(eDimText)
                    )
                );
                maxTextWidth = Math.max(maxTextWidth, textWidth);
            }
            
            int infoStartX = 20;
            int infoEndX = infoStartX + maxTextWidth;
            int editButtonSpacing = 15;
            int editButtonX = infoEndX + editButtonSpacing;
            int editButtonWidth = 60;
            
            int deleteButtonWidth = 70;
            int restoreButtonWidth = 70;
            int restoreDeleteSpacing = 10;
            int rightMargin = 20;
            
            int deleteButtonX = this.width - rightMargin - deleteButtonWidth;
            int restoreButtonX = deleteButtonX - restoreDeleteSpacing - restoreButtonWidth;
            
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Edit"),
                    btn -> {
                        this.client.setScreen(new EditDisabledChunkloaderCoordsScreen(this, entry));
                    })
                .dimensions(editButtonX, itemY + 10, editButtonWidth, 20)
                .build()
            );
            
            ButtonWidget restoreButton = ButtonWidget.builder(
                    Text.literal("Restore"),
                    btn -> {
                        ChunkloaderNetworking.sendRestoreDisabledChunkloader(entry.chunkX(), entry.chunkZ());
                        ChunkloaderNetworking.requestDisabledChunkloadersList();
                    })
                .dimensions(restoreButtonX, itemY + 10, restoreButtonWidth, 20)
                .build();
            this.addDrawableChild(restoreButton);
            
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Delete").formatted(Formatting.RED),
                    btn -> {
                        String name = entry.name() != null ? entry.name() : "Unnamed";
                        Text title = Text.literal("Delete Chunkloader?").formatted(Formatting.RED, Formatting.BOLD);
                        Text message = Text.literal("Are you sure you want to permanently delete\n" + name + "?");
                        DisabledChunkloadersScreen screen = this;
                        int deleteChunkX = entry.chunkX();
                        int deleteChunkZ = entry.chunkZ();
                        this.client.setScreen(new ChunkloaderConfirmationScreen(
                            screen,
                            title,
                            message,
                            () -> {
                                List<DisabledChunkloadersListPayload.DisabledChunkloaderEntry> newList = new ArrayList<>(disabledChunkloaders);
                                newList.removeIf(e -> e.chunkX() == deleteChunkX && e.chunkZ() == deleteChunkZ);
                                disabledChunkloaders.clear();
                                disabledChunkloaders.addAll(newList);
                                scrollOffset = 0;
                                ChunkloaderNetworking.sendDeleteDisabledChunkloader(deleteChunkX, deleteChunkZ);
                                ChunkloaderNetworking.requestDisabledChunkloadersList();
                            },
                            null
                        ));
                    })
                .dimensions(deleteButtonX, itemY + 10, deleteButtonWidth, 20)
                .build()
            );
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);
        
        TextRenderer renderer = this.textRenderer;
        
        Text title = Text.literal("Disabled Fakeplayer/Chunkplayer").formatted(Formatting.BOLD, Formatting.RED);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, 20, 0xFFFF5555, false);
        
        if (disabledChunkloaders.isEmpty()) {
            Text emptyText = Text.literal("No disabled fakeplayers/chunkplayers").formatted(Formatting.GRAY);
            int emptyWidth = renderer.getWidth(emptyText);
            context.drawText(renderer, emptyText, (this.width - emptyWidth) / 2, this.height / 2, 0xFFCCCCCC, false);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        
        int availableHeight = contentBottom - contentTop;
        int itemsThatFitFully = availableHeight / ITEM_HEIGHT;
        
        int maxScroll = Math.max(0, disabledChunkloaders.size() - itemsThatFitFully);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        
        context.enableScissor(0, contentTop, this.width, contentBottom);
        
        for (int i = 0; i < itemsThatFitFully && (scrollOffset + i) < disabledChunkloaders.size(); i++) {
            int index = scrollOffset + i;
            if (index < 0 || index >= disabledChunkloaders.size()) {
                continue;
            }
            DisabledChunkloadersListPayload.DisabledChunkloaderEntry entry = disabledChunkloaders.get(index);
            int itemY = contentTop + i * ITEM_HEIGHT;
            
            if (itemY < contentTop || itemY + ITEM_HEIGHT > contentBottom) {
                continue;
            }
            
            String name = entry.name() != null ? entry.name() : "Unnamed";
            Formatting color;
            int colorValue;
            if (entry.allowMobSpawning()) {
                if (entry.hasWarning()) {
                    color = Formatting.YELLOW;
                    colorValue = 0xFFFFFF55;
                } else {
                    color = Formatting.GREEN;
                    colorValue = 0xFF55FF55;
                }
            } else {
                color = Formatting.BLUE;
                colorValue = 0xFF5596FF;
            }
            Text nameText = Text.literal(name).formatted(color);
            context.drawText(renderer, nameText, 20, itemY + 5, colorValue, false);
            
            String posText = String.format("Chunk: %d, %d | Block: %d, %d, %d", 
                entry.chunkX(), entry.chunkZ(), entry.blockX(), entry.blockY(), entry.blockZ());
            Text positionText = Text.literal(posText).formatted(Formatting.GRAY);
            context.drawText(renderer, positionText, 20, itemY + 18, 0xFFCCCCCC, false);
            
            String dimText = entry.dimension().replace("minecraft:", "");
            Formatting dimColor = Formatting.GRAY;
            String dimLower = dimText.toLowerCase();
            if (dimLower.contains("overworld")) {
                dimColor = Formatting.GREEN;
            } else if (dimLower.contains("end")) {
                dimColor = Formatting.LIGHT_PURPLE;
            } else if (dimLower.contains("nether")) {
                dimColor = Formatting.RED;
            }
            Text dimensionText = Text.literal("Dimension: ").formatted(Formatting.GRAY)
                .append(Text.literal(dimText).formatted(dimColor));
            context.drawText(renderer, dimensionText, 20, itemY + 29, 0xFFFFFFFF, false);
            
            if (i < itemsThatFitFully - 1 && (scrollOffset + i + 1) < disabledChunkloaders.size()) {
                int lineY = itemY + ITEM_HEIGHT - 1;
                int lineLeft = 10;
                int lineRight = this.width - 10;
                context.fill(lineLeft, lineY, lineRight, lineY + 1, 0x33FFFFFF);
            }
        }
        
        context.disableScissor();
        
        drawScrollbar(context);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void drawScrollbar(DrawContext context) {
        int availableHeight = contentBottom - contentTop;
        int itemsVisible = availableHeight / ITEM_HEIGHT;
        int maxItems = Math.max(0, disabledChunkloaders.size() - itemsVisible);
        
        if (maxItems <= 0) {
            return;
        }
        
        int scrollbarWidth = 3;
        int scrollbarX = this.width - scrollbarWidth - 2;
        int scrollbarHeight = (int)((double)itemsVisible / disabledChunkloaders.size() * availableHeight);
        if (maxItems > 0) {
            int scrollbarY = contentTop + (int)((double)scrollOffset / maxItems * (availableHeight - scrollbarHeight));
            
            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarWidth, contentBottom, 0x33000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
        }
    }
    
    private void drawDimBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int availableHeight = contentBottom - contentTop;
        int maxItems = Math.max(0, disabledChunkloaders.size() - (availableHeight / ITEM_HEIGHT));
        
        int scrollDelta = (int)(verticalAmount * 3);
        scrollOffset = Math.max(0, Math.min(maxItems, scrollOffset - scrollDelta));
        
        this.clearChildren();
        this.init();
        return true;
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}
