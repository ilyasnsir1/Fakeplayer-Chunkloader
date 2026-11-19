package de.chunkloader.client;

import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ChunkloaderKeyMappings {

    public static KeyMapping simulationStatusHUDToggleKey;
    public static KeyMapping chunkplayerStatusHUDToggleKey;
    public static KeyMapping disabledChunkloadersKey;
    public static KeyMapping activeChunkloadersKey;

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

        activeChunkloadersKey = new KeyMapping(
            "key.chunkloader.active_chunkloaders",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.misc"
        );
        event.register(activeChunkloadersKey);
    }
}

