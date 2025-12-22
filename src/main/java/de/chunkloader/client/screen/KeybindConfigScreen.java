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
    
    private ButtonWidget key1Button;
    private ButtonWidget key2Button;
    private ButtonWidget key3Button;
    
    public KeybindConfigScreen(Screen parent) {
        super(Text.literal("Keybind Settings"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 30;
        
        key1Button = ButtonWidget.builder(
            getKeyButtonText(ChunkloaderClient.simulationStatusHUDToggleKey, "Simulation Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderClient.simulationStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key1Button);
        
        key2Button = ButtonWidget.builder(
            getKeyButtonText(ChunkloaderClient.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"),
            btn -> {
                waitingForKey = ChunkloaderClient.chunkplayerStatusHUDToggleKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .dimensions(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key2Button);
        
        key3Button = ButtonWidget.builder(
            getKeyButtonText(ChunkloaderClient.disabledChunkloadersKey, "Disabled Chunkloaders List"),
            btn -> {
                waitingForKey = ChunkloaderClient.disabledChunkloadersKey;
                tickCounter = 0;
                errorMessage = null;
                updateButtons();
            })
            .dimensions(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(key3Button);
        
        int backButtonWidth = 100;
        int backButtonY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> this.client.setScreen(parent))
            .dimensions(centerX - backButtonWidth / 2, backButtonY, backButtonWidth, buttonHeight)
            .build()
        );
    }
    
    private Text getKeyButtonText(KeyBinding keyBinding, String label) {
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
    
    private void updateButtons() {
        if (key1Button != null) {
            key1Button.setMessage(getKeyButtonText(ChunkloaderClient.simulationStatusHUDToggleKey, "Simulation Status HUD"));
        }
        if (key2Button != null) {
            key2Button.setMessage(getKeyButtonText(ChunkloaderClient.chunkplayerStatusHUDToggleKey, "Chunkplayer Status HUD"));
        }
        if (key3Button != null) {
            key3Button.setMessage(getKeyButtonText(ChunkloaderClient.disabledChunkloadersKey, "Disabled Chunkloaders List"));
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
                updateButtons();
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
            updateButtons();
            return true;
        }
        
        return super.keyPressed(keyInput);
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
    public boolean shouldPause() {
        return false;
    }
}
