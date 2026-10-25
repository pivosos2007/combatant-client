/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Controls physical PBR-map detail independently of the base surface appearance.
 * Packed data: RG=OpenGL normal XY, B=roughness, A=height; color RGB=sRGB, A=metallic.
 */
public record UiMaterialTextureSpec(float tileSizePx, float normalStrength,
                                    float heightStrength, float roughnessVariation,
                                    float metallic, float specularStrength, float tintStrength,
                                    float anisotropy, float directionDegrees) {
    public static final UiMaterialTextureSpec DEFAULT =
            new UiMaterialTextureSpec(128f, 0.32f, 0.12f, 0.19f);

    public UiMaterialTextureSpec(float tileSizePx, float normalStrength, float heightStrength, float roughnessVariation) {
        this(tileSizePx, normalStrength, heightStrength, roughnessVariation, 0f, 0.5f, 1f, 0f, 0f);
    }

    public static UiMaterialTextureSpec forMode(UiSurfaceMaterialSpec.Mode mode) {
        if (mode == null) return DEFAULT;
        return switch (mode) {
            case GRAPHITE -> new UiMaterialTextureSpec(160, .65f, .12f, .5f, 0, .3f, .75f, 0, 0);
            case SOFT_TOUCH -> new UiMaterialTextureSpec(192, .5f, .08f, .4f, 0, .4f, .8f, 0, 0);
            case SATIN_TITANIUM -> new UiMaterialTextureSpec(192, 1, .08f, .7f, 1, .7f, .3f, .35f, 0);
            case BRUSHED_METAL -> new UiMaterialTextureSpec(256, 1, .18f, .8f, 1, .8f, .3f, .8f, 0);
            case CERAMIC -> new UiMaterialTextureSpec(192, .25f, .025f, .3f, 0, .8f, .2f, 0, 0);
            case ICE -> new UiMaterialTextureSpec(256, 1, .18f, .8f, 0, .7f, .15f, 0, 0);
            default -> DEFAULT;
        };
    }

    public UiMaterialTextureSpec {
        tileSizePx = finiteClamp(tileSizePx, 16f, 1024f);
        normalStrength = finiteClamp(normalStrength, 0f, 2f);
        heightStrength = finiteClamp(heightStrength, 0f, 1f);
        roughnessVariation = finiteClamp(roughnessVariation, 0f, 1f);
        metallic = finiteClamp(metallic, 0f, 1f);
        specularStrength = finiteClamp(specularStrength, 0f, 2f);
        tintStrength = finiteClamp(tintStrength, 0f, 1f);
        anisotropy = finiteClamp(anisotropy, 0f, .95f);
        directionDegrees = Float.isFinite(directionDegrees) ? ((directionDegrees % 360f) + 360f) % 360f : 0f;
    }

    public UiMaterialTextureSpec withTileSize(float v) {
        return new UiMaterialTextureSpec(v, normalStrength, heightStrength, roughnessVariation, metallic, specularStrength, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withNormalStrength(float v) {
        return new UiMaterialTextureSpec(tileSizePx, v, heightStrength, roughnessVariation, metallic, specularStrength, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withHeightStrength(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, v, roughnessVariation, metallic, specularStrength, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withRoughnessVariation(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, v, metallic, specularStrength, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withMetallic(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, roughnessVariation, v, specularStrength, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withSpecularStrength(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, roughnessVariation, metallic, v, tintStrength, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withTintStrength(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, roughnessVariation, metallic, specularStrength, v, anisotropy, directionDegrees);
    }
    public UiMaterialTextureSpec withAnisotropy(float v) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, roughnessVariation, metallic, specularStrength, tintStrength, v, directionDegrees);
    }
    public UiMaterialTextureSpec withDirection(float degrees) {
        return new UiMaterialTextureSpec(tileSizePx, normalStrength, heightStrength, roughnessVariation, metallic, specularStrength, tintStrength, anisotropy, degrees);
    }
    private static float finiteClamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.clamp(v, lo, hi) : lo;
    }
}
