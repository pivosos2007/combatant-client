/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Safe metrics for single-line text supplied by servers or other runtime data.
 *
 * <p>The important invariant is that screen layout is measured through the same
 * {@link TextRenderer} (including its language fallback) and at the same scale
 * that will draw it. World billboard callers must keep using
 * {@link WorldTextRenderer}, whose metrics match its direct glyph backend.</p>
 */
public enum RuntimeTextLayout {
    ;

    public static String singleLine(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder out = null;
        int previous = -1;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int count = Character.charCount(codePoint);
            boolean separator = codePoint == '\r'
                    || codePoint == '\n'
                    || codePoint == '\t'
                    || Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
            boolean unsafeControl = Character.isISOControl(codePoint) && !separator;
            boolean unsafeFormat = Character.getType(codePoint) == Character.FORMAT
                    && codePoint != 0x200C
                    && codePoint != 0x200D;

            if (separator || unsafeControl || unsafeFormat) {
                if (out == null) {
                    out = new StringBuilder(value.length());
                    out.append(value, 0, offset);
                }
                if (separator && previous != ' ' && out.length() > 0) {
                    out.append(' ');
                    previous = ' ';
                }
            } else {
                if (out != null) out.appendCodePoint(codePoint);
                previous = codePoint;
            }
            offset += count;
        }
        return out == null ? value : out.toString();
    }

    public static Component singleLine(Component value) {
        if (value == null) return Component.empty();
        MutableComponent out = Component.empty();
        boolean[] hasOutput = {false};
        boolean[] pendingSpace = {false};
        value.visit((style, text) -> {
            if (text == null || text.isEmpty()) return Optional.empty();
            StringBuilder safe = new StringBuilder(text.length() + 1);
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                int type = Character.getType(codePoint);
                boolean separator = codePoint == '\r' || codePoint == '\n' || codePoint == '\t'
                        || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
                boolean unsafeControl = Character.isISOControl(codePoint) && !separator;
                boolean unsafeFormat = type == Character.FORMAT && codePoint != 0x200C && codePoint != 0x200D;
                if (separator) {
                    pendingSpace[0] = hasOutput[0];
                } else if (!unsafeControl && !unsafeFormat) {
                    if (pendingSpace[0] && codePoint != ' ') safe.append(' ');
                    safe.appendCodePoint(codePoint);
                    hasOutput[0] = true;
                    pendingSpace[0] = false;
                }
                offset += Character.charCount(codePoint);
            }
            if (!safe.isEmpty()) {
                out.append(Component.literal(safe.toString()).setStyle(style != null ? style : Style.EMPTY));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static Metrics measure(TextRenderer renderer, String value, double scale, boolean shadow) {
        String text = singleLine(value);
        if (renderer == null) return new Metrics(text, 0.0, 0.0);

        boolean started = !renderer.isBuilding();
        if (started) renderer.begin(scale, true, false);
        try {
            double width = finiteNonNegative(renderer.getWidth(text, shadow));
            double height = finiteNonNegative(renderer.getHeight(shadow));
            return new Metrics(text, width, height);
        } finally {
            if (started && renderer.isBuilding()) renderer.end();
        }
    }

    public static double width(TextRenderer renderer, String value, double scale, boolean shadow) {
        return measure(renderer, value, scale, shadow).width();
    }

    public static double height(TextRenderer renderer, double scale, boolean shadow) {
        return measure(renderer, "Ag", scale, shadow).height();
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public record Metrics(String text, double width, double height) {
    }
}
