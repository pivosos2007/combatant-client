/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.BetterChatMessageLayout.CachedLine;
import combatant.client.features.gui.chat.BetterChatMessageLayout.Glyph;

import java.util.ArrayList;
import java.util.List;

/** Whitespace-aware line breaker with character fallback for tokens wider than the viewport. */
final class BetterChatWordWrapper {
    private BetterChatWordWrapper() { }

    static void appendParagraph(List<Glyph> paragraph,
                                int paragraphStart,
                                int paragraphEnd,
                                float maxWidth,
                                List<CachedLine> output) {
        if (paragraph.isEmpty()) {
            output.add(new CachedLine(paragraphStart, Math.max(paragraphStart, paragraphEnd - 1), List.of()));
            return;
        }

        int start = 0;
        while (start < paragraph.size()) {
            while (start < paragraph.size() && isBreakableWhitespace(paragraph.get(start))) start++;
            if (start >= paragraph.size()) break;

            float width = 0f;
            int fitEnd = start;
            int lastBreak = -1;
            while (fitEnd < paragraph.size()) {
                Glyph glyph = paragraph.get(fitEnd);
                float advance = glyph.x1() - glyph.x0();
                if (width + advance > maxWidth && fitEnd > start) break;
                width += advance;
                if (isBreakableWhitespace(glyph)) lastBreak = fitEnd;
                fitEnd++;
            }

            int end;
            int next;
            if (fitEnd == paragraph.size()) {
                end = trimTrailingWhitespace(paragraph, start, fitEnd);
                next = paragraph.size();
            } else if (isBreakableWhitespace(paragraph.get(fitEnd))) {
                // The complete word fits; only its following separator does not.
                end = trimTrailingWhitespace(paragraph, start, fitEnd);
                next = fitEnd + 1;
            } else if (lastBreak >= start) {
                end = trimTrailingWhitespace(paragraph, start, lastBreak);
                next = lastBreak + 1;
            } else {
                end = Math.max(start + 1, fitEnd);
                next = end;
            }

            if (end > start) output.add(line(paragraph, start, end));
            start = next;
        }
    }

    private static int trimTrailingWhitespace(List<Glyph> glyphs, int start, int end) {
        while (end > start && isBreakableWhitespace(glyphs.get(end - 1))) end--;
        return end;
    }

    private static CachedLine line(List<Glyph> source, int start, int end) {
        List<Glyph> glyphs = new ArrayList<>(end - start);
        float x = 0f;
        for (int index = start; index < end; index++) {
            Glyph glyph = source.get(index);
            float width = glyph.x1() - glyph.x0();
            glyphs.add(new Glyph(x, x + width, glyph.charIndex(), glyph.charEndExclusive(),
                    glyph.color(), glyph.hover(), glyph.font(), glyph.text(), glyph.style(), glyph.item()));
            x += width;
        }
        Glyph first = glyphs.getFirst();
        Glyph last = glyphs.getLast();
        return new CachedLine(first.charIndex(), Math.max(first.charIndex(), last.charEndExclusive() - 1), List.copyOf(glyphs));
    }

    private static boolean isBreakableWhitespace(Glyph glyph) {
        if (glyph == null || glyph.item() != null || glyph.text() == null || glyph.text().isEmpty()) return false;
        int codePoint = glyph.text().codePointAt(0);
        return codePoint != 0x00A0 && codePoint != 0x202F && Character.isWhitespace(codePoint);
    }
}
