/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/** One vec4 per vertex for the hot 2D SDF shape family. */
public record UiFastShapeParams(float kind, float shape, float strokeWidth, float flags) {
    private static final int FLAG_BITS = 2;
    private static final int FLAG_MASK = (1 << FLAG_BITS) - 1;
    private static final float SOFTNESS_STEPS = 16.0f;
    public static final float KIND_ROUNDED = 1.0f;
    public static final float KIND_SQUIRCLE = 2.0f;
    public static final float FLAG_FILL = 1.0f;
    public static final float FLAG_INNER_STROKE = 2.0f;

    public static UiFastShapeParams squircle(UiBoxShape box, UiStroke stroke, boolean fill) {
        if (box == null || !box.isSquircle()) {
            throw new IllegalArgumentException("Fast squircle params require a squircle UiBoxShape");
        }
        UiStroke safeStroke = stroke != null ? stroke : UiStroke.NONE;
        return new UiFastShapeParams(
                KIND_SQUIRCLE,
                box.squircleExponent(),
                fill ? 0.0f : safeStroke.thickness(),
                fill ? FLAG_FILL : 0.0f
        );
    }

    public static UiFastShapeParams rounded(float radius, UiStroke stroke, boolean fill) {
        return rounded(radius, stroke, fill, 0.0f);
    }

    public static UiFastShapeParams rounded(float radius, UiStroke stroke, boolean fill, float softness) {
        UiStroke safeStroke = stroke != null ? stroke : UiStroke.NONE;
        int flagBits = fill ? (int) FLAG_FILL : (int) FLAG_INNER_STROKE;
        return new UiFastShapeParams(
                KIND_ROUNDED,
                Math.max(0.0f, radius),
                fill ? 0.0f : safeStroke.thickness(),
                packFlags(flagBits, softness)
        );
    }

    public boolean fill() {
        return (Math.round(flags) & FLAG_MASK & (int) FLAG_FILL) != 0;
    }

    private static float packFlags(int flags, float softness) {
        int quantizedSoftness = Math.max(0, Math.min(1023, Math.round(Math.max(0.0f, softness) * SOFTNESS_STEPS)));
        return (quantizedSoftness << FLAG_BITS) | (flags & FLAG_MASK);
    }
}
