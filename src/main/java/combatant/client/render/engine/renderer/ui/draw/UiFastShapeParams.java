/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/** One vec4 per vertex for the hot 2D SDF shape family. */
public record UiFastShapeParams(float kind, float shape, float strokeWidth, float flags) {
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
        UiStroke safeStroke = stroke != null ? stroke : UiStroke.NONE;
        return new UiFastShapeParams(
                KIND_ROUNDED,
                Math.max(0.0f, radius),
                fill ? 0.0f : safeStroke.thickness(),
                fill ? FLAG_FILL : FLAG_INNER_STROKE
        );
    }

    public boolean fill() {
        return (Math.round(flags) & (int) FLAG_FILL) != 0;
    }
}
