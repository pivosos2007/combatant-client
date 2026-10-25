/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Shared appearance for solid ice surfaces and ice/glass composition. */
public record UiIceMaterialSpec(float seed, float density, float frost, float cracks,
                                float blueAbsorption, float displacementPx,
                                float facetContrast, float clarity,
                                UiMaterialTextureSpec texture) implements UiMaterialSpec {
    public static final UiIceMaterialSpec LIGHT = new UiIceMaterialSpec(
            0f, 2.2f, 0.32f, 0.28f, 0.22f, 1.1f, 0.22f, 0.06f);

    public UiIceMaterialSpec(float seed, float density, float frost, float cracks,
                             float blueAbsorption, float displacementPx, float facetContrast, float clarity) {
        this(seed, density, frost, cracks, blueAbsorption, displacementPx, facetContrast, clarity,
                UiMaterialTextureSpec.forMode(UiSurfaceMaterialSpec.Mode.ICE));
    }

    public UiIceMaterialSpec {
        seed = Float.isFinite(seed) ? Math.clamp(seed, -4096f, 4096f) : 0f;
        density = clamp(density, 0.5f, 6f);
        frost = clamp(frost, 0f, 1f);
        cracks = clamp(cracks, 0f, 1f);
        blueAbsorption = clamp(blueAbsorption, 0f, 1f);
        displacementPx = clamp(displacementPx, 0f, 12f);
        facetContrast = clamp(facetContrast, 0f, 1f);
        clarity = clamp(clarity, 0f, 0.25f);
        texture = texture != null ? texture : UiMaterialTextureSpec.forMode(UiSurfaceMaterialSpec.Mode.ICE);
    }

    @Override public Family family() { return Family.SOLID; }

    public UiIceMaterialSpec withSeed(float value) {
        return new UiIceMaterialSpec(value, density, frost, cracks, blueAbsorption,
                displacementPx, facetContrast, clarity, texture);
    }

    public UiIceMaterialSpec withFreezing(float frost, float cracks) {
        return new UiIceMaterialSpec(seed, density, frost, cracks, blueAbsorption,
                displacementPx, facetContrast, clarity, texture);
    }

    public UiIceMaterialSpec withTexture(UiMaterialTextureSpec value) {
        return new UiIceMaterialSpec(seed, density, frost, cracks, blueAbsorption,
                displacementPx, facetContrast, clarity, value);
    }

    public UiSurfaceMaterialSpec asSurface() {
        return new UiSurfaceMaterialSpec(UiSurfaceMaterialSpec.Mode.ICE, density, frost,
                cracks, 0f, 0xFFC8E5F1, this).withMicrotexture(texture);
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
}
