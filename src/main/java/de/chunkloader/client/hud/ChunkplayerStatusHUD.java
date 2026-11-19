package de.chunkloader.client.hud;

import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Environment(EnvType.CLIENT)
public class ChunkplayerStatusHUD {

    private static volatile boolean enabled = false;
    private static volatile long activationOrder;
    private static volatile ChunkplayerStatusResponsePayload lastStatus = null;
    private static volatile long lastUpdateTime = 0;
    private static volatile boolean requestPending = false;
    private static volatile long requestPendingTime = 0;
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
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        resetStatus();
        if (value) {
            activationOrder = StatusHudLayout.nextActivationOrder();
        }
        enabled = value;
    }

    public static long getActivationOrder() {
        return activationOrder;
    }

    public static void resetStatus() {
        lastStatus = null;
        lastUpdateTime = 0;
        requestPending = false;
        requestPendingTime = 0;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void updateStatus(ChunkplayerStatusResponsePayload status) {
        if (!enabled) {
            return;
        }
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

    public static void extractRenderState(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight, int offsetY) {
        ChunkplayerStatusResponsePayload status = lastStatus;
        if (!enabled || status == null) {
            return;
        }

        int padding = 5;
        int x = padding;
        int y = padding + offsetY;

        int boxWidth = 200;
        int lineHeight = 12;
        int boxHeight = getHeight();

        context.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);

        context.fill(x, y, x + boxWidth, y + 1, 0xFF000000);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF000000);
        context.fill(x, y, x + 1, y + boxHeight, 0xFF000000);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF000000);

        int borderColor = status.inLoadedChunk() ? 0xFF0088FF : 0xFFFF0000;
        context.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, borderColor);
        context.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, borderColor);
        context.fill(x + 1, y + 1, x + 2, y + boxHeight - 1, borderColor);
        context.fill(x + boxWidth - 2, y + 1, x + boxWidth - 1, y + boxHeight - 1, borderColor);

        int textX = x + padding;
        int textY = y + padding;

        boolean isUpdating = isRequestPending();
        long now = System.currentTimeMillis();
        boolean shouldBlink = isUpdating && ((now / 500) % 2 == 0);

        if (status.inLoadedChunk()) {
            Component statusText = Component.literal("● Chunkloading: ACTIVE").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            context.text(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                context.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }

            textY += lineHeight;

            if (status.chunkplayerName() != null && !status.chunkplayerName().isEmpty()) {
                Component chunkplayerLabel = Component.literal("Chunkplayer: ").withStyle(ChatFormatting.GRAY);
                Component chunkplayerValue = Component.literal(status.chunkplayerName()).withStyle(ChatFormatting.AQUA);
                int labelWidth = font.width(chunkplayerLabel);
                context.text(font, chunkplayerLabel, textX, textY, 0xFFCCCCCC, false);
                context.text(font, chunkplayerValue, textX + labelWidth, textY, 0xFFFFFFFF, false);
                textY += lineHeight;
            }

            Component chunkLabel = Component.literal("Chunk: ").withStyle(ChatFormatting.GRAY);
            Component chunkValue = Component.literal(status.chunkX() + ", " + status.chunkZ()).withStyle(ChatFormatting.WHITE);
            int chunkLabelWidth = font.width(chunkLabel);
            context.text(font, chunkLabel, textX, textY, 0xFFCCCCCC, false);
            context.text(font, chunkValue, textX + chunkLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            Component radiusLabel = Component.literal("SD: ").withStyle(ChatFormatting.GRAY);
            Component radiusValue = Component.literal(StatusHudLayout.describeDistance(status.radius())).withStyle(ChatFormatting.WHITE);
            int radiusLabelWidth = font.width(radiusLabel);
            context.text(font, radiusLabel, textX, textY, 0xFFCCCCCC, false);
            context.text(font, radiusValue, textX + radiusLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;

            if (status.distance() >= 0) {
                Component distanceLabel = Component.literal("Distance: ").withStyle(ChatFormatting.GRAY);
                Component distanceValue = Component.literal(status.distance() + " chunks").withStyle(ChatFormatting.WHITE);
                int distanceLabelWidth = font.width(distanceLabel);
                context.text(font, distanceLabel, textX, textY, 0xFFCCCCCC, false);
                context.text(font, distanceValue, textX + distanceLabelWidth, textY, 0xFFFFFFFF, false);
            }
        } else {
            Component statusText = Component.literal("● Chunkloading: INACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            context.text(font, statusText, textX, textY, 0xFFFFFFFF, false);

            if (shouldBlink) {
                int indicatorX = textX + font.width(statusText) + 3;
                context.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }
        }
    }

    public static boolean needsUpdate() {
        if (!enabled || isRequestPending()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long interval = UPDATE_INTERVAL_MS_CLOSE;

        if (lastStatus != null) {
            interval = getUpdateInterval(lastStatus.distance());
        }

        return (now - lastUpdateTime) >= interval;
    }

    public static ChunkplayerStatusResponsePayload getLastStatus() {
        return lastStatus;
    }

    public static int getHeight() {
        ChunkplayerStatusResponsePayload status = lastStatus;
        if (!enabled || status == null) {
            return 0;
        }
        int lines = 1;
        if (status.inLoadedChunk()) {
            lines += 2;
            if (status.chunkplayerName() != null && !status.chunkplayerName().isEmpty()) {
                lines++;
            }
            if (status.distance() >= 0) {
                lines++;
            }
        }
        return lines * 12 + 10;
    }

    public static void forceUpdate() {
        if (enabled) {
            lastUpdateTime = 0;
            requestPending = false;
        }
    }
}

