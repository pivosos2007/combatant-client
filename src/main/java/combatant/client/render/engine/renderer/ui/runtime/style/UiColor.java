/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

public enum UiColor {
    ;

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255.0f);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int multiplyAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 0xFF;
        int a = Math.round(base * clamp01(alpha));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int parse(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String text = value.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        try {
            if (text.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(text, 16);
            }
            if (text.length() == 8) {
                return (int) Long.parseLong(text, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
