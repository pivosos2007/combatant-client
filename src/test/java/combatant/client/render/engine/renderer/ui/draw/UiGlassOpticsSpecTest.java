package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiGlassOpticsSpecTest {
    @Test
    void liquidIsTheDefaultAndFresnelRemainsExplicit() {
        assertEquals(UiGlassOpticsSpec.Mode.LIQUID_REFRACTION,
                UiLiquidGlassMaterial.DEFAULT.optics().mode());
        assertEquals(UiGlassOpticsSpec.Mode.FRESNEL_GLASS,
                UiLiquidGlassMaterial.FRESNEL_GLASS.optics().mode());
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
        assertEquals(-0.25f, spec.refraction());
        assertEquals(0.001f, spec.bevelWidth());
        assertEquals(0.5f, spec.bevelDepth());
        assertEquals(5.0f, spec.interactionStrength());
        assertEquals(0.0f, spec.interactionRadius());
        assertEquals(1.0f, spec.interactionViscosity());
        assertEquals(0.0f, spec.cleanReveal());
    }
}
