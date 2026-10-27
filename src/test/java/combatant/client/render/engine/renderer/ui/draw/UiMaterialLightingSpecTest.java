/* Combatant Client. GPL-3.0-only. */
package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiMaterialLightingSpecTest {
    @Test void legacyPresetsRemainUnchanged() {
        assertFalse(UiSurfaceMaterialSpec.SATIN_TITANIUM.lighting().enabled());
        assertFalse(UiSurfaceMaterialSpec.SATIN_TITANIUM.microtextureEnabled());
        assertFalse(UiSurfaceMaterialSpec.GRAPHITE.lighting().enabled());
        assertFalse(UiSurfaceMaterialSpec.SOFT_TOUCH.lighting().enabled());
    }

    @Test void lightingIsImmutableAndIndependentOfPointerSpecular() {
        var legacy = UiSurfaceMaterialSpec.SATIN_TITANIUM.withResponse(0.7f);
        var lit = legacy.withLighting(UiMaterialLightingSpec.CURRENT.withStrength(0.65f));
        assertEquals(UiMaterialLightingSpec.Source.CURRENT_TARGET, lit.lighting().source());
        assertEquals(0.65f, lit.lighting().strength());
        assertEquals(0.7f, lit.response());
        assertFalse(legacy.lighting().enabled());
        assertTrue(lit.withMicrotexture(true).microtextureEnabled());
        assertFalse(lit.microtextureEnabled());
        assertEquals(legacy.lighting(), lit.withLighting(UiMaterialLightingSpec.NONE).lighting());
    }

    @Test void invalidParametersCannotProduceInvalidGpuPayload() {
        var spec = new UiMaterialLightingSpec(null, Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(UiMaterialLightingSpec.Source.NONE, spec.source());
        assertEquals(0f, spec.strength());
        assertEquals(0f, spec.samplingRadiusPx());
        var clamped = new UiMaterialLightingSpec(UiMaterialLightingSpec.Source.CAPTURED_SCENE, 9f, 99f);
        assertEquals(1f, clamped.strength());
        assertEquals(32f, clamped.samplingRadiusPx());
    }
}
