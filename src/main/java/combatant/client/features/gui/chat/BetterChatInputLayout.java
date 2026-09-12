/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Visual lines and cursor mapping for the editable chat input. */
final class BetterChatInputLayout {
    private BetterChatInputLayout() { }

    static List<InputLine> wrap(String text, float fontSize, float maxWidth) {
        String safe = text == null ? "" : text;
        List<InputLine> lines = new ArrayList<>();
        int start = 0;
        float width = 0f;
        for (int offset = 0; offset < safe.length(); ) {
            int codePoint = safe.codePointAt(offset);
            int length = Character.charCount(codePoint);
            String glyph = new String(Character.toChars(codePoint));
            float advance = BetterChatTextSupport.width(BetterChatTextSupport.iosevkaRegular(), glyph, fontSize);
            if (width + advance > maxWidth && width > 0f) {
                lines.add(new InputLine(start, offset, width));
                start = offset;
                width = 0f;
            }
            width += advance;
            offset += length;
        }
        lines.add(new InputLine(start, safe.length(), width));
        return lines;
    }

    static float widthTo(String text, List<InputLine> lines, int cursorIndex, float fontSize) {
        if (lines == null || lines.isEmpty()) return 0f;
        int lineIndex = visualLineForCursor(lines, cursorIndex);
        InputLine line = lines.get(Mth.clamp(lineIndex, 0, lines.size() - 1));
        return BetterChatTextSupport.width(BetterChatTextSupport.iosevkaRegular(),
                safeSubstring(text, line.start(), cursorIndex), fontSize);
    }

    static int visualLineForCursor(List<InputLine> lines, int cursorIndex) {
        if (lines == null || lines.isEmpty()) return 0;
        for (int i = 0; i < lines.size(); i++) {
            InputLine line = lines.get(i);
            boolean last = i == lines.size() - 1;
            if (cursorIndex >= line.start() && (cursorIndex < line.end() || last && cursorIndex <= line.end())) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    private static String safeSubstring(String text, int start, int end) {
        if (text == null) return "";
        int length = text.length();
        int safeStart = Math.max(0, Math.min(length, start));
        int safeEnd = Math.max(0, Math.min(length, end));
        if (safeEnd < safeStart) {
            int swap = safeStart;
            safeStart = safeEnd;
            safeEnd = swap;
        }
        return text.substring(safeStart, safeEnd);
    }

    record InputLine(int start, int end, float width) { }
}
