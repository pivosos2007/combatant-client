/*
 * Combatant Client, Copyright (c) 2026 pivosos2007. GPL-3.0-only.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Shared appearance for solid ice surfaces and ice/glass composition. */
public record UiIceMaterialSpec(float seed, float density, float frost, float cracks,
                                float blueAbsorption, float displacementPx,
                                float facetContrast, float clarity) implements UiMaterialSpec {
    public static final UiIceMaterialSpec LIGHT = new UiIceMaterialSpec(
            0f, 2.2f, 0.32f, 0.28f, 0.22f, 1.1f, 0.22f, 0.06f);

    public UiIceMaterialSpec {
        seed = Float.isFinite(seed) ? Math.clamp(seed, -4096f, 4096f) : 0f;
        density = clamp(density, 0.5f, 6f);
        frost = clamp(frost, 0f, 1f);
        cracks = clamp(cracks, 0f, 1f);
        blueAbsorption = clamp(blueAbsorption, 0f, 1f);
        displacementPx = clamp(displacementPx, 0f, 12f);
        facetContrast = clamp(facetContrast, 0f, 1f);
        clarity = clamp(clarity, 0f, 0.25f);
    }

    @Override public Family family() { return Family.SOLID; }

    public UiIceMaterialSpec withSeed(float value) {
        return new UiIceMaterialSpec(value, density, frost, cracks, blueAbsorption,
                displacementPx, facetContrast, clarity);
    }

    public UiIceMaterialSpec withFreezing(float frost, float cracks) {
        return new UiIceMaterialSpec(seed, density, frost, cracks, blueAbsorption,
                displacementPx, facetContrast, clarity);
    }

    public UiSurfaceMaterialSpec asSurface() {
        return new UiSurfaceMaterialSpec(UiSurfaceMaterialSpec.Mode.ICE, density, frost,
                cracks, 0f, 0xFFC8E5F1, this);
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
}
