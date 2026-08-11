package de.chunkloader.mixin.client;

import de.chunkloader.client.hud.ChunkplayerStatusHUD;
import de.chunkloader.client.hud.SimulationStatusHUD;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void renderStatusHUDs(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.player == null || client.currentScreen != null) {
            return;
        }

        int offsetY = 0;

        if (SimulationStatusHUD.isEnabled()) {
            SimulationStatusHUD.render(
                context,
                client.textRenderer,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight()
            );
            offsetY = SimulationStatusHUD.getHeight() + 5;
        }

        if (ChunkplayerStatusHUD.isEnabled()) {
            ChunkplayerStatusHUD.render(
                context,
                client.textRenderer,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(),
                offsetY
            );
        }
    }
}

