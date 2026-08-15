/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/** Semantic superellipse profiles so components do not hard-code exponents. */
public enum UiSquircleProfile {
    SOFT(3.0f),
    STANDARD(4.0f),
    TIGHT(5.0f);

    private final float exponent;

    UiSquircleProfile(float exponent) {
        this.exponent = exponent;
    }

    public float exponent() {
        return exponent;
    }
}
