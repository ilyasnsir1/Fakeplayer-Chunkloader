package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
    modid = ChunkloaderForgeMod.MODID,
    value = Dist.CLIENT
)
public class ChunkloaderKeyMappings {

    public static KeyMapping simulationStatusHUDToggleKey;
    public static KeyMapping chunkplayerStatusHUDToggleKey;
    public static KeyMapping disabledChunkloadersKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        simulationStatusHUDToggleKey = new KeyMapping(
            "key.chunkloader.simulation_status_hud_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
			"key.categories.misc"
        );
        event.register(simulationStatusHUDToggleKey);

        chunkplayerStatusHUDToggleKey = new KeyMapping(
            "key.chunkloader.chunkplayer_status_hud_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
			"key.categories.misc"
        );
        event.register(chunkplayerStatusHUDToggleKey);

        disabledChunkloadersKey = new KeyMapping(
            "key.chunkloader.disabled_chunkloaders",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
			"key.categories.misc"
        );
        event.register(disabledChunkloadersKey);
    }
}

