package de.chunkloader.mixin.client;

import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import de.chunkloader.client.hud.StatusHudLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
    private void renderStatusHUDs(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.font == null || client.player == null || client.gui.screen() != null) {
            return;
        }

        int chunkplayerOffsetY = StatusHudLayout.chunkplayerOffset();

        if (SimulationStatusHUD.isEnabled()) {
            SimulationStatusHUD.extractRenderState(
                context,
                client.font,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight()
            );
        }

        if (ChunkplayerStatusHUD.isEnabled()) {
            ChunkplayerStatusHUD.extractRenderState(
                context,
                client.font,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                chunkplayerOffsetY
            );
        }
    }
}