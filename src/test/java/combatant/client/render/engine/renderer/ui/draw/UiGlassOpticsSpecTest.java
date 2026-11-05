/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiGlassOpticsSpecTest {
    @Test
    void liquidIsTheOnlyDefaultOpticalModel() {
        assertEquals(UiGlassOpticsSpec.Mode.LIQUID_REFRACTION,
                UiLiquidGlassMaterial.DEFAULT.optics().mode());
        assertEquals(0.12f, UiLiquidGlassMaterial.DEFAULT.tintStrength());
        assertEquals(1.0f, UiLiquidGlassMaterial.DEFAULT.withTintStrength(4.0f).tintStrength());
    }

    @Test
    void clampsAuthoredOpticalParameters() {
        UiGlassOpticsSpec spec = new UiGlassOpticsSpec(
                null,
                Float.POSITIVE_INFINITY,
                -1.0f,
                4.0f,
                9.0f,
                -2.0f,
                3.0f,
                -4.0f
        );

        assertEquals(UiGlassOpticsSpec.Mode.LIQUID_REFRACTION, spec.mode());
        assertEquals(-0.08f, spec.refraction());
        assertEquals(0.001f, spec.bevelWidth());
        assertEquals(0.15f, spec.bevelDepth());
        assertEquals(5.0f, spec.interactionStrength());
        assertEquals(0.0f, spec.interactionRadius());
        assertEquals(1.0f, spec.interactionViscosity());
        assertEquals(0.0f, spec.cleanReveal());
    }
}
