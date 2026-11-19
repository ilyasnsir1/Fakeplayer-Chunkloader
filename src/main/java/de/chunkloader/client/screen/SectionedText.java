package de.chunkloader.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class SectionedText {
    static final int LINE_SPACING = 12;
    static final int TOP_WITH_SIDEBAR = 54;
    static final int TOP_BELOW_TABS = 74;
    static final int BOTTOM_INSET = 44;

    private static final int SECTION_GAP = 16;
    private static final int MIN_SECTION_GAP = 10;

    private final List<Line> lines = new ArrayList<>();
    private final List<Integer> separators = new ArrayList<>();
    private int height;

    private record Line(FormattedCharSequence text, int y) {}

    int layout(Font font, List<Component> sections, int width, int availableHeight) {
        int maxWidth = Math.max(1, width - 10);
        wrap(font, sections, maxWidth, SECTION_GAP);
        int overflow = height - availableHeight;
        int gaps = separators.size();
        if (availableHeight > 0 && overflow > 0 && gaps > 0
                && overflow <= gaps * (SECTION_GAP - MIN_SECTION_GAP)) {
            wrap(font, sections, maxWidth, SECTION_GAP - Math.ceilDiv(overflow, gaps));
        }
        return height;
    }

    private void wrap(Font font, List<Component> sections, int width, int sectionGap) {
        lines.clear();
        separators.clear();
        height = 0;
        int y = 0;
        for (Component section : sections) {
            if (section.getString().isEmpty()) {
                separators.add(y + 2);
                y += sectionGap;
                continue;
            }
            for (var wrapped : font.split(section, width)) {
                lines.add(new Line(wrapped, y));
                height = y + font.lineHeight;
                y += LINE_SPACING;
            }
            if (section.getStyle().isBold()) {
                y += 4;
            }
        }
    }

    void render(GuiGraphicsExtractor context, Font font, int left, int top, int right, int bottom, int scrollOffset) {
        if (bottom <= top) {
            return;
        }
        context.enableScissor(left, top, right, bottom);
        int originY = top - scrollOffset;
        for (Line line : lines) {
            int y = originY + line.y();
            if (y + font.lineHeight > top && y < bottom) {
                context.text(font, line.text(), left, y, 0xFFCCCCCC, false);
            }
        }
        int separatorWidth = Math.clamp(right - left - 30, 0, 200);
        for (int separator : separators) {
            int y = originY + separator;
            if (y >= top && y < bottom) {
                context.fill(left, y, left + separatorWidth, y + 1, 0x66FFFFFF);
            }
        }
        context.disableScissor();
    }
}
