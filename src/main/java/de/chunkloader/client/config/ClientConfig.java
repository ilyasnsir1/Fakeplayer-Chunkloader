package de.chunkloader.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chunkloader.util.KeybindHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "chunkloader_client_config.json";
    private static final int DEFAULT_PANEL_COLOR = 0xFF2C2C2C;
    private static final int DEFAULT_BORDER_COLOR = 0xFF4A4A4A;
    private static final int DEFAULT_DIVIDER_COLOR = 0x33FFFFFF;
    private static final int DEFAULT_FRAME_COLOR = 0xFF111417;
    private static final int DEFAULT_SCROLLBAR_TRACK_COLOR = 0x33000000;
    private static final int DEFAULT_SCROLLBAR_THUMB_COLOR = 0xFFAAAAAA;
    private static final int DEFAULT_LEFT_PANEL_TEXT_COLOR = 0xCC808080;
    private static final int DEFAULT_LEFT_PANEL_VALUE_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_LEFT_PANEL_NAME_COLOR = 0xFFFFFFFF;

    private static final int DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR = 0xFF000000;
    private static final int DEFAULT_ACTION_SEARCH_BORDER_COLOR = DEFAULT_BORDER_COLOR;
    private static final int DEFAULT_ACTION_SEARCH_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR = DEFAULT_LEFT_PANEL_TEXT_COLOR;
    private static final int DEFAULT_COMPASS_DIRECTION_COLOR = 0xFFFFFFAA;

    private static final String DEFAULT_CHUNKMAP_LAYOUT_PRESET = "TOP";
    private static final String DEFAULT_DISABLED_CHUNKLOADERS_KEY = "F8";

    private int panelColor = DEFAULT_PANEL_COLOR;
    private int borderColor = DEFAULT_BORDER_COLOR;
    private int dividerColor = DEFAULT_DIVIDER_COLOR;
    private int frameColor = DEFAULT_FRAME_COLOR;
    private int scrollbarTrackColor = DEFAULT_SCROLLBAR_TRACK_COLOR;
    private int scrollbarThumbColor = DEFAULT_SCROLLBAR_THUMB_COLOR;
    private int leftPanelTextColor = DEFAULT_LEFT_PANEL_TEXT_COLOR;
    private int leftPanelValueColor = DEFAULT_LEFT_PANEL_VALUE_COLOR;
    private int leftPanelStatusColor = 0;
    private int leftPanelDimColor = 0;
    private int leftPanelNameColor = DEFAULT_LEFT_PANEL_NAME_COLOR;

    private int actionSearchBackgroundColor = DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR;
    private int actionSearchBorderColor = DEFAULT_ACTION_SEARCH_BORDER_COLOR;
    private int actionSearchTextColor = DEFAULT_ACTION_SEARCH_TEXT_COLOR;
    private int actionSearchPlaceholderColor = DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR;
    private int compassDirectionColor = DEFAULT_COMPASS_DIRECTION_COLOR;
    private int skinPanelColor = 0;
    private int skinBorderColor = 0;
    private int skinDividerColor = 0;
    private int skinTitleColor = 0;
    private int skinPlayerNameColor = 0;
    private int skinSearchbarBgColor = 0;
    private int skinSearchbarBorderColor = 0;
    private int skinSearchbarTextColor = 0;
    private int skinSearchbarPlaceholderColor = 0;
    private int skinViewportColor = 0;
    private int skinTextColor = 0;
    private int skinStatusSuccessColor = 0;
    private int skinStatusErrorColor = 0;
    private int skinStatusWarningColor = 0;
    private int skinLayerChevronBgColor = 0;
    private int skinLayerChevronColor = 0;
    private int skinLayerMenuBgColor = 0;
    private int skinLayerActiveColor = 0;
    private int skinLayerInactiveColor = 0;
    private final EnumMap<PanelColorTarget, Integer> colorPreviewOverrides = new EnumMap<>(PanelColorTarget.class);

    private String chunkMapLayoutPreset = DEFAULT_CHUNKMAP_LAYOUT_PRESET;
    private String disabledChunkloadersKeyName = DEFAULT_DISABLED_CHUNKLOADERS_KEY;
    private boolean hideOtherDots = false;
    private final Map<String, Integer> chunkMapRotationByKey = new HashMap<>();
    private Path configPath;

    private ClientConfig() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gameDirectory != null) {
            Path configDir = client.gameDirectory.toPath().resolve("config");
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

    private static ClientConfig INSTANCE;

    public static ClientConfig load() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        ClientConfig config = new ClientConfig();
        if (config.configPath == null) {
            INSTANCE = config;
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
                if (json.has("leftPanelStatusColor")) {
                    try {
                        config.leftPanelStatusColor = parseColor(json.get("leftPanelStatusColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.leftPanelStatusColor = 0;
                    }
                }
                if (json.has("leftPanelDimColor")) {
                    try {
                        config.leftPanelDimColor = parseColor(json.get("leftPanelDimColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.leftPanelDimColor = 0;
                    }
                }
                if (json.has("compassDirectionColor")) {
                    try {
                        config.compassDirectionColor = parseColor(json.get("compassDirectionColor").getAsString());
                    } catch (NumberFormatException e) {
                        config.compassDirectionColor = DEFAULT_COMPASS_DIRECTION_COLOR;
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

                if (json.has("skinPanelColor")) {
                    try { config.skinPanelColor = parseColor(json.get("skinPanelColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinBorderColor")) {
                    try { config.skinBorderColor = parseColor(json.get("skinBorderColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinDividerColor")) {
                    try { config.skinDividerColor = parseColor(json.get("skinDividerColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinTitleColor")) {
                    try { config.skinTitleColor = parseColor(json.get("skinTitleColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinPlayerNameColor")) {
                    try { config.skinPlayerNameColor = parseColor(json.get("skinPlayerNameColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinSearchbarBgColor")) {
                    try { config.skinSearchbarBgColor = parseColor(json.get("skinSearchbarBgColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinSearchbarBorderColor")) {
                    try { config.skinSearchbarBorderColor = parseColor(json.get("skinSearchbarBorderColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinSearchbarTextColor")) {
                    try { config.skinSearchbarTextColor = parseColor(json.get("skinSearchbarTextColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinSearchbarPlaceholderColor")) {
                    try { config.skinSearchbarPlaceholderColor = parseColor(json.get("skinSearchbarPlaceholderColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinViewportColor")) {
                    try { config.skinViewportColor = parseColor(json.get("skinViewportColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinTextColor")) {
                    try { config.skinTextColor = parseColor(json.get("skinTextColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinStatusSuccessColor")) {
                    try { config.skinStatusSuccessColor = parseColor(json.get("skinStatusSuccessColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinStatusErrorColor")) {
                    try { config.skinStatusErrorColor = parseColor(json.get("skinStatusErrorColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinStatusWarningColor")) {
                    try { config.skinStatusWarningColor = parseColor(json.get("skinStatusWarningColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinLayerChevronBgColor")) {
                    try { config.skinLayerChevronBgColor = parseColor(json.get("skinLayerChevronBgColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinLayerChevronColor")) {
                    try { config.skinLayerChevronColor = parseColor(json.get("skinLayerChevronColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinLayerMenuBgColor")) {
                    try { config.skinLayerMenuBgColor = parseColor(json.get("skinLayerMenuBgColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinLayerActiveColor")) {
                    try { config.skinLayerActiveColor = parseColor(json.get("skinLayerActiveColor").getAsString()); } catch (Exception ignored) {}
                }
                if (json.has("skinLayerInactiveColor")) {
                    try { config.skinLayerInactiveColor = parseColor(json.get("skinLayerInactiveColor").getAsString()); } catch (Exception ignored) {}
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
                    try {
                        boolean left = json.get("chunkMapLeftButtonBar").getAsBoolean();
                        config.chunkMapLayoutPreset = left ? "LEFT" : DEFAULT_CHUNKMAP_LAYOUT_PRESET;
                    } catch (Exception e) {
                        config.chunkMapLayoutPreset = DEFAULT_CHUNKMAP_LAYOUT_PRESET;
                    }
                }
                if (json.has("disabledChunkloadersKeyName")) {
                    try {
                        String key = json.get("disabledChunkloadersKeyName").getAsString();
                        config.disabledChunkloadersKeyName = (key != null && !key.isBlank()) ? key : DEFAULT_DISABLED_CHUNKLOADERS_KEY;
                    } catch (Exception e) {
                        config.disabledChunkloadersKeyName = DEFAULT_DISABLED_CHUNKLOADERS_KEY;
                    }
                }
                if (json.has("hideOtherDots")) {
                    try {
                        config.hideOtherDots = json.get("hideOtherDots").getAsBoolean();
                    } catch (Exception e) {
                        config.hideOtherDots = false;
                    }
                }
                if (json.has("chunkMapRotationByKey") && json.get("chunkMapRotationByKey").isJsonObject()) {
                    try {
                        JsonObject rotations = json.getAsJsonObject("chunkMapRotationByKey");
                        for (Map.Entry<String, com.google.gson.JsonElement> entry : rotations.entrySet()) {
                            try {
                                int rotation = entry.getValue().getAsInt();
                                if (rotation >= 0 && rotation <= 3) {
                                    config.chunkMapRotationByKey.put(entry.getKey(), rotation);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
            }
        } else {
            config.save();
        }
        KeybindHelper.setDisabledChunkloadersKeyName(config.getDisabledChunkloadersKeyName());
        INSTANCE = config;
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
            json.addProperty("leftPanelStatusColor", String.format("#%08X", leftPanelStatusColor));
            json.addProperty("leftPanelDimColor", String.format("#%08X", leftPanelDimColor));

            json.addProperty("actionSearchBackgroundColor", String.format("#%08X", actionSearchBackgroundColor));
            json.addProperty("actionSearchBorderColor", String.format("#%08X", actionSearchBorderColor));
            json.addProperty("actionSearchTextColor", String.format("#%08X", actionSearchTextColor));
            json.addProperty("actionSearchPlaceholderColor", String.format("#%08X", actionSearchPlaceholderColor));
            json.addProperty("compassDirectionColor", String.format("#%08X", compassDirectionColor));

            json.addProperty("skinPanelColor", String.format("#%08X", skinPanelColor));
            json.addProperty("skinBorderColor", String.format("#%08X", skinBorderColor));
            json.addProperty("skinDividerColor", String.format("#%08X", skinDividerColor));
            json.addProperty("skinTitleColor", String.format("#%08X", skinTitleColor));
            json.addProperty("skinPlayerNameColor", String.format("#%08X", skinPlayerNameColor));
            json.addProperty("skinSearchbarBgColor", String.format("#%08X", skinSearchbarBgColor));
            json.addProperty("skinSearchbarBorderColor", String.format("#%08X", skinSearchbarBorderColor));
            json.addProperty("skinSearchbarTextColor", String.format("#%08X", skinSearchbarTextColor));
            json.addProperty("skinSearchbarPlaceholderColor", String.format("#%08X", skinSearchbarPlaceholderColor));
            json.addProperty("skinViewportColor", String.format("#%08X", skinViewportColor));
            json.addProperty("skinTextColor", String.format("#%08X", skinTextColor));
            json.addProperty("skinStatusSuccessColor", String.format("#%08X", skinStatusSuccessColor));
            json.addProperty("skinStatusErrorColor", String.format("#%08X", skinStatusErrorColor));
            json.addProperty("skinStatusWarningColor", String.format("#%08X", skinStatusWarningColor));
            json.addProperty("skinLayerChevronBgColor", String.format("#%08X", skinLayerChevronBgColor));
            json.addProperty("skinLayerChevronColor", String.format("#%08X", skinLayerChevronColor));
            json.addProperty("skinLayerMenuBgColor", String.format("#%08X", skinLayerMenuBgColor));
            json.addProperty("skinLayerActiveColor", String.format("#%08X", skinLayerActiveColor));
            json.addProperty("skinLayerInactiveColor", String.format("#%08X", skinLayerInactiveColor));

            json.addProperty("chunkMapLayoutPreset", chunkMapLayoutPreset);
            json.addProperty("disabledChunkloadersKeyName", disabledChunkloadersKeyName);
            json.addProperty("hideOtherDots", hideOtherDots);
            JsonObject rotations = new JsonObject();
            for (Map.Entry<String, Integer> entry : chunkMapRotationByKey.entrySet()) {
                int rotation = entry.getValue() == null ? 0 : Math.max(0, Math.min(3, entry.getValue()));
                rotations.addProperty(entry.getKey(), rotation);
            }
            json.add("chunkMapRotationByKey", rotations);

            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
        }
    }

    public int getPanelColor() {
        return getColorWithPreview(PanelColorTarget.PANEL, panelColor);
    }

    public String getChunkMapLayoutPreset() {
        return chunkMapLayoutPreset;
    }

    public String getDisabledChunkloadersKeyName() {
        return disabledChunkloadersKeyName;
    }

    public void setDisabledChunkloadersKeyName(String keyName) {
        this.disabledChunkloadersKeyName = (keyName != null && !keyName.isBlank()) ? keyName : DEFAULT_DISABLED_CHUNKLOADERS_KEY;
        save();
    }

    public boolean isHideOtherDots() {
        return hideOtherDots;
    }

    public void setHideOtherDots(boolean hideOtherDots) {
        this.hideOtherDots = hideOtherDots;
        save();
    }

    public boolean toggleHideOtherDots() {
        this.hideOtherDots = !this.hideOtherDots;
        save();
        return this.hideOtherDots;
    }

    public void setChunkMapLayoutPreset(String preset) {
        this.chunkMapLayoutPreset = (preset == null || preset.isBlank())
            ? DEFAULT_CHUNKMAP_LAYOUT_PRESET
            : preset;
        save();
    }

    public int getChunkMapRotation(String key, int fallbackRotation) {
        if (key == null || key.isBlank()) {
            return Math.max(0, Math.min(3, fallbackRotation));
        }
        Integer rotation = chunkMapRotationByKey.get(key);
        if (rotation == null) {
            return Math.max(0, Math.min(3, fallbackRotation));
        }
        return Math.max(0, Math.min(3, rotation));
    }

    public void setChunkMapRotation(String key, int rotation) {
        if (key == null || key.isBlank()) {
            return;
        }
        chunkMapRotationByKey.put(key, Math.max(0, Math.min(3, rotation)));
        save();
    }

    public void setPanelColor(int color) {
        this.panelColor = color;
        save();
    }

    public int getBorderColor() {
        return getColorWithPreview(PanelColorTarget.BORDER, borderColor);
    }

    public void setBorderColor(int color) {
        this.borderColor = color;
        save();
    }

    public int getDividerColor() {
        return getColorWithPreview(PanelColorTarget.DIVIDER, dividerColor);
    }

    public void setDividerColor(int color) {
        this.dividerColor = color;
        save();
    }

    public int getFrameColor() {
        return getColorWithPreview(PanelColorTarget.FRAME, frameColor);
    }

    public void setFrameColor(int color) {
        this.frameColor = color;
        save();
    }

    public int getScrollbarTrackColor() {
        return getColorWithPreview(PanelColorTarget.SCROLLBAR_TRACK, scrollbarTrackColor);
    }

    public void setScrollbarTrackColor(int color) {
        this.scrollbarTrackColor = color;
        save();
    }

    public int getScrollbarThumbColor() {
        return getColorWithPreview(PanelColorTarget.SCROLLBAR_THUMB, scrollbarThumbColor);
    }

    public void setScrollbarThumbColor(int color) {
        this.scrollbarThumbColor = color;
        save();
    }

    public int getLeftPanelTextColor() {
        return getColorWithPreview(PanelColorTarget.LEFT_PANEL_TEXT, leftPanelTextColor);
    }

    public void setLeftPanelTextColor(int color) {
        this.leftPanelTextColor = color;
        save();
    }

    public int getLeftPanelValueColor() {
        return getColorWithPreview(PanelColorTarget.LEFT_PANEL_VALUE, leftPanelValueColor);
    }

    public void setLeftPanelValueColor(int color) {
        this.leftPanelValueColor = color;
        save();
    }

    public int getLeftPanelStatusColor() {
        return getColorWithPreview(PanelColorTarget.LEFT_PANEL_STATUS, leftPanelStatusColor);
    }

    public void setLeftPanelStatusColor(int color) {
        this.leftPanelStatusColor = color;
        save();
    }

    public int getLeftPanelDimColor() {
        return getColorWithPreview(PanelColorTarget.LEFT_PANEL_DIM, leftPanelDimColor);
    }

    public void setLeftPanelDimColor(int color) {
        this.leftPanelDimColor = color;
        save();
    }

    public int getLeftPanelNameColor() {
        return getColorWithPreview(PanelColorTarget.LEFT_PANEL_NAME, leftPanelNameColor);
    }

    public void setLeftPanelNameColor(int color) {
        this.leftPanelNameColor = color;
        save();
    }

    public int getActionSearchBackgroundColor() {
        return getColorWithPreview(PanelColorTarget.SEARCHBAR_BACKGROUND, actionSearchBackgroundColor);
    }

    public void setActionSearchBackgroundColor(int color) {
        this.actionSearchBackgroundColor = color;
        save();
    }

    public int getActionSearchBorderColor() {
        return getColorWithPreview(PanelColorTarget.SEARCHBAR_BORDER, actionSearchBorderColor);
    }

    public void setActionSearchBorderColor(int color) {
        this.actionSearchBorderColor = color;
        save();
    }

    public int getActionSearchTextColor() {
        return getColorWithPreview(PanelColorTarget.SEARCHBAR_TEXT, actionSearchTextColor);
    }

    public void setActionSearchTextColor(int color) {
        this.actionSearchTextColor = color;
        save();
    }

    public int getActionSearchPlaceholderColor() {
        return getColorWithPreview(PanelColorTarget.SEARCHBAR_PLACEHOLDER, actionSearchPlaceholderColor);
    }

    public void setActionSearchPlaceholderColor(int color) {
        this.actionSearchPlaceholderColor = color;
        save();
    }

    public int getCompassDirectionColor() {
        return getColorWithPreview(PanelColorTarget.COMPASS_DIRECTION, compassDirectionColor);
    }

    public void setCompassDirectionColor(int color) {
        this.compassDirectionColor = color;
        save();
    }

    public int getSkinPanelColor() {
        int base = skinPanelColor != 0 ? skinPanelColor : getPanelColor();
        return getColorWithPreview(PanelColorTarget.SKIN_PANEL, base);
    }

    public void setSkinPanelColor(int color) {
        this.skinPanelColor = color;
        save();
    }

    public int getSkinBorderColor() {
        int base = skinBorderColor != 0 ? skinBorderColor : getBorderColor();
        return getColorWithPreview(PanelColorTarget.SKIN_BORDER, base);
    }

    public void setSkinBorderColor(int color) {
        this.skinBorderColor = color;
        save();
    }

    public int getSkinDividerColor() {
        int base = skinDividerColor != 0 ? skinDividerColor : getDividerColor();
        return getColorWithPreview(PanelColorTarget.SKIN_DIVIDER, base);
    }

    public void setSkinDividerColor(int color) {
        this.skinDividerColor = color;
        save();
    }

    public int getSkinTitleColor() {
        int base = skinTitleColor != 0 ? skinTitleColor : 0xFFFFFFFF;
        return getColorWithPreview(PanelColorTarget.SKIN_TITLE, base);
    }

    public void setSkinTitleColor(int color) {
        this.skinTitleColor = color;
        save();
    }

    public int getSkinPlayerNameColor() {
        int base = skinPlayerNameColor != 0 ? skinPlayerNameColor : getLeftPanelValueColor();
        return getColorWithPreview(PanelColorTarget.SKIN_PLAYER_NAME, base);
    }

    public void setSkinPlayerNameColor(int color) {
        this.skinPlayerNameColor = color;
        save();
    }

    public int getSkinSearchbarBgColor() {
        int base = skinSearchbarBgColor != 0 ? skinSearchbarBgColor : 0xFF0A0D10;
        return getColorWithPreview(PanelColorTarget.SKIN_SEARCHBAR_BG, base);
    }

    public void setSkinSearchbarBgColor(int color) {
        this.skinSearchbarBgColor = color;
        save();
    }

    public int getSkinSearchbarBorderColor() {
        int base = skinSearchbarBorderColor != 0 ? skinSearchbarBorderColor : getActionSearchBorderColor();
        return getColorWithPreview(PanelColorTarget.SKIN_SEARCHBAR_BORDER, base);
    }

    public void setSkinSearchbarBorderColor(int color) {
        this.skinSearchbarBorderColor = color;
        save();
    }

    public int getSkinSearchbarTextColor() {
        int base = skinSearchbarTextColor != 0 ? skinSearchbarTextColor : getActionSearchTextColor();
        return getColorWithPreview(PanelColorTarget.SKIN_SEARCHBAR_TEXT, base);
    }

    public void setSkinSearchbarTextColor(int color) {
        this.skinSearchbarTextColor = color;
        save();
    }

    public int getSkinSearchbarPlaceholderColor() {
        int base = skinSearchbarPlaceholderColor != 0 ? skinSearchbarPlaceholderColor : getActionSearchPlaceholderColor();
        return getColorWithPreview(PanelColorTarget.SKIN_SEARCHBAR_PLACEHOLDER, base);
    }

    public void setSkinSearchbarPlaceholderColor(int color) {
        this.skinSearchbarPlaceholderColor = color;
        save();
    }

    public int getSkinViewportColor() {
        int base = skinViewportColor != 0 ? skinViewportColor : getFrameColor();
        return getColorWithPreview(PanelColorTarget.SKIN_VIEWPORT, base);
    }

    public void setSkinViewportColor(int color) {
        this.skinViewportColor = color;
        save();
    }

    public int getSkinTextColor() {
        int base = skinTextColor != 0 ? skinTextColor : getLeftPanelTextColor();
        return getColorWithPreview(PanelColorTarget.SKIN_TEXT, base);
    }

    public void setSkinTextColor(int color) {
        this.skinTextColor = color;
        save();
    }

    public int getSkinStatusSuccessColor() {
        int base = skinStatusSuccessColor != 0 ? skinStatusSuccessColor : 0xFF55FF55;
        return getColorWithPreview(PanelColorTarget.SKIN_STATUS_SUCCESS, base);
    }

    public void setSkinStatusSuccessColor(int color) {
        this.skinStatusSuccessColor = color;
        save();
    }

    public int getSkinStatusErrorColor() {
        int base = skinStatusErrorColor != 0 ? skinStatusErrorColor : 0xFFFF7777;
        return getColorWithPreview(PanelColorTarget.SKIN_STATUS_ERROR, base);
    }

    public void setSkinStatusErrorColor(int color) {
        this.skinStatusErrorColor = color;
        save();
    }

    public int getSkinStatusWarningColor() {
        int base = skinStatusWarningColor != 0 ? skinStatusWarningColor : 0xFFFFCC66;
        return getColorWithPreview(PanelColorTarget.SKIN_STATUS_WARNING, base);
    }

    public void setSkinStatusWarningColor(int color) {
        this.skinStatusWarningColor = color;
        save();
    }

    public int getSkinLayerChevronBgColor() {
        int base = skinLayerChevronBgColor != 0 ? skinLayerChevronBgColor : 0x99000000;
        return getColorWithPreview(PanelColorTarget.SKIN_LAYER_CHEVRON_BG, base);
    }

    public void setSkinLayerChevronBgColor(int color) {
        this.skinLayerChevronBgColor = color;
        save();
    }

    public int getSkinLayerChevronColor() {
        int base = skinLayerChevronColor != 0 ? skinLayerChevronColor : 0xFFFFFFFF;
        return getColorWithPreview(PanelColorTarget.SKIN_LAYER_CHEVRON, base);
    }

    public void setSkinLayerChevronColor(int color) {
        this.skinLayerChevronColor = color;
        save();
    }

    public int getSkinLayerMenuBgColor() {
        int base = skinLayerMenuBgColor != 0 ? skinLayerMenuBgColor : 0xCC0A0D10;
        return getColorWithPreview(PanelColorTarget.SKIN_LAYER_MENU_BG, base);
    }

    public void setSkinLayerMenuBgColor(int color) {
        this.skinLayerMenuBgColor = color;
        save();
    }

    public int getSkinLayerActiveColor() {
        int base = skinLayerActiveColor != 0 ? skinLayerActiveColor : 0xFFFFFFFF;
        return getColorWithPreview(PanelColorTarget.SKIN_LAYER_ACTIVE, base);
    }

    public void setSkinLayerActiveColor(int color) {
        this.skinLayerActiveColor = color;
        save();
    }

    public int getSkinLayerInactiveColor() {
        int base = skinLayerInactiveColor != 0 ? skinLayerInactiveColor : 0xCC808080;
        return getColorWithPreview(PanelColorTarget.SKIN_LAYER_INACTIVE, base);
    }

    public void setSkinLayerInactiveColor(int color) {
        this.skinLayerInactiveColor = color;
        save();
    }

    public Map<PanelColorTarget, Integer> getStoredPanelColors() {
        EnumMap<PanelColorTarget, Integer> colors = new EnumMap<>(PanelColorTarget.class);
        for (PanelColorTarget target : PanelColorTarget.values()) {
            colors.put(target, getStoredPanelColor(target));
        }
        return Map.copyOf(colors);
    }

    public void setColorPreviewOverrides(Map<PanelColorTarget, Integer> overrides) {
        colorPreviewOverrides.clear();
        if (overrides == null) {
            return;
        }

        for (PanelColorTarget target : PanelColorTarget.values()) {
            Integer color = overrides.get(target);
            if (color != null) {
                colorPreviewOverrides.put(target, color);
            }
        }
    }

    public void clearColorPreviewOverrides() {
        colorPreviewOverrides.clear();
    }

    public void savePanelColors(Map<PanelColorTarget, Integer> colors) {
        if (colors == null) {
            return;
        }

        for (PanelColorTarget target : PanelColorTarget.values()) {
            Integer color = colors.get(target);
            if (color != null) {
                setStoredPanelColor(target, color);
            }
        }
        save();
    }

    private int getStoredPanelColor(PanelColorTarget target) {
        return switch (target) {
            case PANEL -> panelColor;
            case BORDER -> borderColor;
            case DIVIDER -> dividerColor;
            case FRAME -> frameColor;
            case SCROLLBAR_TRACK -> scrollbarTrackColor;
            case SCROLLBAR_THUMB -> scrollbarThumbColor;
            case LEFT_PANEL_TEXT -> leftPanelTextColor;
            case LEFT_PANEL_VALUE -> leftPanelValueColor;
            case LEFT_PANEL_STATUS -> leftPanelStatusColor != 0 ? leftPanelStatusColor : 0xFF55FF55;
            case LEFT_PANEL_DIM -> leftPanelDimColor != 0 ? leftPanelDimColor : 0xFF55FF55;
            case LEFT_PANEL_NAME -> leftPanelNameColor;
            case SEARCHBAR_BACKGROUND -> actionSearchBackgroundColor;
            case SEARCHBAR_BORDER -> actionSearchBorderColor;
            case SEARCHBAR_TEXT -> actionSearchTextColor;
            case SEARCHBAR_PLACEHOLDER -> actionSearchPlaceholderColor;
            case COMPASS_DIRECTION -> compassDirectionColor;
            case SKIN_PANEL -> skinPanelColor != 0 ? skinPanelColor : panelColor;
            case SKIN_BORDER -> skinBorderColor != 0 ? skinBorderColor : borderColor;
            case SKIN_DIVIDER -> skinDividerColor != 0 ? skinDividerColor : dividerColor;
            case SKIN_TITLE -> skinTitleColor != 0 ? skinTitleColor : 0xFFFFFFFF;
            case SKIN_PLAYER_NAME -> skinPlayerNameColor != 0 ? skinPlayerNameColor : leftPanelValueColor;
            case SKIN_SEARCHBAR_BG -> skinSearchbarBgColor != 0 ? skinSearchbarBgColor : 0xFF0A0D10;
            case SKIN_SEARCHBAR_BORDER -> skinSearchbarBorderColor != 0 ? skinSearchbarBorderColor : actionSearchBorderColor;
            case SKIN_SEARCHBAR_TEXT -> skinSearchbarTextColor != 0 ? skinSearchbarTextColor : actionSearchTextColor;
            case SKIN_SEARCHBAR_PLACEHOLDER -> skinSearchbarPlaceholderColor != 0 ? skinSearchbarPlaceholderColor : actionSearchPlaceholderColor;
            case SKIN_VIEWPORT -> skinViewportColor != 0 ? skinViewportColor : frameColor;
            case SKIN_TEXT -> skinTextColor != 0 ? skinTextColor : leftPanelTextColor;
            case SKIN_STATUS_SUCCESS -> skinStatusSuccessColor != 0 ? skinStatusSuccessColor : 0xFF55FF55;
            case SKIN_STATUS_ERROR -> skinStatusErrorColor != 0 ? skinStatusErrorColor : 0xFFFF7777;
            case SKIN_STATUS_WARNING -> skinStatusWarningColor != 0 ? skinStatusWarningColor : 0xFFFFCC66;
            case SKIN_LAYER_CHEVRON_BG -> skinLayerChevronBgColor != 0 ? skinLayerChevronBgColor : 0x99000000;
            case SKIN_LAYER_CHEVRON -> skinLayerChevronColor != 0 ? skinLayerChevronColor : 0xFFFFFFFF;
            case SKIN_LAYER_MENU_BG -> skinLayerMenuBgColor != 0 ? skinLayerMenuBgColor : 0xCC0A0D10;
            case SKIN_LAYER_ACTIVE -> skinLayerActiveColor != 0 ? skinLayerActiveColor : 0xFFFFFFFF;
            case SKIN_LAYER_INACTIVE -> skinLayerInactiveColor != 0 ? skinLayerInactiveColor : 0xCC808080;
        };
    }

    private void setStoredPanelColor(PanelColorTarget target, int color) {
        switch (target) {
            case PANEL -> panelColor = color;
            case BORDER -> borderColor = color;
            case DIVIDER -> dividerColor = color;
            case FRAME -> frameColor = color;
            case SCROLLBAR_TRACK -> scrollbarTrackColor = color;
            case SCROLLBAR_THUMB -> scrollbarThumbColor = color;
            case LEFT_PANEL_TEXT -> leftPanelTextColor = color;
            case LEFT_PANEL_VALUE -> leftPanelValueColor = color;
            case LEFT_PANEL_STATUS -> leftPanelStatusColor = color;
            case LEFT_PANEL_DIM -> leftPanelDimColor = color;
            case LEFT_PANEL_NAME -> leftPanelNameColor = color;
            case SEARCHBAR_BACKGROUND -> actionSearchBackgroundColor = color;
            case SEARCHBAR_BORDER -> actionSearchBorderColor = color;
            case SEARCHBAR_TEXT -> actionSearchTextColor = color;
            case SEARCHBAR_PLACEHOLDER -> actionSearchPlaceholderColor = color;
            case COMPASS_DIRECTION -> compassDirectionColor = color;
            case SKIN_PANEL -> skinPanelColor = color;
            case SKIN_BORDER -> skinBorderColor = color;
            case SKIN_DIVIDER -> skinDividerColor = color;
            case SKIN_TITLE -> skinTitleColor = color;
            case SKIN_PLAYER_NAME -> skinPlayerNameColor = color;
            case SKIN_SEARCHBAR_BG -> skinSearchbarBgColor = color;
            case SKIN_SEARCHBAR_BORDER -> skinSearchbarBorderColor = color;
            case SKIN_SEARCHBAR_TEXT -> skinSearchbarTextColor = color;
            case SKIN_SEARCHBAR_PLACEHOLDER -> skinSearchbarPlaceholderColor = color;
            case SKIN_VIEWPORT -> skinViewportColor = color;
            case SKIN_TEXT -> skinTextColor = color;
            case SKIN_STATUS_SUCCESS -> skinStatusSuccessColor = color;
            case SKIN_STATUS_ERROR -> skinStatusErrorColor = color;
            case SKIN_STATUS_WARNING -> skinStatusWarningColor = color;
            case SKIN_LAYER_CHEVRON_BG -> skinLayerChevronBgColor = color;
            case SKIN_LAYER_CHEVRON -> skinLayerChevronColor = color;
            case SKIN_LAYER_MENU_BG -> skinLayerMenuBgColor = color;
            case SKIN_LAYER_ACTIVE -> skinLayerActiveColor = color;
            case SKIN_LAYER_INACTIVE -> skinLayerInactiveColor = color;
        }
    }

    private int getColorWithPreview(PanelColorTarget target, int storedColor) {
        return colorPreviewOverrides.getOrDefault(target, storedColor);
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
        clearColorPreviewOverrides();
        this.panelColor = DEFAULT_PANEL_COLOR;
        this.borderColor = DEFAULT_BORDER_COLOR;
        this.dividerColor = DEFAULT_DIVIDER_COLOR;
        this.frameColor = DEFAULT_FRAME_COLOR;
        this.scrollbarTrackColor = DEFAULT_SCROLLBAR_TRACK_COLOR;
        this.scrollbarThumbColor = DEFAULT_SCROLLBAR_THUMB_COLOR;
        this.leftPanelTextColor = DEFAULT_LEFT_PANEL_TEXT_COLOR;
        this.leftPanelValueColor = DEFAULT_LEFT_PANEL_VALUE_COLOR;
        this.leftPanelNameColor = DEFAULT_LEFT_PANEL_NAME_COLOR;
        this.leftPanelStatusColor = 0;
        this.leftPanelDimColor = 0;

        this.actionSearchBackgroundColor = DEFAULT_ACTION_SEARCH_BACKGROUND_COLOR;
        this.actionSearchBorderColor = DEFAULT_ACTION_SEARCH_BORDER_COLOR;
        this.actionSearchTextColor = DEFAULT_ACTION_SEARCH_TEXT_COLOR;
        this.actionSearchPlaceholderColor = DEFAULT_ACTION_SEARCH_PLACEHOLDER_COLOR;
        this.compassDirectionColor = DEFAULT_COMPASS_DIRECTION_COLOR;
        this.skinPanelColor = 0;
        this.skinBorderColor = 0;
        this.skinDividerColor = 0;
        this.skinTitleColor = 0;
        this.skinPlayerNameColor = 0;
        this.skinSearchbarBgColor = 0;
        this.skinSearchbarBorderColor = 0;
        this.skinSearchbarTextColor = 0;
        this.skinSearchbarPlaceholderColor = 0;
        this.skinViewportColor = 0;
        this.skinTextColor = 0;
        this.skinStatusSuccessColor = 0;
        this.skinStatusErrorColor = 0;
        this.skinStatusWarningColor = 0;
        this.skinLayerChevronBgColor = 0;
        this.skinLayerChevronColor = 0;
        this.skinLayerMenuBgColor = 0;
        this.skinLayerActiveColor = 0;
        this.skinLayerInactiveColor = 0;
        this.disabledChunkloadersKeyName = DEFAULT_DISABLED_CHUNKLOADERS_KEY;
        this.hideOtherDots = false;
        save();
    }
}
