/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

/**
 * Canonical conversion between authored logical text size and TextRenderer's legacy scale.
 *
 * <p>UI code should express text size in the same logical coordinate units used for layout.
 * Only the text backend boundary should deal in multiplicative renderer scale.</p>
 */
public final class TextSizing {
    public static final float REFERENCE_SIZE = 18.0f;
    public static final float MIN_SIZE = 0.18f;

    private TextSizing() {
    }

    public static float scaleForSize(float size) {
        if (!Float.isFinite(size)) return 1.0f;
        return Math.max(MIN_SIZE, size) / REFERENCE_SIZE;
    }

    public static double scaleForSize(double size) {
        if (!Double.isFinite(size)) return 1.0;
        return Math.max(MIN_SIZE, size) / REFERENCE_SIZE;
    }

    public static float sizeForScale(float scale) {
        if (!Float.isFinite(scale)) return REFERENCE_SIZE;
        return Math.max(0.01f, scale) * REFERENCE_SIZE;
    }
}
