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

    /**
     * Scales the alpha of a "#RRGGBB" / "#AARRGGBB" colour. Scripted HUD panels call this for
     * every colour on every frame, so it parses in place and, when the colour does not change
     * (typically amount = 1 on a fully visible panel), returns the input instead of a new string.
     */
    public static String alpha(String hex, float amount) {
        if (hex == null || hex.isBlank()) return "#00000000";
        int start = hex.charAt(0) == '#' ? 1 : 0;
        int digits = hex.length() - start;
        if (digits != 6 && digits != 8) return hex;

        int value = 0;
        for (int i = start; i < hex.length(); i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0) return hex;
            value = (value << 4) | digit;
        }
        int argb = digits == 6 ? 0xFF000000 | value : value;
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(amount));
        int scaled = (argb & 0x00FFFFFF) | (a << 24);
        if (scaled == argb && start == 1 && digits == 8 && isUpperHex(hex)) return hex;
        return hex(scaled);
    }

    // hex() emits upper-case digits, so only an upper-case input is already in canonical form.
    private static boolean isUpperHex(String hex) {
        for (int i = 1; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /** Same output as {@code String.format("#%08X", argb)}; called per node per frame. */
    public static String hex(int argb) {
        char[] out = new char[9];
        out[0] = '#';
        for (int i = 8; i >= 1; i--) {
            out[i] = HEX_DIGITS[argb & 0xF];
            argb >>>= 4;
        }
        return new String(out);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
