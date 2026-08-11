package de.chunkloader.client.screen;

import de.chunkloader.client.ChunkloaderClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class KeybindConfigScreen extends Screen {

    private final Screen parent;
    private KeyBinding waitingForKey = null;
    private int tickCounter = 0;
    private String errorMessage = null;

    private ButtonWidget key1ButtonWidget;
    private ButtonWidget key2ButtonWidget;
    private ButtonWidget key3ButtonWidget;

    public KeybindConfigScreen(Screen parent) {
        super(Text.literal("Keybind Settings"));
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

        key1ButtonWidget = ButtonWidget.builder(
            getKeyButtonWidgetText(ChunkloaderClient.simulationStatusHUDToggleKey, "Simulation Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderClient.simulationStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtonWidgets();
            })
            .dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key1ButtonWidget);

        key2ButtonWidget = ButtonWidget.builder(
            getKeyButtonWidgetText(ChunkloaderClient.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderClient.chunkplayerStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtonWidgets();
            })
            .dimensions(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key2ButtonWidget);

        key3ButtonWidget = ButtonWidget.builder(
            getKeyButtonWidgetText(ChunkloaderClient.disabledChunkloadersKey, "Disabled Players List"),
            btn -> {
                waitingForKey = ChunkloaderClient.disabledChunkloadersKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtonWidgets();
            })
            .dimensions(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key3ButtonWidget);

        int backButtonWidgetWidth = 100;
        int backButtonWidgetY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> this.client.setScreen(parent))
            .dimensions(centerX - backButtonWidgetWidth / 2, backButtonWidgetY, backButtonWidgetWidth, buttonHeight)
            .build()
        );
    }

    private Text getKeyButtonWidgetText(KeyBinding keyBinding, String label) {
        if (keyBinding == null) {
            return Text.literal(label + ": Unknown");
        }
        String keyName = keyBinding.getBoundKeyLocalizedText().getString();
        if (waitingForKey == keyBinding) {
            return Text.literal(label + ": ").formatted(Formatting.WHITE)
                .append(Text.literal("Press a key...").formatted(Formatting.YELLOW));
        }
        return Text.literal(label + ": ").formatted(Formatting.WHITE)
            .append(Text.literal(keyName).formatted(Formatting.GREEN));
    }

    private void updateButtonWidgets() {
        if (key1ButtonWidget != null) {
            key1ButtonWidget.setMessage(getKeyButtonWidgetText(ChunkloaderClient.simulationStatusHUDToggleKey, "Simulation Status HUD"));
        }
        if (key2ButtonWidget != null) {
            key2ButtonWidget.setMessage(getKeyButtonWidgetText(ChunkloaderClient.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"));
        }
        if (key3ButtonWidget != null) {
            key3ButtonWidget.setMessage(getKeyButtonWidgetText(ChunkloaderClient.disabledChunkloadersKey, "Disabled Players List"));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        Text title = Text.literal("Keybind Settings").formatted(Formatting.BOLD);
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, centerX - titleWidth / 2, startY - 30, 0xFFFFFFFF, false);

        if (waitingForKey != null) {
            Text instruction = Text.literal("Press a key to bind (ESC to cancel)").formatted(Formatting.YELLOW);
            int instructionWidth = this.textRenderer.getWidth(instruction);
            context.drawText(this.textRenderer, instruction, centerX - instructionWidth / 2, startY + 120, 0xFFFFFF00, false);

            if (tickCounter > 0 && tickCounter % 20 < 10) {
                Text blink = Text.literal("Waiting for input...").formatted(Formatting.GRAY, Formatting.ITALIC);
                int blinkWidth = this.textRenderer.getWidth(blink);
                context.drawText(this.textRenderer, blink, centerX - blinkWidth / 2, startY + 140, 0xFFCCCCCC, false);
            }
        }

        if (errorMessage != null) {
            Text error = Text.literal(errorMessage).formatted(Formatting.RED);
            int errorWidth = this.textRenderer.getWidth(error);
            context.drawText(this.textRenderer, error, centerX - errorWidth / 2, startY + 160, 0xFFFF0000, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private boolean isKeyAlreadyBound(InputUtil.Key key, KeyBinding exclude) {
        for (KeyBinding binding : MinecraftClient.getInstance().options.allKeys) {
            if (binding != exclude) {
                try {
                    java.lang.reflect.Field keyField = KeyBinding.class.getDeclaredField("boundKey");
                    keyField.setAccessible(true);
                    InputUtil.Key boundKey = (InputUtil.Key) keyField.get(binding);
                    if (boundKey != null && boundKey.equals(key)) {
                        return true;
                    }
                } catch (Exception e) {
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (waitingForKey != null) {
            int keyCode = keyInput.key();
            if (keyCode == 256) {
                waitingForKey = null;
                errorMessage = null;
                updateButtonWidgets();
                return true;
            }

            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(keyCode);

            if (isKeyAlreadyBound(key, waitingForKey)) {
                errorMessage = "This key is already bound to another action!";
                return true;
            }

            errorMessage = null;
            waitingForKey.setBoundKey(key);
            KeyBinding.updateKeysByCode();
            MinecraftClient.getInstance().options.write();
            waitingForKey = null;
            updateButtonWidgets();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void tick() {
        super.tick();
        if (waitingForKey != null) {
            tickCounter++;
            updateButtonWidgets();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
