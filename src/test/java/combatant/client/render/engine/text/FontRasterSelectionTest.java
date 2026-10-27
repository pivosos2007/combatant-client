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
        assertFalse(FontRasterSelection.preferSmallBitmap(2.0, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, 1.0, true, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, 1.0, false, true));
    }
    @Test void tinyTextUsesPhysicalMasksWhileInvalidFootprintsKeepMsdf() {
        assertTrue(FontRasterSelection.preferSmallBitmap(0.25, 1.0, false, false));
        assertTrue(FontRasterSelection.preferSmallBitmap(10.0 / 18.0, 1.0, false, false));
        assertTrue(FontRasterSelection.preferSmallBitmap(10.0 / 18.0, 2.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(-1, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(Double.NaN, 1.0, false, false));
        assertFalse(FontRasterSelection.preferSmallBitmap(1.0, Double.POSITIVE_INFINITY, false, false));
    }

    @Test void sameFramebufferFootprintUsesSameBackendRegardlessOfLogicalScale() {
        for (double physicalHeight : new double[]{8, 10, 12, 18, 24, 32, 48}) {
            boolean nativeChoice = FontRasterSelection.preferSmallBitmap(physicalHeight / 18, 1, false, false);
            assertEquals(nativeChoice, FontRasterSelection.preferSmallBitmap(physicalHeight / 36, 2, false, false));
        }
    }
}
