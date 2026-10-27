package combatant.client.render.engine.renderer.ui.draw;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiMaterialTextureSpecTest {
    @Test void clampsAndSanitizesEveryControl() {
        var texture = new UiMaterialTextureSpec(Float.NaN, -99f, 99f, Float.POSITIVE_INFINITY);
        assertEquals(16f, texture.tileSizePx());
        assertEquals(0f, texture.normalStrength());
        assertEquals(1f, texture.heightStrength());
        assertEquals(0f, texture.roughnessVariation());
    }

    @Test void immutablePresetAndTextureControlsDoNotLeakToLegacySurfaces() {
        var material = UiSurfaceMaterialSpec.SATIN_TITANIUM
                .withMicrotexture(UiMaterialTextureSpec.DEFAULT
                        .withTileSize(144f)
                        .withNormalStrength(0.54f)
                        .withHeightStrength(0.17f)
                        .withRoughnessVariation(0.29f))
                .withLighting(UiMaterialLightingSpec.CURRENT);
        assertTrue(material.microtextureEnabled());
        assertEquals(144f, material.texture().tileSizePx());
        assertEquals(0.54f, material.texture().normalStrength());
        assertEquals(0.65f, material.lighting().strength());
        assertFalse(UiSurfaceMaterialSpec.SATIN_TITANIUM.microtextureEnabled());
        assertEquals(192f, UiSurfaceMaterialSpec.SATIN_TITANIUM.texture().tileSizePx());
        assertFalse(material.withMicrotexture(false).microtextureEnabled());
    }

    @Test void pbrControlsHaveIndependentFiniteRangesAndPreserveOtherValues() {
        var texture = new UiMaterialTextureSpec(128, .5f, .2f, .7f, 9, -1, Float.NaN, 99, -450);
        assertEquals(1, texture.metallic());
        assertEquals(0, texture.specularStrength());
        assertEquals(0, texture.tintStrength());
        assertEquals(.95f, texture.anisotropy());
        assertEquals(270, texture.directionDegrees());
        var changed = texture.withMetallic(.4f).withSpecularStrength(.8f).withTintStrength(.3f)
                .withAnisotropy(.6f).withDirection(40).withTileSize(256).withHeightStrength(.1f)
                .withNormalStrength(.7f).withRoughnessVariation(.8f);
        assertEquals(new UiMaterialTextureSpec(256, .7f, .1f, .8f, .4f, .8f, .3f, .6f, 40), changed);
        assertEquals(0, UiSurfaceMaterialSpec.GRAPHITE.texture().metallic());
        assertEquals(1, UiSurfaceMaterialSpec.SATIN_TITANIUM.texture().metallic());
        assertEquals(0, UiSurfaceMaterialSpec.CERAMIC.texture().metallic());
    }
}
