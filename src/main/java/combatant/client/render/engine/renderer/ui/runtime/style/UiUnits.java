/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.text.TextSizing;

/**
 * Script-facing UI unit conversions.
 *
 * <p>Layout coordinates, dimensions, spacing, radii and authored font sizes all live in the same
 * logical UI coordinate space. Text backends still consume their historical multiplicative scale,
 * but that conversion is intentionally kept below the script/style boundary.</p>
 */
public final class UiUnits {
    /** A text backend scale of 1.0 corresponds to an authored 18-unit font size. */
    public static final float FONT_REFERENCE_SIZE = TextSizing.REFERENCE_SIZE;
    public static final float MIN_FONT_SIZE = TextSizing.MIN_SIZE;

    private UiUnits() {
    }

    public static float fontScale(float fontSize) {
        return TextSizing.scaleForSize(fontSize);
    }

    public static float fontSize(float legacyScale) {
        return TextSizing.sizeForScale(legacyScale);
    }
}
