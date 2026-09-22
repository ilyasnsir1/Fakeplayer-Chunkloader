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
public class ChunkplayerStatusScreen extends Screen {

    private final boolean inLoadedChunk;
    private final String chunkplayerName;
    private final int chunkX;
    private final int chunkZ;
    private final int radius;
    private final int distance;

    public ChunkplayerStatusScreen(boolean inLoadedChunk, String chunkplayerName, int chunkX, int chunkZ, int radius, int distance) {
        super(Component.literal("Chunkplayer Status"));
        this.inLoadedChunk = inLoadedChunk;
        this.chunkplayerName = chunkplayerName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.radius = radius;
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
                btn -> this.minecraft.gui.setScreen(null))
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

        Component title = Component.literal("Chunkplayer Status").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
        int titleWidth = renderer.width(title);
        context.text(renderer, title, (this.width - titleWidth) / 2, y, 0xFFFFFFFF, false);
        y += lineHeight * 2;

        if (inLoadedChunk) {
            Component statusText = Component.literal("You are in a loaded chunk!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            int statusWidth = renderer.width(statusText);
            context.text(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;

            if (chunkplayerName != null) {
                Component chunkplayerLabel = Component.literal("Chunkplayer: ").withStyle(ChatFormatting.GRAY);
                Component chunkplayerValue = Component.literal(chunkplayerName).withStyle(ChatFormatting.AQUA);
                int labelWidth = renderer.width(chunkplayerLabel);
                context.text(renderer, chunkplayerLabel, (this.width - labelWidth - renderer.width(chunkplayerValue)) / 2, y, 0xFFCCCCCC, false);
                context.text(renderer, chunkplayerValue, (this.width - labelWidth - renderer.width(chunkplayerValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }

            Component chunkLabel = Component.literal("Chunk Position: ").withStyle(ChatFormatting.GRAY);
            Component chunkValue = Component.literal(chunkX + ", " + chunkZ).withStyle(ChatFormatting.WHITE);
            int labelWidth = renderer.width(chunkLabel);
            context.text(renderer, chunkLabel, (this.width - labelWidth - renderer.width(chunkValue)) / 2, y, 0xFFCCCCCC, false);
            context.text(renderer, chunkValue, (this.width - labelWidth - renderer.width(chunkValue)) / 2 + labelWidth, y, 0xFFFFFFFF, false);
            y += lineHeight;

            Component radiusLabel = Component.literal("Radius: ").withStyle(ChatFormatting.GRAY);
            Component radiusValue = Component.literal(radius + " chunks").withStyle(ChatFormatting.WHITE);
            int radiusLabelWidth = renderer.width(radiusLabel);
            context.text(renderer, radiusLabel, (this.width - radiusLabelWidth - renderer.width(radiusValue)) / 2, y, 0xFFCCCCCC, false);
            context.text(renderer, radiusValue, (this.width - radiusLabelWidth - renderer.width(radiusValue)) / 2 + radiusLabelWidth, y, 0xFFFFFFFF, false);
            y += lineHeight;

            if (distance >= 0) {
                Component distanceLabel = Component.literal("Distance: ").withStyle(ChatFormatting.GRAY);
                Component distanceValue = Component.literal(distance + " chunks").withStyle(ChatFormatting.WHITE);
                int distanceLabelWidth = renderer.width(distanceLabel);
                context.text(renderer, distanceLabel, (this.width - distanceLabelWidth - renderer.width(distanceValue)) / 2, y, 0xFFCCCCCC, false);
                context.text(renderer, distanceValue, (this.width - distanceLabelWidth - renderer.width(distanceValue)) / 2 + distanceLabelWidth, y, 0xFFFFFFFF, false);
                y += lineHeight;
            }

            Component infoText = Component.literal("Chunks are kept loaded via chunk tickets").withStyle(ChatFormatting.GRAY);
            int infoWidth = renderer.width(infoText);
            context.text(renderer, infoText, (this.width - infoWidth) / 2, y + lineHeight, 0xFFCCCCCC, false);
        } else {
            Component statusText = Component.literal("You are NOT in a loaded chunk").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            int statusWidth = renderer.width(statusText);
            context.text(renderer, statusText, (this.width - statusWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight * 2;

            Component infoText = Component.literal("No chunkplayer is loading this area").withStyle(ChatFormatting.GRAY);
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

