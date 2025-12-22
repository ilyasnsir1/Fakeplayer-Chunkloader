package de.chunkloader.client.hud;

import de.chunkloader.network.payload.ChunkplayerStatusResponsePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ChunkplayerStatusHUD {
    
    private static boolean enabled = false;
    private static ChunkplayerStatusResponsePayload lastStatus = null;
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
    
    public static void updateStatus(ChunkplayerStatusResponsePayload status) {
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
    
    public static void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight, int offsetY) {
        if (!enabled || lastStatus == null) {
            return;
        }
        
        int padding = 5;
        int x = padding;
        int y = padding + offsetY;
        
        int boxWidth = 200;
        int boxHeight = 60;
        
        int lineHeight = 12;
        int contentHeight = lineHeight;
        if (lastStatus.inLoadedChunk()) {
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
        
        int borderColor = lastStatus.inLoadedChunk() ? 0xFF0088FF : 0xFFFF0000;
        context.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, borderColor);
        context.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, borderColor);
        context.fill(x + 1, y + 1, x + 2, y + boxHeight - 1, borderColor);
        context.fill(x + boxWidth - 2, y + 1, x + boxWidth - 1, y + boxHeight - 1, borderColor);
        
        int textX = x + padding;
        int textY = y + padding;
        
        boolean isUpdating = isRequestPending();
        long now = System.currentTimeMillis();
        boolean shouldBlink = isUpdating && ((now / 500) % 2 == 0);
        
        if (lastStatus.inLoadedChunk()) {
            Text statusText = Text.literal("● Chunkloading: ACTIVE").formatted(Formatting.AQUA, Formatting.BOLD);
            context.drawText(textRenderer, statusText, textX, textY, 0xFFFFFFFF, false);
            
            if (shouldBlink) {
                int indicatorX = textX + textRenderer.getWidth(statusText) + 3;
                context.fill(indicatorX, textY + 4, indicatorX + 3, textY + 7, 0xFFFFFF00);
            }
            
            textY += lineHeight;
            
            if (lastStatus.chunkplayerName() != null && !lastStatus.chunkplayerName().isEmpty()) {
                Text chunkplayerLabel = Text.literal("Chunkplayer: ").formatted(Formatting.GRAY);
                Text chunkplayerValue = Text.literal(lastStatus.chunkplayerName()).formatted(Formatting.AQUA);
                int labelWidth = textRenderer.getWidth(chunkplayerLabel);
                context.drawText(textRenderer, chunkplayerLabel, textX, textY, 0xFFCCCCCC, false);
                context.drawText(textRenderer, chunkplayerValue, textX + labelWidth, textY, 0xFFFFFFFF, false);
                textY += lineHeight;
            }
            
            Text chunkLabel = Text.literal("Chunk: ").formatted(Formatting.GRAY);
            Text chunkValue = Text.literal(lastStatus.chunkX() + ", " + lastStatus.chunkZ()).formatted(Formatting.WHITE);
            int chunkLabelWidth = textRenderer.getWidth(chunkLabel);
            context.drawText(textRenderer, chunkLabel, textX, textY, 0xFFCCCCCC, false);
            context.drawText(textRenderer, chunkValue, textX + chunkLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;
            
            Text radiusLabel = Text.literal("Radius: ").formatted(Formatting.GRAY);
            Text radiusValue = Text.literal(lastStatus.radius() + " chunks").formatted(Formatting.WHITE);
            int radiusLabelWidth = textRenderer.getWidth(radiusLabel);
            context.drawText(textRenderer, radiusLabel, textX, textY, 0xFFCCCCCC, false);
            context.drawText(textRenderer, radiusValue, textX + radiusLabelWidth, textY, 0xFFFFFFFF, false);
            textY += lineHeight;
            
            if (lastStatus.distance() >= 0) {
                Text distanceLabel = Text.literal("Distance: ").formatted(Formatting.GRAY);
                Text distanceValue = Text.literal(lastStatus.distance() + " chunks").formatted(Formatting.WHITE);
                int distanceLabelWidth = textRenderer.getWidth(distanceLabel);
                context.drawText(textRenderer, distanceLabel, textX, textY, 0xFFCCCCCC, false);
                context.drawText(textRenderer, distanceValue, textX + distanceLabelWidth, textY, 0xFFFFFFFF, false);
            }
        } else {
            Text statusText = Text.literal("● Chunkloading: INACTIVE").formatted(Formatting.RED, Formatting.BOLD);
            context.drawText(textRenderer, statusText, textX, textY, 0xFFFFFFFF, false);
            
            if (shouldBlink) {
                int indicatorX = textX + textRenderer.getWidth(statusText) + 3;
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
    
    public static ChunkplayerStatusResponsePayload getLastStatus() {
        return lastStatus;
    }
    
    public static int getHeight() {
        if (!enabled || lastStatus == null) {
            return 0;
        }
        int padding = 5;
        int lineHeight = 12;
        int contentHeight = lineHeight;
        if (lastStatus.inLoadedChunk()) {
            contentHeight += lineHeight * 4;
        }
        return contentHeight + padding * 2;
    }
}

