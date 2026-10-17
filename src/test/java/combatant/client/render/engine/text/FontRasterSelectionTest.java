/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FontRasterSelectionTest {
    @Test void nativeSmallHudUsesBitmap() {
        assertTrue(FontRasterSelection.preferSmallBitmap(1.0, 1.0, false, false));
        assertTrue(FontRasterSelection.preferSmallBitmap(1.0, 1.2, false, false));
    }
    @Test void highDpiOrLargeHudKeepsMsdf() {
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, 2.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.5, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, 1.0, true, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, 1.0, false, true));
    }
    @Test void tinyAndInvalidFootprintsKeepMsdf() {
        assertFalse(FontRasterSelection.preferSmallBitmap(0.25, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(-1, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(Double.NaN, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, Double.POSITIVE_INFINITY, false, false));
    }
}
