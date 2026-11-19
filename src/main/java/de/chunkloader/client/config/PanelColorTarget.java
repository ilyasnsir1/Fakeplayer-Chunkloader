package de.chunkloader.client.config;

public enum PanelColorTarget {
    PANEL("Panel Background", 0xFF2C2C2C, 1),
    BORDER("Panel Border", 0xFF4A4A4A, 1),
    DIVIDER("Divider Lines", 0x33FFFFFF, 1),
    FRAME("Chunkmap Frame", 0xFF111417, 1),
    SCROLLBAR_TRACK("Scrollbar Track", 0x33000000, 1),
    SCROLLBAR_THUMB("Scrollbar Thumb", 0xFFAAAAAA, 1),
    LEFT_PANEL_TEXT("Labels", 0xCC808080, 1),
    LEFT_PANEL_VALUE("Values", 0xFFFFFFFF, 1),
    LEFT_PANEL_STATUS("Status", 0xFF55FF55, 1),
    LEFT_PANEL_DIM("Dimension", 0xFF55FF55, 1),
    LEFT_PANEL_NAME("Player Name", 0xFFFFFFFF, 1),
    SEARCHBAR_BACKGROUND("Search Bar Background", 0xFF000000, 1),
    SEARCHBAR_BORDER("Search Bar Border", 0xFF4A4A4A, 1),
    SEARCHBAR_TEXT("Search Bar Text", 0xFFFFFFFF, 1),
    SEARCHBAR_PLACEHOLDER("Search Bar Placeholder", 0xCC808080, 1),
    COMPASS_DIRECTION("Compass Direction", 0xFFFFFFAA, 1),

    SKIN_PANEL("Skin Panel Background", 0xFF2C2C2C, 2),
    SKIN_BORDER("Skin Panel Border", 0xFF4A4A4A, 2),
    SKIN_DIVIDER("Skin Divider Lines", 0x33FFFFFF, 2),
    SKIN_TITLE("Skin Header Title", 0xFFFFFFFF, 2),
    SKIN_PLAYER_NAME("Skin Player Name", 0xFFFFFFFF, 2),
    SKIN_SEARCHBAR_BG("Skin Path Background", 0xFF0A0D10, 2),
    SKIN_SEARCHBAR_BORDER("Skin Path Border", 0xFF4A4A4A, 2),
    SKIN_SEARCHBAR_TEXT("Skin Path Text", 0xFFFFFFFF, 2),
    SKIN_SEARCHBAR_PLACEHOLDER("Skin Path Placeholder", 0xCC808080, 2),
    SKIN_VIEWPORT("Skin Viewport", 0xFF111417, 2),
    SKIN_TEXT("Skin Screen Text", 0xCC808080, 2),
    SKIN_STATUS_SUCCESS("Skin Status Success", 0xFF55FF55, 2),
    SKIN_STATUS_ERROR("Skin Status Error", 0xFFFF7777, 2),
    SKIN_STATUS_WARNING("Skin Status Warning", 0xFFFFCC66, 2),
    SKIN_LAYER_CHEVRON_BG("Skin Layer Chevron Background", 0x99000000, 2),
    SKIN_LAYER_CHEVRON("Skin Layer Chevron", 0xFFFFFFFF, 2),
    SKIN_LAYER_MENU_BG("Skin Layer Menu Background", 0xCC0A0D10, 2),
    SKIN_LAYER_ACTIVE("Skin Layer Active Text", 0xFFFFFFFF, 2),
    SKIN_LAYER_INACTIVE("Skin Layer Inactive Text", 0xCC808080, 2);

    private final String displayName;
    private final int defaultColor;
    private final int page;

    PanelColorTarget(String displayName, int defaultColor, int page) {
        this.displayName = displayName;
        this.defaultColor = defaultColor;
        this.page = page;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public int getPage() {
        return page;
    }
}
