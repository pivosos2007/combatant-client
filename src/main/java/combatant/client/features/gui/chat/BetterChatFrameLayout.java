/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.BetterChatMessageLayout.Glyph;
import combatant.client.features.gui.chat.BetterChatMessageLayout.VisualLine;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable screen-space geometry used by selection, hover, context actions and rendering. */
record BetterChatFrameLayout(float x, float y, float w, float h,
                             float lineSpacing,
                             List<FrameLine> lines,
                             List<MessageBubble> bubbles) {
    static BetterChatFrameLayout empty() {
        return new BetterChatFrameLayout(0, 0, 0, 0, 0, List.of(), List.of());
    }

    static BetterChatFrameLayout build(List<VisualLine> lines,
                                       float x, float y, float w, float h,
                                       float fontSize, float lineHeight, float yOffset,
                                       float timestampReserve,
                                       float padding, float messagePadX, float messagePadY,
                                       float messageGap, float lineSpacing) {
        List<FrameLine> positioned = new ArrayList<>(lines.size());
        float cursorY = y + messagePadY + yOffset;
        int lastGroup = Integer.MIN_VALUE;
        for (int index = 0; index < lines.size(); index++) {
            VisualLine line = lines.get(index);
            if (index > 0 && line.messageGroup() != lastGroup) cursorY += messageGap;
            lastGroup = line.messageGroup();
            float y0 = cursorY;
            float y1 = y0 + fontSize;
            List<GlyphBox> boxes = new ArrayList<>(line.glyphs().size());
            for (Glyph glyph : line.glyphs()) {
                boxes.add(new GlyphBox(
                        x + padding + messagePadX + glyph.x0(), y0,
                        x + padding + messagePadX + glyph.x1(), y1,
                        glyph.charIndex(), glyph.charEndExclusive(), glyph.color(), glyph.font(),
                        glyph.hover(), glyph.text(), glyph.style(), glyph.item()));
            }
            positioned.add(new FrameLine(line.message(), line.messageIndex(), line.messageGroup(),
                    y0, y1, List.copyOf(boxes)));
            cursorY += lineHeight;
        }
        List<FrameLine> immutableLines = List.copyOf(positioned);
        return new BetterChatFrameLayout(x, y, w, h, lineSpacing, immutableLines,
                buildBubbles(immutableLines, x, w, timestampReserve, padding, messagePadX, messagePadY));
    }

    private static List<MessageBubble> buildBubbles(List<FrameLine> lines,
                                                     float x, float width, float timestampReserve,
                                                     float padding, float messagePadX, float messagePadY) {
        if (lines.isEmpty()) return Collections.emptyList();
        List<MessageBubble> bubbles = new ArrayList<>();
        int start = 0;
        while (start < lines.size()) {
            FrameLine first = lines.get(start);
            int end = start;
            float maxRight = x + padding + messagePadX;
            while (end + 1 < lines.size() && lines.get(end + 1).messageGroup() == first.messageGroup()) end++;
            for (int index = start; index <= end; index++) {
                FrameLine line = lines.get(index);
                if (!line.glyphs().isEmpty()) maxRight = Math.max(maxRight, line.glyphs().getLast().x1());
            }
            FrameLine last = lines.get(end);
            float bubbleX = x + padding;
            float bubbleY = first.y0() - messagePadY;
            float maxBubbleWidth = Math.max(32f, width - padding * 2f);
            float desiredWidth = maxRight - bubbleX + messagePadX + Math.max(0f, timestampReserve);
            float bubbleWidth = Mth.clamp(desiredWidth, 76f, maxBubbleWidth);
            float bubbleHeight = last.y1() - first.y0() + messagePadY * 2f;
            bubbles.add(new MessageBubble(last.message(), first.messageGroup(), bubbleX, bubbleY,
                    bubbleWidth, bubbleHeight, first, last));
            start = end + 1;
        }
        return List.copyOf(bubbles);
    }

    MessageClipBounds messageClipBounds(float padding) {
        if (lines.isEmpty() || h <= 0f) return null;
        return new MessageClipBounds(x + padding, y, Math.max(1f, w - padding * 2f), h);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    PickResult pick(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return null;
        for (FrameLine line : lines) {
            if (mouseY < line.y0() || mouseY > line.y1() + lineSpacing || line.glyphs().isEmpty()) continue;
            float minX = line.glyphs().getFirst().x0();
            float maxX = line.glyphs().getLast().x1();
            if (mouseX < minX - 2f || mouseX > maxX + 2f) continue;
            GlyphBox closest = null;
            for (GlyphBox glyph : line.glyphs()) {
                if (mouseX >= glyph.x0() && mouseX <= glyph.x1()) return new PickResult(line, glyph);
                if (closest == null || Math.abs(mouseX - glyph.x1()) < Math.abs(mouseX - closest.x1())) closest = glyph;
            }
            if (closest != null) return new PickResult(line, closest);
        }
        return null;
    }

    PickResult pickContext(double mouseX, double mouseY) {
        PickResult direct = pick(mouseX, mouseY);
        if (direct != null || !contains(mouseX, mouseY)) return direct;
        for (MessageBubble bubble : bubbles) {
            if (mouseX < bubble.x() || mouseX > bubble.x() + bubble.w()
                    || mouseY < bubble.y() || mouseY > bubble.y() + bubble.h()) continue;
            FrameLine closestLine = null;
            float bestLineDistance = Float.MAX_VALUE;
            for (FrameLine line : lines) {
                if (line.messageGroup() != bubble.messageGroup() || line.glyphs().isEmpty()) continue;
                float distance = mouseY < line.y0() ? (float) (line.y0() - mouseY)
                        : mouseY > line.y1() ? (float) (mouseY - line.y1()) : 0f;
                if (distance < bestLineDistance) {
                    bestLineDistance = distance;
                    closestLine = line;
                }
            }
            if (closestLine == null) return null;
            GlyphBox closest = null;
            float bestGlyphDistance = Float.MAX_VALUE;
            for (GlyphBox glyph : closestLine.glyphs()) {
                float distance = mouseX < glyph.x0() ? (float) (glyph.x0() - mouseX)
                        : mouseX > glyph.x1() ? (float) (mouseX - glyph.x1()) : 0f;
                if (distance < bestGlyphDistance) {
                    bestGlyphDistance = distance;
                    closest = glyph;
                }
            }
            return closest == null ? null : new PickResult(closestLine, closest);
        }
        return null;
    }

    record GlyphBox(float x0, float y0, float x1, float y1,
                    int charIndex, int charEndExclusive, int color, String font,
                    HoverEvent hover, String text, Style style, ItemStack item) { }

    record FrameLine(ChatLine message, int messageIndex, int messageGroup,
                     float y0, float y1, List<GlyphBox> glyphs) { }

    record MessageBubble(ChatLine message, int messageGroup, float x, float y, float w, float h,
                         FrameLine firstLine, FrameLine lastLine) { }

    record MessageClipBounds(float x, float y, float w, float h) { }

    record PickResult(FrameLine line, GlyphBox glyph) { }
}
