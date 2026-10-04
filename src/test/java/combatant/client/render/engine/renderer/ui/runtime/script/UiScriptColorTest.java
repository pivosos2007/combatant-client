/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The hand-rolled hex helpers replaced String.format / parseLong on the per-frame HUD path;
 * these pin them to the exact output of the implementation they replaced.
 */
class UiScriptColorTest {

    @Test
    void hexMatchesStringFormat() {
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            int argb = random.nextInt();
            assertEquals(String.format("#%08X", argb), UiScriptColor.hex(argb));
        }
        for (int argb : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x00FFFFFF, 0xFF000000}) {
            assertEquals(String.format("#%08X", argb), UiScriptColor.hex(argb));
        }
    }

    @Test
    void alphaMatchesPreviousImplementation() {
        List<String> inputs = List.of(
                "#FF050608", "#99FFFFFF", "#22ffffff", "#abcdef", "ABCDEF", "#00000000",
                "#FFFFFFFF", "80FF0000", "#12", "#GGGGGG", "", "   ", "#1234567", "#ffAABBcc");
        float[] amounts = {-1.0f, 0.0f, 0.25f, 0.5f, 0.999f, 1.0f, 1.5f};
        for (String input : inputs) {
            for (float amount : amounts) {
                assertEquals(previousAlpha(input, amount), UiScriptColor.alpha(input, amount),
                        () -> "alpha(" + input + ", " + amount + ")");
            }
        }
        Random random = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            String input = String.format("#%08X", random.nextInt());
            float amount = random.nextFloat() * 1.4f - 0.2f;
            assertEquals(previousAlpha(input, amount), UiScriptColor.alpha(input, amount));
        }
    }

    @Test
    void unchangedCanonicalColourIsReturnedAsIs() {
        String colour = "#FF050608";
        assertSame(colour, UiScriptColor.alpha(colour, 1.0f));
    }

    @Test
    void nullAndBlankFallBackToTransparent() {
        assertEquals("#00000000", UiScriptColor.alpha(null, 1.0f));
        assertEquals("#00000000", UiScriptColor.alpha("  ", 1.0f));
    }

    // Verbatim copy of the implementation before the per-frame rewrite.
    private static String previousAlpha(String hex, float amount) {
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
            int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, amount)));
            return String.format("#%08X", (argb & 0x00FFFFFF) | (a << 24));
        } catch (NumberFormatException ignored) {
            return hex;
        }
    }
}
