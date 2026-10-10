/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Optical model data for the shared glass surface pipeline.
 *
 * <p>The mode changes only how the common SDF, capture and prepared-blur inputs are sampled. It
 * does not select another renderer. Rectangles, primitives and compound/metaball fields all consume
 * the same specification.</p>
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
                               float tiltXDegrees, float tiltYDegrees, boolean specular) {
    /** liquidGL-derived defaults for Combatant's captured-scene coordinates. */
    public static final UiGlassOpticsSpec LIQUID = new UiGlassOpticsSpec(
            Mode.LIQUID_REFRACTION,
            0.028f,
            0.15f,
            0.095f,
            0.50f,
            0.35f,
            0.65f,
            0.55f, 0.0f, 1.0f, false, 0.0f, 0.0f, true
    );

    /** Pre-existing Combatant Fresnel/mirror model. */
    public static final UiGlassOpticsSpec FRESNEL_GLASS = new UiGlassOpticsSpec(
            Mode.FRESNEL_GLASS,
            0.0f,
            0.15f,
            0.0f,
            0.0f,
            0.0f,
            0.65f,
            0.045f, 0.0f, 1.0f, false, 0.0f, 0.0f, true
    );

    /** Pre-existing eight-field constructor remains source compatible. */
    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, 0.0f, 1.0f, false, 0.0f, 0.0f, true);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification,
                            boolean shapeDeformation) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification,
                shapeDeformation, 0.0f, 0.0f, true);
    }

    public UiGlassOpticsSpec {
        mode = mode != null ? mode : Mode.LIQUID_REFRACTION;
        refraction = finiteClamp(refraction, -0.25f, 0.25f);
        bevelWidth = finiteClamp(bevelWidth, 0.001f, 1.0f);
        bevelDepth = finiteClamp(bevelDepth, -0.5f, 0.5f);
        interactionStrength = finiteClamp(interactionStrength, 0.0f, 5.0f);
        interactionRadius = finiteClamp(interactionRadius, 0.0f, 2.0f);
        interactionViscosity = finiteClamp(interactionViscosity, 0.0f, 1.0f);
        cleanReveal = finiteClamp(cleanReveal, 0.0f, 1.0f);
        chromaticAberration = finiteClamp(chromaticAberration, 0.0f, 0.95f);
        magnification = finiteClamp(magnification, 0.65f, 1.8f);
        tiltXDegrees = finiteClamp(tiltXDegrees, -16.0f, 15.75f);
        tiltYDegrees = finiteClamp(tiltYDegrees, -16.0f, 15.75f);
    }

    public UiGlassOpticsSpec withMode(Mode value) {
        return new UiGlassOpticsSpec(value, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    /** Independent optical curvature; does not alter the bevel width or blur. */
    public UiGlassOpticsSpec withRefraction(float value) {
        return new UiGlassOpticsSpec(mode, value, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    public UiGlassOpticsSpec withCleanReveal(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, value,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    public UiGlassOpticsSpec withInteractionStrength(float strength) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                strength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    public UiGlassOpticsSpec withChromaticAberration(float aberration) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                aberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    public UiGlassOpticsSpec withMagnification(float zoom) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, zoom, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular);
    }

    public UiGlassOpticsSpec withShapeDeformation(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, enabled,
                tiltXDegrees, tiltYDegrees, specular);
    }

    /** liquidGL-compatible optical tilt, independent of pointer-driven deformation. */
    public UiGlassOpticsSpec withTilt(float xDegrees, float yDegrees) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                xDegrees, yDegrees, specular);
    }

    public UiGlassOpticsSpec withSpecular(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, enabled);
    }

    public enum Mode {
        LIQUID_REFRACTION(0),
        FRESNEL_GLASS(1),
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

    private static float finiteClamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
