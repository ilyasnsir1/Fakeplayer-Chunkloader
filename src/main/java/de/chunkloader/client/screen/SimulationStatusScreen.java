package de.chunkloader.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
public class SimulationStatusScreen extends Screen {

    private final boolean inSimulatedChunk;
    private final String fakeplayerName;
    private final int chunkX;
    private final int chunkZ;
    private final int distance;

    public SimulationStatusScreen(boolean inSimulatedChunk, String fakeplayerName, int chunkX, int chunkZ, int distance) {
        super(Component.literal("Simulation Status"));
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

        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.minecraft.setScreen(null))
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        drawDimBackground(context);

        Font renderer = this.font;
        int lineHeight = 12;
        int y = this.height / 2 - 40;

        Component title = Component.literal("Simulation Status").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
        int titleWidth = renderer.width(title);
        context.text(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += lineHeight * 2;

        if (inSimulatedChunk) {
            Component statusText = Component.literal("You are in a simulated chunk!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            int statusWidth = renderer.width(statusText);
            context.text(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;

            if (fakeplayerName != null) {
                Component fakeplayerLabel = Component.literal("Fakeplayer: ").withStyle(ChatFormatting.GRAY);
                Component fakeplayerValue = Component.literal(fakeplayerName).withStyle(ChatFormatting.YELLOW);
                int labelWidth = renderer.width(fakeplayerLabel);
                context.text(renderer, fakeplayerLabel, (this.width - labelWidth - renderer.width(fakeplayerValue)) / 2, y, 0xFFCCCCCC, false);
                context.text(renderer, fakeplayerValue, (this.width - labelWidth - renderer.width(fakeplayerValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }

            Component chunkLabel = Component.literal("Chunk Position: ").withStyle(ChatFormatting.GRAY);
            Component chunkValue = Component.literal(chunkX + ", " + chunkZ).withStyle(ChatFormatting.WHITE);
            int labelWidth = renderer.width(chunkLabel);
            context.text(renderer, chunkLabel, (this.width - labelWidth - renderer.width(chunkValue)) / 2, y, 0xFFCCCCCC, false);
            context.text(renderer, chunkValue, (this.width - labelWidth - renderer.width(chunkValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
            y += lineHeight;

            if (distance >= 0) {
                Component distanceLabel = Component.literal("Distance: ").withStyle(ChatFormatting.GRAY);
                Component distanceValue = Component.literal(distance + " chunks").withStyle(ChatFormatting.WHITE);
                int labelWidth2 = renderer.width(distanceLabel);
                context.text(renderer, distanceLabel, (this.width - labelWidth2 - renderer.width(distanceValue)) / 2, y, 0xFFCCCCCC, false);
                context.text(renderer, distanceValue, (this.width - labelWidth2 - renderer.width(distanceValue)) / 2 + labelWidth2, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }

            Component infoText = Component.literal("~625 chunks are simulated").withStyle(ChatFormatting.GRAY);
            int infoWidth = renderer.width(infoText);
            context.text(renderer, infoText, (this.width - infoWidth) / 2, y + lineHeight, 0xFFCCCCCC, false);
        } else {
            Component statusText = Component.literal("You are NOT in a simulated chunk").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            int statusWidth = renderer.width(statusText);
            context.text(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;

            Component infoText = Component.literal("No fakeplayer is simulating this area").withStyle(ChatFormatting.GRAY);
            int infoWidth = renderer.width(infoText);
            context.text(renderer, infoText, (this.width - infoWidth) / 2, y, 0xFFCCCCCC, false);
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
    public boolean isPauseScreen() {
        return false;
    }
}

