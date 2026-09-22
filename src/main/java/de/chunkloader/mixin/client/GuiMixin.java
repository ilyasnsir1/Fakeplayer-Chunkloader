package de.chunkloader.mixin.client;

import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderStatusHUDs(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.font == null || client.player == null || client.screen != null) {
            return;
        }

        int offsetY = 0;

        if (SimulationStatusHUD.isEnabled()) {
            SimulationStatusHUD.extractRenderState(
                context,
                client.font,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight()
            );
            offsetY = SimulationStatusHUD.getHeight() + 5;
        }

        if (ChunkplayerStatusHUD.isEnabled()) {
            ChunkplayerStatusHUD.extractRenderState(
                context,
                client.font,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                offsetY
            );
        }
    }
}
