package de.chunkloader.mixin.client;

import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void renderStatusHUDs(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.font == null || client.player == null || client.screen != null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int offsetY = 0;

        if (SimulationStatusHUD.isEnabled()) {
            SimulationStatusHUD.render(graphics, screenWidth, screenHeight);
            offsetY = SimulationStatusHUD.getHeight();
            if (offsetY > 0) {
                offsetY += 5;
            }
        }

        if (ChunkplayerStatusHUD.isEnabled()) {
            ChunkplayerStatusHUD.render(graphics, screenWidth, screenHeight, offsetY);
        }
    }
}
