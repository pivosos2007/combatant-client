/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import net.minecraft.network.chat.Component;

import java.util.List;

final class BetterChatMessageGrouping {
    private static final String BOX_PREFIXES = "┏┓┗┛┃│║╔╗╚╝╭╮╰╯┌┐└┘┣┫├┤╠╣";

    private BetterChatMessageGrouping() {
    }

    static int[] groupIds(List<ChatLine> messages) {
        if (messages == null || messages.isEmpty()) {
            return new int[0];
        }

        int[] groups = new int[messages.size()];
        int currentGroup = -1;
        boolean inBoxBlock = false;
        for (int i = 0; i < messages.size(); i++) {
            boolean boxLine = isBoxLine(messages.get(i));
            boolean emptyBoxSpacer = isBlank(messages.get(i))
                    && (isBoxLineAt(messages, i - 1) || isBoxLineAt(messages, i + 1));
            boolean attachToBox = boxLine || emptyBoxSpacer;

            if (attachToBox) {
                if (!inBoxBlock) {
                    currentGroup++;
                    inBoxBlock = true;
                }
            } else {
                currentGroup++;
                inBoxBlock = false;
            }
            groups[i] = currentGroup;
        }
        return groups;
    }

    private static boolean isBoxLineAt(List<ChatLine> messages, int index) {
        return index >= 0 && index < messages.size() && isBoxLine(messages.get(index));
    }

    private static boolean isBoxLine(ChatLine line) {
        String text = string(line).stripLeading();
        if (text.isEmpty()) return false;
        int cp = text.codePointAt(0);
        if (BOX_PREFIXES.indexOf(cp) >= 0) return true;
        return isMostlyBoxDrawing(text);
    }

    private static boolean isMostlyBoxDrawing(String text) {
        int drawing = 0;
        int visible = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue;
            visible++;
            if (isBoxDrawing(cp)) drawing++;
        }
        return visible > 0 && drawing >= Math.max(1, visible - 1);
    }

    private static boolean isBoxDrawing(int cp) {
        return (cp >= 0x2500 && cp <= 0x257F) || cp == '═' || cp == '║';
    }

    private static boolean isBlank(ChatLine line) {
        return string(line).isBlank();
    }

    private static String string(ChatLine line) {
        if (line == null) return "";
        Component text = line.text();
        return text == null ? "" : text.getString();
    }
}
