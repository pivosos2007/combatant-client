/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

/** Utility color helpers shared by Java-side UI script patch builders. */
public final class UiScriptColor {
    private UiScriptColor() {
    }

    public static String alpha(String hex, float amount) {
        if (hex == null || hex.isBlank()) return "#00000000";
        String text = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int argb;
            if (text.length() == 6) {
                argb = 0xFF000000 | Integer.parseUnsignedInt(text, 16);
            } else if (text.length() == 8) {
                argb = (int) Long.parseLong(text, 16);
            } else {
                return hex;
            }
            int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(amount));
            return hex((argb & 0x00FFFFFF) | (a << 24));
        } catch (NumberFormatException ignored) {
            return hex;
        }
    }

    public static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
