/*
 * Combatant Client, Copyright (c) 2026 pivosos2007. GPL-3.0-only.
 */
package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class UiIceMaterialSpecTest {
    @Test
    void oneAppearanceCanBeUsedForSolidAndGlass() {
        UiIceMaterialSpec ice = UiIceMaterialSpec.LIGHT.withSeed(12f).withFreezing(0.6f, 0.3f);
        UiSurfaceMaterialSpec surface = ice.asSurface();

        assertEquals(UiSurfaceMaterialSpec.Mode.ICE, surface.mode());
        assertEquals(ice, surface.ice());
        assertEquals(UiMaterialSpec.Family.SOLID, surface.family());
        assertFalse(surface.samplesBackdrop());
        UiLiquidGlassMaterial glass = UiLiquidGlassMaterial.DEFAULT.withIce(ice);
        assertEquals(UiMaterialSpec.Family.GLASS, glass.family());
        assertEquals("shaders/ui_glass_ice_blend.frag", glass.visual().shader().getPath());
        assertEquals(ice.seed(), glass.visual().parameters().get(0).floatValue());
        assertEquals(ice.density(), glass.visual().parameters().get(1).floatValue());
        assertEquals(ice.displacementPx(), glass.visual().sampleReachPx());
    }

    @Test
    void invalidParameterValuesAreClampedAndNonFiniteValuesSanitized() {
        UiIceMaterialSpec ice = new UiIceMaterialSpec(
                Float.NaN, -1f, Float.POSITIVE_INFINITY, -0.4f,
                4f, 200f, -8f, 1f);
        assertEquals(0f, ice.seed());
        assertEquals(0.5f, ice.density());
        assertEquals(0f, ice.frost());
        assertEquals(0f, ice.cracks());
        assertEquals(1f, ice.blueAbsorption());
        assertEquals(12f, ice.displacementPx());
        assertEquals(0f, ice.facetContrast());
        assertEquals(0.25f, ice.clarity());
    }

    @Test
    void independentSeedsDoNotMutateSharedPreset() {
        UiIceMaterialSpec a = UiIceMaterialSpec.LIGHT.withSeed(1f);
        UiIceMaterialSpec b = UiIceMaterialSpec.LIGHT.withSeed(2f);
        assertNotEquals(a, b);
        assertEquals(0f, UiIceMaterialSpec.LIGHT.seed());
    }
}
