package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class SimulationStatusScreen extends Screen {
    
    private final boolean inSimulatedChunk;
    private final String fakeplayerName;
    private final int chunkX;
    private final int chunkZ;
    private final int distance;

    public SimulationStatusScreen(boolean inSimulatedChunk, String fakeplayerName, int chunkX, int chunkZ, int distance) {
        super(Text.literal("Simulation Status"));
        this.inSimulatedChunk = inSimulatedChunk;
        this.fakeplayerName = fakeplayerName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.distance = distance;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 30;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                btn -> this.client.setScreen(null))
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);
        
        TextRenderer renderer = this.textRenderer;
        int lineHeight = 12;
        int y = this.height / 2 - 40;
        
        Text title = Text.literal("Simulation Status").formatted(Formatting.BOLD, Formatting.WHITE);
        int titleWidth = renderer.getWidth(title);
        context.drawText(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += lineHeight * 2;
        
        if (inSimulatedChunk) {
            Text statusText = Text.literal("You are in a simulated chunk!").formatted(Formatting.GREEN, Formatting.BOLD);
            int statusWidth = renderer.getWidth(statusText);
            context.drawText(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;
            
            if (fakeplayerName != null) {
                Text fakeplayerLabel = Text.literal("Fakeplayer: ").formatted(Formatting.GRAY);
                Text fakeplayerValue = Text.literal(fakeplayerName).formatted(Formatting.YELLOW);
                int labelWidth = renderer.getWidth(fakeplayerLabel);
                context.drawText(renderer, fakeplayerLabel, (this.width - labelWidth - renderer.getWidth(fakeplayerValue)) / 2, y, 0xFFCCCCCC, false);
                context.drawText(renderer, fakeplayerValue, (this.width - labelWidth - renderer.getWidth(fakeplayerValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }
            
            Text chunkLabel = Text.literal("Chunk Position: ").formatted(Formatting.GRAY);
            Text chunkValue = Text.literal(chunkX + ", " + chunkZ).formatted(Formatting.WHITE);
            int labelWidth = renderer.getWidth(chunkLabel);
            context.drawText(renderer, chunkLabel, (this.width - labelWidth - renderer.getWidth(chunkValue)) / 2, y, 0xFFCCCCCC, false);
            context.drawText(renderer, chunkValue, (this.width - labelWidth - renderer.getWidth(chunkValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
            y += lineHeight;
            
            if (distance >= 0) {
                Text distanceLabel = Text.literal("Distance: ").formatted(Formatting.GRAY);
                Text distanceValue = Text.literal(distance + " chunks").formatted(Formatting.WHITE);
                int labelWidth2 = renderer.getWidth(distanceLabel);
                context.drawText(renderer, distanceLabel, (this.width - labelWidth2 - renderer.getWidth(distanceValue)) / 2, y, 0xFFCCCCCC, false);
                context.drawText(renderer, distanceValue, (this.width - labelWidth2 - renderer.getWidth(distanceValue)) / 2 + labelWidth2, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }
            
            Text infoText = Text.literal("~625 chunks are simulated").formatted(Formatting.GRAY);
            int infoWidth = renderer.getWidth(infoText);
            context.drawText(renderer, infoText, (this.width - infoWidth) / 2, y + lineHeight, 0xFFCCCCCC, false);
        } else {
            Text statusText = Text.literal("You are NOT in a simulated chunk").formatted(Formatting.RED, Formatting.BOLD);
            int statusWidth = renderer.getWidth(statusText);
            context.drawText(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;
            
            Text infoText = Text.literal("No fakeplayer is simulating this area").formatted(Formatting.GRAY);
            int infoWidth = renderer.getWidth(infoText);
            context.drawText(renderer, infoText, (this.width - infoWidth) / 2, y, 0xFFCCCCCC, false);
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
    public boolean shouldPause() {
        return false;
    }
}

