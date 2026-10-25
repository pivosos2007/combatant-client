/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiSurfaceMaterialSpec(Mode mode,
                                    float detailScale,
                                    float roughness,
                                    float bevelStrength,
                                    float response,
                                    int accentArgb,
                                    UiIceMaterialSpec ice,
                                    UiMaterialLightingSpec lighting,
                                    boolean microtextureEnabled,
                                    UiMaterialTextureSpec texture) implements UiMaterialSpec {
    @Override public Family family() { return Family.SOLID; }
    @Override public boolean samplesBackdrop() { return lighting != null && lighting.enabled(); }

    public static final UiSurfaceMaterialSpec ICE = UiIceMaterialSpec.LIGHT.asSurface();

    public UiSurfaceMaterialSpec(Mode mode, float detailScale, float roughness,
                                 float bevelStrength, float response, int accentArgb,
                                 UiIceMaterialSpec ice) {
        this(mode, detailScale, roughness, bevelStrength, response, accentArgb,
                ice, UiMaterialLightingSpec.NONE, false, UiMaterialTextureSpec.forMode(mode));
    }

    public UiSurfaceMaterialSpec(Mode mode, float detailScale, float roughness,
                                 float bevelStrength, float response, int accentArgb) {
        this(mode, detailScale, roughness, bevelStrength, response, accentArgb,
                UiIceMaterialSpec.LIGHT, UiMaterialLightingSpec.NONE, false, UiMaterialTextureSpec.forMode(mode));
    }

    public UiSurfaceMaterialSpec(Mode mode, float detailScale, float roughness,
                                 float bevelStrength, float response, int accentArgb,
                                 UiIceMaterialSpec ice, UiMaterialLightingSpec lighting) {
        this(mode, detailScale, roughness, bevelStrength, response, accentArgb,
                ice, lighting, false, UiMaterialTextureSpec.forMode(mode));
    }

    public UiSurfaceMaterialSpec(Mode mode, float detailScale, float roughness,
                                 float bevelStrength, float response, int accentArgb,
                                 UiIceMaterialSpec ice, UiMaterialLightingSpec lighting,
                                 boolean microtextureEnabled) {
        this(mode, detailScale, roughness, bevelStrength, response, accentArgb,
                ice, lighting, microtextureEnabled, UiMaterialTextureSpec.forMode(mode));
    }

    public static final UiSurfaceMaterialSpec GRAPHITE = new UiSurfaceMaterialSpec(
            Mode.GRAPHITE, 0.36f, 0.88f, 0.24f, 0.0f, 0xFF77838F);
    public static final UiSurfaceMaterialSpec CERAMIC = new UiSurfaceMaterialSpec(
            Mode.CERAMIC, 0.22f, 0.22f, 0.65f, 0.0f, 0xFFE2E8E9);
    public static final UiSurfaceMaterialSpec BRUSHED_METAL = new UiSurfaceMaterialSpec(
            Mode.BRUSHED_METAL, 0.22f, 0.53f, 0.48f, 0.0f, 0xFFB3C7D2);
    public static final UiSurfaceMaterialSpec SATIN_TITANIUM = new UiSurfaceMaterialSpec(
            Mode.SATIN_TITANIUM, 0.18f, 0.42f, 0.55f, 0.0f, 0xFFDCE7F2);
    public static final UiSurfaceMaterialSpec SOFT_TOUCH = new UiSurfaceMaterialSpec(
            Mode.SOFT_TOUCH, 0.34f, 0.86f, 0.24f, 0.0f, 0xFFB8C4D0);
    public static final UiSurfaceMaterialSpec PHOSPHOR_LED = new UiSurfaceMaterialSpec(
            Mode.PHOSPHOR_LED, 4.0f, 0.13f, 0.30f, 0.0f, 0xFF7FA2FF);

    public UiSurfaceMaterialSpec {
        ice = ice != null ? ice : UiIceMaterialSpec.LIGHT;
        lighting = lighting != null ? lighting : UiMaterialLightingSpec.NONE;
        texture = texture != null ? texture : mode == Mode.ICE ? ice.texture() : UiMaterialTextureSpec.forMode(mode);
        mode = mode != null ? mode : Mode.SOFT_TOUCH;
        if (mode == Mode.ICE) microtextureEnabled = true;
        detailScale = finiteClamp(detailScale, 0.01f, 64.0f);
        roughness = finiteClamp(roughness, 0.0f, 1.0f);
        bevelStrength = finiteClamp(bevelStrength, 0.0f, 1.0f);
        response = finiteClamp(response, 0.0f, 1.0f);
    }

    public UiSurfaceMaterialSpec withAccent(int argb) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength, response, argb, ice, lighting, microtextureEnabled, texture);
    }

    public UiSurfaceMaterialSpec withResponse(float value) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength, value, accentArgb, ice, lighting, microtextureEnabled, texture);
    }

    public UiSurfaceMaterialSpec withParameters(float scale, float roughnessValue,
                                                float bevel, float responseValue) {
        UiIceMaterialSpec appearance = mode == Mode.ICE
                ? new UiIceMaterialSpec(ice.seed(), scale, roughnessValue, bevel,
                        ice.blueAbsorption(), ice.displacementPx(), ice.facetContrast(), ice.clarity(), texture) : ice;
        return new UiSurfaceMaterialSpec(mode, scale, roughnessValue, bevel, responseValue, accentArgb, appearance, lighting, microtextureEnabled, texture);
    }

    /** Add environment lighting without changing the legacy pointer highlight. */
    public UiSurfaceMaterialSpec withLighting(UiMaterialLightingSpec value) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength,
                response, accentArgb, ice, value, microtextureEnabled, texture);
    }

    /** Optional tile-based micro-roughness; existing materials remain visually unchanged. */
    public UiSurfaceMaterialSpec withMicrotexture(boolean enabled) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength,
                response, accentArgb, ice, lighting, enabled, texture);
    }

    /** Enables source-backed PBR detail; a missing or malformed set is a render error. */
    public UiSurfaceMaterialSpec withMicrotexture(UiMaterialTextureSpec settings) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength,
                response, accentArgb, ice, lighting, true,
                settings != null ? settings : UiMaterialTextureSpec.forMode(mode));
    }

    public enum Mode {
        SATIN_TITANIUM(1),
        SOFT_TOUCH(2),
        PHOSPHOR_LED(3),
        GRAPHITE(4),
        CERAMIC(5),
        BRUSHED_METAL(6),
        ICE(7),
        CELLULAR_STRUCTURE(8),
        CLIMATE_LAYER(9);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    private static float finiteClamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
