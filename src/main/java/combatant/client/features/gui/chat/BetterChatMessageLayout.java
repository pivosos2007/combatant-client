/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.render.engine.text.TextGlyphFallback;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.TopEnchantUtil;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

/** Builds and caches rich-message glyph lines. Wrapping prefers whitespace and splits only oversized words. */
final class BetterChatMessageLayout {
    private static final BetterChatMessageCache CACHE = new BetterChatMessageCache();

    private BetterChatMessageLayout() { }

    static LayoutResult tail(List<ChatLine> messages, int[] groups, float fontSize, float maxWidth, int maxLines) {
        if (messages == null || messages.isEmpty()) return new LayoutResult(Collections.emptyList(), 0);
        List<VisualLine> lines = new ArrayList<>();
        int seenMessages = 0;
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            ChatLine message = messages.get(messageIndex);
            int group = groups != null && messageIndex < groups.length ? groups[messageIndex] : messageIndex;
            List<CachedLine> cached = lines(message, fontSize, maxWidth);
            for (int line = cached.size() - 1; line >= 0; line--) {
                CachedLine value = cached.get(line);
                lines.addFirst(new VisualLine(message, messageIndex, group,
                        value.startChar(), value.endChar(), value.glyphs()));
            }
            seenMessages++;
            if (lines.size() >= maxLines) break;
        }
        return new LayoutResult(lines, seenMessages);
    }

    static void invalidateLayouts() {
        CACHE.invalidateLayouts();
    }

    static void clear() {
        CACHE.clear();
    }

    private static List<CachedLine> lines(ChatLine message, float fontSize, float maxWidth) {
        int baseColor = theme().textPrimary();
        CachedMessageLayout cached = CACHE.layout(message);
        if (cached != null
                && Math.abs(cached.fontSize() - fontSize) < 0.01f
                && Math.abs(cached.maxWidth() - maxWidth) < 0.5f
                && cached.baseColor() == baseColor) {
            return cached.lines();
        }

        List<Segment> segments = CACHE.segments(message, BetterChatRichMessageFlattener::flatten);
        List<CachedLine> result = buildLines(segments, fontSize, maxWidth, baseColor);
        CACHE.putLayout(message, new CachedMessageLayout(fontSize, maxWidth, baseColor, result));
        return result;
    }

    private static List<CachedLine> buildLines(List<Segment> segments,
                                               float fontSize,
                                               float maxWidth,
                                               int baseColor) {
        List<CachedLine> lines = new ArrayList<>();
        List<Glyph> paragraph = new ArrayList<>();
        TextRenderer activeRenderer = null;
        float scale = BetterChatTextSupport.scale(fontSize);
        int charIndex = 0;
        int paragraphStart = 0;

        try {
            for (Segment segment : segments) {
                Style style = segment.style();
                HoverEvent hover = style.getHoverEvent();

                if (segment.item() != null && !segment.item().isEmpty()) {
                    if (activeRenderer != null) {
                        activeRenderer.end();
                        activeRenderer = null;
                    }
                    float itemSize = Math.max(12f, fontSize);
                    int logicalLength = Math.max(0, segment.logicalLength());
                    paragraph.add(new Glyph(0f, itemSize, charIndex, charIndex + logicalLength,
                            baseColor, hover, "", segment.text(), style, segment.item().copy()));
                    charIndex += logicalLength;
                    continue;
                }

                int color = style.getColor() != null
                        ? 0xFF000000 | style.getColor().getValue()
                        : baseColor;
                if (hover instanceof HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate template)) {
                    ItemStack stack = resolveCachedItem(template.create());
                    if (IllegalItemUtil.isIllegal(stack)) color = IllegalItemUtil.illegalColor();
                    else if (TopEnchantUtil.hasTopEnchant(stack)) color = TopEnchantUtil.topColor();
                } else if (color == baseColor) {
                    warmHoverCache(segment.text());
                }

                String font = BetterChatTextSupport.fontForStyle(style);
                String text = segment.text();
                for (int offset = 0; offset < text.length(); ) {
                    int codePoint = text.codePointAt(offset);
                    int clusterEnd = BetterChatTextSupport.nextClusterEnd(text, offset);
                    int clusterLength = clusterEnd - offset;
                    if (codePoint == '\n') {
                        BetterChatWordWrapper.appendParagraph(
                                paragraph, paragraphStart, charIndex, maxWidth, lines);
                        paragraph = new ArrayList<>();
                        charIndex++;
                        paragraphStart = charIndex;
                        offset++;
                        continue;
                    }

                    String glyphText = text.substring(offset, clusterEnd);
                    String resolvedFont = BetterChatTextSupport.fontForCluster(font, glyphText);
                    boolean svg = TextGlyphFallback.isSvgFontKey(resolvedFont);
                    TextRenderer renderer = svg ? null : BetterChatTextSupport.renderer(resolvedFont);
                    if (svg) {
                        if (activeRenderer != null) {
                            activeRenderer.end();
                            activeRenderer = null;
                        }
                    } else if (renderer != activeRenderer) {
                        if (activeRenderer != null) activeRenderer.end();
                        activeRenderer = renderer;
                        if (activeRenderer != null) activeRenderer.begin(scale, true, false);
                    }
                    float advance = svg
                            ? BetterChatTextSupport.svgGlyphSize(BetterChatTextSupport.renderer(font), fontSize, false)
                            : activeRenderer == null ? 0f : (float) activeRenderer.getWidth(glyphText, false);
                    paragraph.add(new Glyph(0f, advance, charIndex, charIndex + clusterLength,
                            color, hover, resolvedFont,
                            glyphText, style, null));
                    charIndex += clusterLength;
                    offset += clusterLength;
                }
            }
        } finally {
            if (activeRenderer != null) activeRenderer.end();
        }

        if (!paragraph.isEmpty()) {
            BetterChatWordWrapper.appendParagraph(
                    paragraph, paragraphStart, charIndex, maxWidth, lines);
        } else if (charIndex == 0) {
            lines.add(new CachedLine(0, 0, List.of(new Glyph(
                    0f, 2f, 0, 1, baseColor, null, "iosevka_medium", " ", Style.EMPTY, null))));
        }
        return List.copyOf(lines);
    }

     private static void warmHoverCache(String text) {
        BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
        if (cache == null || text == null) return;
        String cleaned = text.replaceAll("[\\[\\]<>‚]", "").trim();
        if (!cleaned.isEmpty()) cache.findItemByDisplayNameWithMeta(cleaned);
    }

     private static ItemStack resolveCachedItem(ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
        if (cache == null) return source.copy();
        ItemStack cached = cache.getItem(BetterChatStoreManager.hoverItemKey(source));
        return cached.isEmpty() ? source.copy() : cached;
    }

    record Segment(String text, Style style, ItemStack item, int logicalLength) {
        static Segment text(String text, Style style) {
            String safe = text == null ? "" : text;
            return new Segment(safe, style == null ? Style.EMPTY : style, null, safe.length());
        }

        static Segment richItem(String accessibleText, ItemStack item) {
            String safe = accessibleText == null ? "" : accessibleText;
            return new Segment(safe, Style.EMPTY, item == null ? ItemStack.EMPTY : item.copy(), safe.length());
        }

        static Segment decorativeItem(ItemStack item, Style style) {
            return new Segment("", style == null ? Style.EMPTY : style,
                    item == null ? ItemStack.EMPTY : item.copy(), 0);
        }
    }

    record Glyph(float x0, float x1, int charIndex, int charEndExclusive, int color,
                 HoverEvent hover, String font, String text, Style style, ItemStack item) { }

    record CachedLine(int startChar, int endChar, List<Glyph> glyphs) { }

    record CachedMessageLayout(float fontSize, float maxWidth, int baseColor, List<CachedLine> lines) { }

    record VisualLine(ChatLine message, int messageIndex, int messageGroup,
                      int startChar, int endChar, List<Glyph> glyphs) { }

    record LayoutResult(List<VisualLine> lines, int messagesSeen) { }
}
