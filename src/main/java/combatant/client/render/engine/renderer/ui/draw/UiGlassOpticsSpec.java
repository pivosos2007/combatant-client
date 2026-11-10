/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Optical model data for the shared glass surface pipeline.
 */
public record UiGlassOpticsSpec(Mode mode,
                               float refraction,
                               float bevelWidth,
                               float bevelDepth,
                               float interactionStrength,
                               float interactionRadius,
                               float interactionViscosity,
                               float cleanReveal,
                               float chromaticAberration, float magnification,
                               boolean shapeDeformation,
                               float tiltXDegrees, float tiltYDegrees, boolean specular,
                               ChromaticMotion chromaticMotion,
                               float opticalThickness,
                               float refractiveIndex,
                               float surfaceRoughness,
                               float frost,
                               float filmThicknessNm,
                               float filmStrength,
                               float reactiveCoating) {
    /** liquidGL-derived defaults for Combatant's captured-scene coordinates. */
    public static final UiGlassOpticsSpec LIQUID = new UiGlassOpticsSpec(
            Mode.LIQUID_REFRACTION,
            0.010f,
            0.15f,
            0.055f,
            0.50f,
            0.35f,
            0.65f,
            0.55f, 0.0f, 1.0f, false, 0.0f, 0.0f, true, ChromaticMotion.STATIC
    );

    /** Pre-existing eight-field constructor remains source compatible. */
    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification, boolean shapeDeformation,
                            float tiltXDegrees, float tiltYDegrees, boolean specular,
                            ChromaticMotion chromaticMotion) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                1.0f, 1.46f, 0.0f, 0.0f, 430f, 0.0f, 0.0f);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, 0.0f, 1.0f, false, 0.0f, 0.0f, true,
                ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification,
                            boolean shapeDeformation) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification,
                shapeDeformation, 0.0f, 0.0f, true, ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification,
                            boolean shapeDeformation,
                            float tiltXDegrees, float tiltYDegrees, boolean specular) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification,
                shapeDeformation, tiltXDegrees, tiltYDegrees, specular, ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec {
        mode = mode != null ? mode : Mode.LIQUID_REFRACTION;
        refraction = finiteClamp(refraction, -0.08f, 0.08f);
        bevelWidth = finiteClamp(bevelWidth, 0.001f, 1.0f);
        bevelDepth = finiteClamp(bevelDepth, -0.15f, 0.15f);
        interactionStrength = finiteClamp(interactionStrength, 0.0f, 5.0f);
        interactionRadius = finiteClamp(interactionRadius, 0.0f, 2.0f);
        interactionViscosity = finiteClamp(interactionViscosity, 0.0f, 1.0f);
        cleanReveal = finiteClamp(cleanReveal, 0.0f, 1.0f);
        chromaticAberration = finiteClamp(chromaticAberration, 0.0f, 0.95f);
        magnification = finiteClamp(magnification, 0.65f, 1.8f);
        tiltXDegrees = finiteClamp(tiltXDegrees, -16.0f, 15.75f);
        tiltYDegrees = finiteClamp(tiltYDegrees, -16.0f, 15.75f);
        chromaticMotion = chromaticMotion != null ? chromaticMotion : ChromaticMotion.STATIC;
        opticalThickness = finiteClamp(opticalThickness, 0.0f, 2.5f);
        refractiveIndex = finiteClamp(refractiveIndex, 1.0f, 2.0f);
        surfaceRoughness = finiteClamp(surfaceRoughness, 0.0f, 1.0f);
        frost = finiteClamp(frost, 0.0f, 1.0f);
        filmThicknessNm = finiteClamp(filmThicknessNm, 0.0f, 1200.0f);
        filmStrength = finiteClamp(filmStrength, 0.0f, 1.0f);
        reactiveCoating = finiteClamp(reactiveCoating, 0.0f, 1.0f);
    }

    public UiGlassOpticsSpec withMode(Mode value) {
        return new UiGlassOpticsSpec(value, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    /** Independent optical curvature; does not alter the bevel width or blur. */
    public UiGlassOpticsSpec withRefraction(float value) {
        return new UiGlassOpticsSpec(mode, value, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withBevel(float width, float depth) {
        return new UiGlassOpticsSpec(mode, refraction, width, depth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withFluid(float radius, float viscosity) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, radius, viscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withCleanReveal(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, value,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withInteractionStrength(float strength) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                strength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withChromaticAberration(float aberration) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                aberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withMagnification(float zoom) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, zoom, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withShapeDeformation(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, enabled,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    /** liquidGL-compatible optical tilt, independent of pointer-driven deformation. */
    public UiGlassOpticsSpec withTilt(float xDegrees, float yDegrees) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                xDegrees, yDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withSpecular(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, enabled, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withChromaticMotion(ChromaticMotion motion) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, motion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost,
                filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withOpticalThickness(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                value, refractiveIndex, surfaceRoughness, frost, filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withRefractiveIndex(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, value, surfaceRoughness, frost, filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withSurfaceRoughness(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, value, frost, filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withFrost(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, value, filmThicknessNm, filmStrength, reactiveCoating);
    }

    public UiGlassOpticsSpec withThinFilm(float thicknessNm, float strength) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost, thicknessNm, strength, reactiveCoating);
    }

    public UiGlassOpticsSpec withReactiveCoating(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion,
                opticalThickness, refractiveIndex, surfaceRoughness, frost, filmThicknessNm, filmStrength, value);
    }

    public enum Mode {
        LIQUID_REFRACTION(0),
        REACTIVE_SMOKED(2),
        ETCHED_GLASS(3),
        SOFT_LENS(4);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    public enum ChromaticMotion {
        STATIC(0),
        FLOW(1),
        IMPACT_EDGE(2);

        private final int shaderId;

        ChromaticMotion(int shaderId) {
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
