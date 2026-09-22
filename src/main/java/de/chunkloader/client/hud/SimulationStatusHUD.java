package de.chunkloader.client.hud;

import de.chunkloader.network.payload.SimulationStatusResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
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

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!enabled) {
            return;
        }

        final SimulationStatusResponsePayload status = lastStatus;
        boolean hasData = status != null;

        int padding = 5;
        int x = padding;
        int y = padding;

        int boxWidth = 200;
        int lineHeight = 12;
        int contentHeight = lineHeight;

        if (hasData) {
            if (lastStatus.inSimulatedChunk()) {
                contentHeight += lineHeight;
                contentHeight += lineHeight;
                contentHeight += lineHeight;
                if (lastStatus.distance() >= 0) {
                    contentHeight += lineHeight;
                }
            }
        } else {
            contentHeight += lineHeight;
        }

        int boxHeight = contentHeight + padding * 2;

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);

        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF000000);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF000000);
        graphics.fill(x, y, x + 1, y + boxHeight, 0xFF000000);
        graphics.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF000000);

        int borderColor = hasData && status.inSimulatedChunk() ? 0xFF00FF00 : 0xFFFF0000;
        graphics.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, borderColor);
        graphics.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, borderColor);
        graphics.fill(x + 1, y + 1, x + 2, y + boxHeight - 1, borderColor);
        graphics.fill(x + boxWidth - 2, y + 1, x + boxWidth - 1, y + boxHeight - 1, borderColor);

        int textX = x + padding;
        int textY = y + padding;

        boolean isUpdating = isRequestPending();
        long now = System.currentTimeMillis();
        boolean shouldBlink = isUpdating && ((now / 500) % 2 == 0);

        var font = Minecraft.getInstance().font;

        if (status == null) {
            Component statusText = Component.literal("● Simulation Distance").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            graphics.drawString(font, statusText, textX, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            Component waitingText = Component.literal("Waiting for data...").withStyle(ChatFormatting.GRAY);
            graphics.drawString(font, waitingText, textX, textY, 0xFFCCCCCC, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                graphics.fill(indicatorX, textY - lineHeight + 4, indicatorX + 3, textY - lineHeight + 7, 0xFFFFFF00);
            }
        } else if (status.inSimulatedChunk()) {
            Component statusText = Component.literal("● Simulation: ACTIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            graphics.drawString(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                graphics.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }

            textY += lineHeight;

            if (status.fakeplayerName() != null && !status.fakeplayerName().isEmpty()) {
                Component fakeplayerLabel = Component.literal("Fakeplayer: ").withStyle(ChatFormatting.GRAY);
                Component fakeplayerValue = Component.literal(status.fakeplayerName()).withStyle(ChatFormatting.GREEN);
                int labelWidth = font.width(fakeplayerLabel);
                int valueColor = 0xFF55FF55;
                graphics.drawString(font, fakeplayerLabel, textX, textY, 0xFFCCCCCC, false);
                graphics.drawString(font, fakeplayerValue, textX + labelWidth, textY, valueColor, false);
                textY += lineHeight;
            }

            Component chunkLabel = Component.literal("Chunk: ").withStyle(ChatFormatting.GRAY);
            Component chunkValue = Component.literal(status.chunkX() + ", " + status.chunkZ()).withStyle(ChatFormatting.WHITE);
            int chunkLabelWidth = font.width(chunkLabel);
            graphics.drawString(font, chunkLabel, textX, textY, 0xFFCCCCCC, false);
            graphics.drawString(font, chunkValue, textX + chunkLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            Component sdLabel = Component.literal("SD: ").withStyle(ChatFormatting.GRAY);
            Component sdValue = Component.literal(status.simulationDistance() + " chunks").withStyle(ChatFormatting.WHITE);
            int sdLabelWidth = font.width(sdLabel);
            graphics.drawString(font, sdLabel, textX, textY, 0xFFCCCCCC, false);
            graphics.drawString(font, sdValue, textX + sdLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            if (status.distance() >= 0) {
                Component distanceLabel = Component.literal("Distance: ").withStyle(ChatFormatting.GRAY);
                Component distanceValue = Component.literal(status.distance() + " chunks").withStyle(ChatFormatting.WHITE);
                int distanceLabelWidth = font.width(distanceLabel);
                graphics.drawString(font, distanceLabel, textX, textY, 0xFFCCCCCC, false);
                graphics.drawString(font, distanceValue, textX + distanceLabelWidth, textY, 0xFFFFFFFF, false);
            }
        } else {
            Component statusText = Component.literal("● Simulation: INACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            graphics.drawString(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                graphics.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }
        }
    }

    public static boolean needsUpdate() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        long interval = UPDATE_INTERVAL_MS_CLOSE;

        SimulationStatusResponsePayload status = lastStatus;
        if (status != null) {
            interval = getUpdateInterval(status.distance());
        }

        return (now - lastUpdateTime) >= interval;
    }

    public static SimulationStatusResponsePayload getLastStatus() {
        return lastStatus;
    }

    public static int getHeight() {
        if (!enabled) {
            return 0;
        }
        int padding = 5;
        int lineHeight = 12;
        int contentHeight = lineHeight;
        SimulationStatusResponsePayload status = lastStatus;
        if (status != null) {
            if (status.inSimulatedChunk()) {
                contentHeight += lineHeight * 4;
            }
        } else {
            contentHeight += lineHeight;
        }
        return contentHeight + padding * 2;
    }

    public static void forceUpdate() {
        if (enabled) {
            lastUpdateTime = 0;
        }
    }
}

