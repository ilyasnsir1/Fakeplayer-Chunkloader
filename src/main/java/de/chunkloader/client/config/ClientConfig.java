package de.chunkloader.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "chunkloader_client_config.json";
    private static final int DEFAULT_PANEL_COLOR = 0xFF2B2F36;
    private static final int DEFAULT_BORDER_COLOR = 0xFF4A4A4A;
    private static final int DEFAULT_DIVIDER_COLOR = 0x33FFFFFF;
    private static final int DEFAULT_FRAME_COLOR = 0xFF111417;
    private static final int DEFAULT_SCROLLBAR_TRACK_COLOR = 0x33000000;
    private static final int DEFAULT_SCROLLBAR_THUMB_COLOR = 0xFFAAAAAA;
    private static final int DEFAULT_LEFT_PANEL_TEXT_COLOR = 0xCC808080;
    private static final int DEFAULT_LEFT_PANEL_VALUE_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_LEFT_PANEL_NAME_COLOR = 0xFFFFFFFF;

    private static final int DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR = DEFAULT_PANEL_COLOR;
    private static final int DEFAULT_ACTION_SEARCH_BORDER_COLOR = DEFAULT_BORDER_COLOR;
    private static final int DEFAULT_ACTION_SEARCH_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR = DEFAULT_LEFT_PANEL_TEXT_COLOR;

    private static final String DEFAULT_CHUNKMAP_LAYOUT_PRESET = "TOP";
    
    private int panelColor = DEFAULT_PANEL_COLOR;
    private int borderColor = DEFAULT_BORDER_COLOR;
    private int dividerColor = DEFAULT_DIVIDER_COLOR;
    private int frameColor = DEFAULT_FRAME_COLOR;
    private int scrollbarTrackColor = DEFAULT_SCROLLBAR_TRACK_COLOR;
    private int scrollbarThumbColor = DEFAULT_SCROLLBAR_THUMB_COLOR;
    private int leftPanelTextColor = DEFAULT_LEFT_PANEL_TEXT_COLOR;
    private int leftPanelValueColor = DEFAULT_LEFT_PANEL_VALUE_COLOR;
    private int leftPanelNameColor = DEFAULT_LEFT_PANEL_NAME_COLOR;

    private int actionSearchBackgroundColor = DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR;
    private int actionSearchBorderColor = DEFAULT_ACTION_SEARCH_BORDER_COLOR;
    private int actionSearchTextColor = DEFAULT_ACTION_SEARCH_TEXT_COLOR;
    private int actionSearchPlaceholderColor = DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR;

    private String chunkMapLayoutPreset = DEFAULT_CHUNKMAP_LAYOUT_PRESET;
    private Path configPath;
    
    private ClientConfig() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.runDirectory != null) {
            Path configDir = client.runDirectory.toPath().resolve("config");
            try {
                if (!java.nio.file.Files.exists(configDir)) {
                    java.nio.file.Files.createDirectories(configDir);
                }
                this.configPath = configDir.resolve(CONFIG_FILE);
            } catch (IOException e) {
                this.configPath = null;
            }
        } else {
            this.configPath = null;
        }
    }
    
    public static ClientConfig load() {
        ClientConfig config = new ClientConfig();
        if (config.configPath == null) {
            return config;
        }
        
        File configFile = config.configPath.toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("panelColor")) {
                    try {
                        config.panelColor = parseColor(json.get("panelColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.panelColor = DEFAULT_PANEL_COLOR;
                    }
                }
                if (json.has("borderColor")) {
                    try {
                        config.borderColor = parseColor(json.get("borderColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.borderColor = DEFAULT_BORDER_COLOR;
                    }
                }
                if (json.has("dividerColor")) {
                    try {
                        config.dividerColor = parseColor(json.get("dividerColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.dividerColor = DEFAULT_DIVIDER_COLOR;
                    }
                }
                if (json.has("frameColor")) {
                    try {
                        config.frameColor = parseColor(json.get("frameColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.frameColor = DEFAULT_FRAME_COLOR;
                    }
                }
                if (json.has("scrollbarTrackColor")) {
                    try {
                        config.scrollbarTrackColor = parseColor(json.get("scrollbarTrackColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.scrollbarTrackColor = DEFAULT_SCROLLBAR_TRACK_COLOR;
                    }
                }
                if (json.has("scrollbarThumbColor")) {
                    try {
                        config.scrollbarThumbColor = parseColor(json.get("scrollbarThumbColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.scrollbarThumbColor = DEFAULT_SCROLLBAR_THUMB_COLOR;
                    }
                }
                if (json.has("leftPanelTextColor")) {
                    try {
                        config.leftPanelTextColor = parseColor(json.get("leftPanelTextColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.leftPanelTextColor = DEFAULT_LEFT_PANEL_TEXT_COLOR;
                    }
                }
                if (json.has("leftPanelValueColor")) {
                    try {
                        config.leftPanelValueColor = parseColor(json.get("leftPanelValueColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.leftPanelValueColor = DEFAULT_LEFT_PANEL_VALUE_COLOR;
                    }
                }
                if (json.has("leftPanelNameColor")) {
                    try {
                        config.leftPanelNameColor = parseColor(json.get("leftPanelNameColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.leftPanelNameColor = DEFAULT_LEFT_PANEL_NAME_COLOR;
                    }
                }

                if (json.has("actionSearchBackgroundColor")) {
                    try {
                        config.actionSearchBackgroundColor = parseColor(json.get("actionSearchBackgroundColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.actionSearchBackgroundColor = DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR;
                    }
                }
                if (json.has("actionSearchBorderColor")) {
                    try {
                        config.actionSearchBorderColor = parseColor(json.get("actionSearchBorderColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.actionSearchBorderColor = DEFAULT_ACTION_SEARCH_BORDER_COLOR;
                    }
                }
                if (json.has("actionSearchTextColor")) {
                    try {
                        config.actionSearchTextColor = parseColor(json.get("actionSearchTextColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.actionSearchTextColor = DEFAULT_ACTION_SEARCH_TEXT_COLOR;
                    }
                }
                if (json.has("actionSearchPlaceholderColor")) {
                    try {
                        config.actionSearchPlaceholderColor = parseColor(json.get("actionSearchPlaceholderColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.actionSearchPlaceholderColor = DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR;
                    }
                }

                if (json.has("chunkMapLayoutPreset")) {
                    try {
                        String preset = json.get("chunkMapLayoutPreset").getAsString();
                        config.chunkMapLayoutPreset = (preset == null || preset.isBlank())
                            ? DEFAULT_CHUNKMAP_LAYOUT_PRESET
                            : preset;
                    } catch (Exception e) {
                        config.chunkMapLayoutPreset = DEFAULT_CHUNKMAP_LAYOUT_PRESET;
                    }
                } else if (json.has("chunkMapLeftButtonBar")) {
                    // Backward compatibility: older builds stored a boolean instead of a preset string.
                    try {
                        boolean left = json.get("chunkMapLeftButtonBar").getAsBoolean();
                        config.chunkMapLayoutPreset = left ? "LEFT" : DEFAULT_CHUNKMAP_LAYOUT_PRESET;
                    } catch (Exception e) {
                        config.chunkMapLayoutPreset = DEFAULT_CHUNKMAP_LAYOUT_PRESET;
                    }
                }
            } catch (Exception e) {
            }
        } else {
            config.save();
        }
        
        return config;
    }
    
    public void save() {
        if (configPath == null) {
            return;
        }
        
        try {
            JsonObject json = new JsonObject();
            json.addProperty("panelColor", String.format("#%08X", panelColor));
            json.addProperty("borderColor", String.format("#%08X", borderColor));
            json.addProperty("dividerColor", String.format("#%08X", dividerColor));
            json.addProperty("frameColor", String.format("#%08X", frameColor));
            json.addProperty("scrollbarTrackColor", String.format("#%08X", scrollbarTrackColor));
            json.addProperty("scrollbarThumbColor", String.format("#%08X", scrollbarThumbColor));
            json.addProperty("leftPanelTextColor", String.format("#%08X", leftPanelTextColor));
            json.addProperty("leftPanelValueColor", String.format("#%08X", leftPanelValueColor));
            json.addProperty("leftPanelNameColor", String.format("#%08X", leftPanelNameColor));

            json.addProperty("actionSearchBackgroundColor", String.format("#%08X", actionSearchBackgroundColor));
            json.addProperty("actionSearchBorderColor", String.format("#%08X", actionSearchBorderColor));
            json.addProperty("actionSearchTextColor", String.format("#%08X", actionSearchTextColor));
            json.addProperty("actionSearchPlaceholderColor", String.format("#%08X", actionSearchPlaceholderColor));

            json.addProperty("chunkMapLayoutPreset", chunkMapLayoutPreset);
            
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
        }
    }
    
    public int getPanelColor() {
        return panelColor;
    }

    public String getChunkMapLayoutPreset() {
        return chunkMapLayoutPreset;
    }

    public void setChunkMapLayoutPreset(String preset) {
        this.chunkMapLayoutPreset = (preset == null || preset.isBlank())
            ? DEFAULT_CHUNKMAP_LAYOUT_PRESET
            : preset;
        save();
    }
    
    public void setPanelColor(int color) {
        this.panelColor = color;
        save();
    }
    
    public int getBorderColor() {
        return borderColor;
    }
    
    public void setBorderColor(int color) {
        this.borderColor = color;
        save();
    }
    
    public int getDividerColor() {
        return dividerColor;
    }
    
    public void setDividerColor(int color) {
        this.dividerColor = color;
        save();
    }
    
    public int getFrameColor() {
        return frameColor;
    }
    
    public void setFrameColor(int color) {
        this.frameColor = color;
        save();
    }
    
    public int getScrollbarTrackColor() {
        return scrollbarTrackColor;
    }
    
    public void setScrollbarTrackColor(int color) {
        this.scrollbarTrackColor = color;
        save();
    }
    
    public int getScrollbarThumbColor() {
        return scrollbarThumbColor;
    }
    
    public void setScrollbarThumbColor(int color) {
        this.scrollbarThumbColor = color;
        save();
    }
    
    public int getLeftPanelTextColor() {
        return leftPanelTextColor;
    }
    
    public void setLeftPanelTextColor(int color) {
        this.leftPanelTextColor = color;
        save();
    }
    
    public int getLeftPanelValueColor() {
        return leftPanelValueColor;
    }
    
    public void setLeftPanelValueColor(int color) {
        this.leftPanelValueColor = color;
        save();
    }
    
    public int getLeftPanelNameColor() {
        return leftPanelNameColor;
    }
    
    public void setLeftPanelNameColor(int color) {
        this.leftPanelNameColor = color;
        save();
    }

    public int getActionSearchBackgroundColor() {
        return actionSearchBackgroundColor;
    }

    public void setActionSearchBackgroundColor(int color) {
        this.actionSearchBackgroundColor = color;
        save();
    }

    public int getActionSearchBorderColor() {
        return actionSearchBorderColor;
    }

    public void setActionSearchBorderColor(int color) {
        this.actionSearchBorderColor = color;
        save();
    }

    public int getActionSearchTextColor() {
        return actionSearchTextColor;
    }

    public void setActionSearchTextColor(int color) {
        this.actionSearchTextColor = color;
        save();
    }

    public int getActionSearchPlaceholderColor() {
        return actionSearchPlaceholderColor;
    }

    public void setActionSearchPlaceholderColor(int color) {
        this.actionSearchPlaceholderColor = color;
        save();
    }
    
    private static int parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return DEFAULT_PANEL_COLOR;
        }
        
        colorStr = colorStr.trim();
        if (colorStr.startsWith("#")) {
            colorStr = colorStr.substring(1);
        }
        
        if (colorStr.length() == 6) {
            return 0xFF000000 | Integer.parseInt(colorStr, 16);
        } else if (colorStr.length() == 8) {
            return (int) Long.parseLong(colorStr, 16);
        } else {
            throw new NumberFormatException("Invalid color format");
        }
    }
    
    public static int parseColorString(String colorStr) {
        return parseColor(colorStr);
    }
    
    public void resetToDefaults() {
        this.panelColor = DEFAULT_PANEL_COLOR;
        this.borderColor = DEFAULT_BORDER_COLOR;
        this.dividerColor = DEFAULT_DIVIDER_COLOR;
        this.frameColor = DEFAULT_FRAME_COLOR;
        this.scrollbarTrackColor = DEFAULT_SCROLLBAR_TRACK_COLOR;
        this.scrollbarThumbColor = DEFAULT_SCROLLBAR_THUMB_COLOR;
        this.leftPanelTextColor = DEFAULT_LEFT_PANEL_TEXT_COLOR;
        this.leftPanelValueColor = DEFAULT_LEFT_PANEL_VALUE_COLOR;
        this.leftPanelNameColor = DEFAULT_LEFT_PANEL_NAME_COLOR;

        this.actionSearchBackgroundColor = DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR;
        this.actionSearchBorderColor = DEFAULT_ACTION_SEARCH_BORDER_COLOR;
        this.actionSearchTextColor = DEFAULT_ACTION_SEARCH_TEXT_COLOR;
        this.actionSearchPlaceholderColor = DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR;
        save();
    }
}

