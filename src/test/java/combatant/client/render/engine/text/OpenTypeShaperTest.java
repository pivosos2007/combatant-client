/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import org.junit.jupiter.api.Test;
import java.awt.Font;
import static org.junit.jupiter.api.Assertions.*;

class OpenTypeShaperTest {
    private static final OpenTypeShaper SHAPER = OpenTypeShaper.fromFont(new Font("Dialog", Font.PLAIN, 12));

    @Test
    void widthsAndPositionsAreFiniteAndInEmUnits() {
        var result = SHAPER.shape("AV office");
        assertTrue(result.advance() > 0.0 && result.advance() < 15.0);
        assertTrue(result.glyphs().length > 0);
        for (var glyph : result.glyphs()) {
            assertTrue(Double.isFinite(glyph.x()));
            assertTrue(Double.isFinite(glyph.y()));
            assertTrue(glyph.glyphId() >= 0);
        }
    }

    @Test
    void bidiRunsRetainAbsoluteUtf16ClusterOffsets() {
        String text = "abc \u0633\u0644\u0627\u0645 def";
        var result = SHAPER.shape(text);
        assertTrue(result.glyphs().length > 6);
        boolean foundArabic = false;
        for (var glyph : result.glyphs()) {
            assertTrue(glyph.cluster() >= 0 && glyph.cluster() < text.length());
            if (glyph.cluster() >= 4 && glyph.cluster() < 8) {
                foundArabic = true;
                assertTrue(Character.UnicodeScript.of(glyph.codePoint()) == Character.UnicodeScript.ARABIC);
            }
        }
        assertTrue(foundArabic);
    }

    @Test
    void repeatedTextReturnsCachedImmutableLayout() {
        assertSame(SHAPER.shape("same string"), SHAPER.shape("same string"));
        assertEquals(0.0, SHAPER.shape("").advance());
        assertEquals(0, SHAPER.shape("").glyphs().length);
    }

    @Test
    void supplementaryCharacterRemainsOneTextCluster() {
        var layout = SHAPER.shape("A\ud83d\ude00B");
        for (var glyph : layout.glyphs()) {
            assertNotEquals(2, glyph.cluster(), "glyph must not start within surrogate pair");
        }
    }
}
