package de.chunkloader.client.hud;

import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
public class SimulationStatusHUD {

    private static boolean enabled = false;
    private static SimulationStatusResponsePayload lastStatus = null;
    private static long lastUpdateTime = 0;
    private static boolean requestPending = false;
    private static long requestPendingTime = 0;
    private static final long REQUEST_TIMEOUT_MS = 2000;
    private static final long UPDATE_INTERVAL_MS_CLOSE = 250;
    private static final long UPDATE_INTERVAL_MS_FAR = 1000;

    private static long getUpdateInterval(int distance) {
        if (distance < 0) {
            return UPDATE_INTERVAL_MS_CLOSE;
        }
        if (distance <= 5) {
            return UPDATE_INTERVAL_MS_CLOSE;
        }
        return UPDATE_INTERVAL_MS_FAR;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            lastStatus = null;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void updateStatus(SimulationStatusResponsePayload status) {
        lastStatus = status;
        lastUpdateTime = System.currentTimeMillis();
        requestPending = false;
    }

    public static void setRequestPending(boolean pending) {
        requestPending = pending;
        if (pending) {
            requestPendingTime = System.currentTimeMillis();
        }
    }

    public static boolean isRequestPending() {
        long now = System.currentTimeMillis();
        if (requestPending && (now - requestPendingTime) > REQUEST_TIMEOUT_MS) {
            requestPending = false;
        }
        return requestPending;
    }

    public static void extractRenderState(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight) {
        if (!enabled || lastStatus == null) {
            return;
        }

        int padding = 5;
        int x = padding;
        int y = padding;

        int boxWidth = 200;
        int boxHeight = 60;

        int lineHeight = 12;
        int contentHeight = lineHeight;
        if (lastStatus.inSimulatedChunk()) {
            contentHeight += lineHeight;
            contentHeight += lineHeight;
            contentHeight += lineHeight;
            if (lastStatus.distance() >= 0) {
                contentHeight += lineHeight;
            }
        }
        boxHeight = contentHeight + padding * 2;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);

        context.fill(x, y, x + boxWidth, y + 1, 0xFF000000);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF000000);
        context.fill(x, y, x + 1, y + boxHeight, 0xFF000000);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF000000);

        int borderColor = lastStatus.inSimulatedChunk() ? 0xFF00FF00 : 0xFFFF0000;
        context.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, borderColor);
        context.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, borderColor);
        context.fill(x + 1, y + 1, x + 2, y + boxHeight - 1, borderColor);
        context.fill(x + boxWidth - 2, y + 1, x + boxWidth - 1, y + boxHeight - 1, borderColor);

        int textX = x + padding;
        int textY = y + padding;

        boolean isUpdating = isRequestPending();
        long now = System.currentTimeMillis();
        boolean shouldBlink = isUpdating && ((now / 500) % 2 == 0);

        if (lastStatus.inSimulatedChunk()) {
            Component statusText = Component.literal("● Simulation: ACTIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            context.text(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                context.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }

            textY += lineHeight;

            if (lastStatus.fakeplayerName() != null && !lastStatus.fakeplayerName().isEmpty()) {
                Component fakeplayerLabel = Component.literal("Fakeplayer: ").withStyle(ChatFormatting.GRAY);
                ChatFormatting nameFormatting = ChatFormatting.GREEN;
                Component fakeplayerValue = Component.literal(lastStatus.fakeplayerName()).withStyle(nameFormatting);
                int labelWidth = font.width(fakeplayerLabel);
                int valueColor = 0xFF55FF55;
                context.text(font, fakeplayerLabel, textX, textY, 0xFFCCCCCC, false);
                context.text(font, fakeplayerValue, textX + labelWidth, textY, valueColor, false);
                textY += lineHeight;
            }

            Component chunkLabel = Component.literal("Chunk: ").withStyle(ChatFormatting.GRAY);
            Component chunkValue = Component.literal(lastStatus.chunkX() + ", " + lastStatus.chunkZ()).withStyle(ChatFormatting.WHITE);
            int chunkLabelWidth = font.width(chunkLabel);
            context.text(font, chunkLabel, textX, textY, 0xFFCCCCCC, false);
            context.text(font, chunkValue, textX + chunkLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            Component sdLabel = Component.literal("SD: ").withStyle(ChatFormatting.GRAY);
            Component sdValue = Component.literal(lastStatus.simulationDistance() + " chunks").withStyle(ChatFormatting.WHITE);
            int sdLabelWidth = font.width(sdLabel);
            context.text(font, sdLabel, textX, textY, 0xFFCCCCCC, false);
            context.text(font, sdValue, textX + sdLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            if (lastStatus.distance() >= 0) {
                Component distanceLabel = Component.literal("Distance: ").withStyle(ChatFormatting.GRAY);
                Component distanceValue = Component.literal(lastStatus.distance() + " chunks").withStyle(ChatFormatting.WHITE);
                int distanceLabelWidth = font.width(distanceLabel);
                context.text(font, distanceLabel, textX, textY, 0xFFCCCCCC, false);
                context.text(font, distanceValue, textX + distanceLabelWidth, textY, 0xFFFFFFFF, false);
            }
        } else {
            Component statusText = Component.literal("● Simulation: INACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            context.text(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                context.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }
        }
    }

    public static boolean needsUpdate() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        long interval = UPDATE_INTERVAL_MS_CLOSE;

        if (lastStatus != null) {
            interval = getUpdateInterval(lastStatus.distance());
        }

        return (now - lastUpdateTime) >= interval;
    }

    public static SimulationStatusResponsePayload getLastStatus() {
        return lastStatus;
    }

    public static int getHeight() {
        if (!enabled || lastStatus == null) {
            return 0;
        }
        int padding = 5;
        int lineHeight = 12;
        int contentHeight = lineHeight;
        if (lastStatus.inSimulatedChunk()) {
            contentHeight += lineHeight * 4;
        }
        return contentHeight + padding * 2;
    }

    public static void forceUpdate() {
        if (enabled) {
            lastUpdateTime = 0;
        }
    }
}

