/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiMaterialClimateSpecTest {
    @Test
    void dryStateIsNotSubmitted() {
        assertFalse(UiMaterialClimateSpec.DRY.active());
        assertTrue(new UiMaterialClimateSpec(0.02f, 0, 0, 0, 0).active());
    }

    @Test
    void invalidAndExcessiveValuesAreBounded() {
        var values = new UiMaterialClimateSpec(Float.NaN, Float.POSITIVE_INFINITY,
                -0.5f, 1.5f, 0.35f);
        assertEquals(0f, values.rain());
        assertEquals(0f, values.waterExit());
        assertEquals(0f, values.frost());
        assertEquals(1f, values.snow());
        assertEquals(0.35f, values.condensation());
    }
}
