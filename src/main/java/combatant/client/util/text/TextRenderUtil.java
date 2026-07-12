/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum TextRenderUtil {
    ;

    public static List<FlatPart> flatten(Component text, int defaultColor) {
        List<FlatPart> out = new ArrayList<>();
        if (text == null) return out;

        text.visit((style, str) -> {
            if (str == null || str.isEmpty()) return Optional.empty();

            Style s = style == null ? Style.EMPTY : style;

            int color = defaultColor;
            if (s.getColor() != null) {
                color = 0xFF000000 | s.getColor().getValue();
            }

            out.add(new FlatPart(str, color));
            return Optional.empty();
        }, Style.EMPTY);

        return out;
    }

    public static List<Part> flattenStyled(Component text, int defaultColor) {
        List<Part> out = new ArrayList<>();
        if (text == null) return out;

        text.visit((style, str) -> {
            if (str == null || str.isEmpty()) return Optional.empty();

            Style s = style == null ? Style.EMPTY : style;

            int color = defaultColor;
            if (s.getColor() != null) {
                color = 0xFF000000 | s.getColor().getValue();
            }

            out.add(new Part(
                    str,
                    color,
                    s.isBold(),
                    s.isItalic(),
                    s.isUnderlined(),
                    s.isStrikethrough(),
                    s.isObfuscated()
            ));
            return Optional.empty();
        }, Style.EMPTY);

        return out;
    }

    public record FlatPart(String text, int color) {
    }

    // Новый API — с bold / italic (для legacy &l &o)

    public record Part(String text, int color, boolean bold, boolean italic, boolean underline, boolean strikethrough,
                       boolean obfuscated) {
    }
}
