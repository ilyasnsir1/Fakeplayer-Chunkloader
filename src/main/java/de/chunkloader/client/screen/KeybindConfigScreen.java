package de.chunkloader.client.screen;

import de.chunkloader.client.ChunkloaderKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.KeyEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KeybindConfigScreen extends Screen {

    private final Screen parent;
    private KeyMapping waitingForKey = null;
    private int tickCounter = 0;
    private String errorMessage = null;

    private Button key1Button;
    private Button key2Button;
    private Button key3Button;

    public KeybindConfigScreen(Screen parent) {
        super(Component.literal("Keybind Settings"));
        this.parent = parent;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 30;

        key1Button = Button.builder(
            getKeyButtonText(ChunkloaderKeyMappings.simulationStatusHUDToggleKey, "Simulation Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderKeyMappings.simulationStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(key1Button);

        key2Button = Button.builder(
            getKeyButtonText(ChunkloaderKeyMappings.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderKeyMappings.chunkplayerStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(key2Button);

        key3Button = Button.builder(
            getKeyButtonText(ChunkloaderKeyMappings.disabledChunkloadersKey, "Disabled Players List"),
            btn -> {
                waitingForKey = ChunkloaderKeyMappings.disabledChunkloadersKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(key3Button);

        int backButtonWidth = 100;
        int backButtonY = this.height - 30;
        this.addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> this.minecraft.setScreen(parent))
            .bounds(centerX - backButtonWidth / 2, backButtonY, backButtonWidth, buttonHeight)
            .build()
        );
    }

    private Component getKeyButtonText(KeyMapping keyMapping, String label) {
        if (keyMapping == null) {
            return Component.literal(label + ": Unknown");
        }
        String keyName = keyMapping.getTranslatedKeyMessage().getString();
        if (waitingForKey == keyMapping) {
            return Component.literal(label + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Press a key...").withStyle(ChatFormatting.YELLOW));
        }
        return Component.literal(label + ": ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal(keyName).withStyle(ChatFormatting.GREEN));
    }

    private void updateButtons() {
        if (key1Button != null) {
            key1Button.setMessage(getKeyButtonText(ChunkloaderKeyMappings.simulationStatusHUDToggleKey, "Simulation Status HUD"));
        }
        if (key2Button != null) {
            key2Button.setMessage(getKeyButtonText(ChunkloaderKeyMappings.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"));
        }
        if (key3Button != null) {
            key3Button.setMessage(getKeyButtonText(ChunkloaderKeyMappings.disabledChunkloadersKey, "Disabled Players List"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        Component title = Component.literal("Keybind Settings").withStyle(ChatFormatting.BOLD);
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, centerX - titleWidth / 2, startY - 30, 0xFFFFFFFF, false);

        if (waitingForKey != null) {
            Component instruction = Component.literal("Press a key to bind (ESC to cancel)").withStyle(ChatFormatting.YELLOW);
            int instructionWidth = this.font.width(instruction);
            graphics.drawString(this.font, instruction, centerX - instructionWidth / 2, startY + 120, 0xFFFFFF00, false);

            if (tickCounter > 0 && tickCounter % 20 < 10) {
                Component blink = Component.literal("Waiting for input...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                int blinkWidth = this.font.width(blink);
                graphics.drawString(this.font, blink, centerX - blinkWidth / 2, startY + 140, 0xFFCCCCCC, false);
            }
        }

        if (errorMessage != null) {
            Component error = Component.literal(errorMessage).withStyle(ChatFormatting.RED);
            int errorWidth = this.font.width(error);
            graphics.drawString(this.font, error, centerX - errorWidth / 2, startY + 160, 0xFFFF0000, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    private boolean isKeyAlreadyBound(InputConstants.Key key, KeyMapping exclude) {
        KeyMapping[] allKeys = Minecraft.getInstance().options.keyMappings;
        for (KeyMapping mapping : allKeys) {
            if (mapping != exclude) {
                InputConstants.Key boundKey = mapping.getKey();
                if (boundKey != null && boundKey.equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (waitingForKey != null) {
            int keyCode = event.key();
            if (keyCode == 256) {
                waitingForKey = null;
                errorMessage = null;
                updateButtons();
                return true;
            }

            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);

            if (isKeyAlreadyBound(key, waitingForKey)) {
                errorMessage = "This key is already bound to another action!";
                updateButtons();
                return true;
            }

            errorMessage = null;
            waitingForKey.setKey(key);
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            waitingForKey = null;
            updateButtons();
            return true;
        }

        if (event.key() == 256) {
            this.minecraft.setScreen(parent);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (waitingForKey != null) {
            tickCounter++;
            updateButtons();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

