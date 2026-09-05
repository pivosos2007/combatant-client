/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.renderer.Renderer2D;

/** Backend-neutral blur quality used by semantic effect/backdrop commands. */
public enum UiBlurQuality {
    LOW(0, 2),
    MEDIUM(1, 3),
    HIGH(2, 4),
    ULTRA(3, 5);

    public final int id;
    public final int iterations;

    UiBlurQuality(int id, int iterations) {
        this.id = id;
        this.iterations = iterations;
    }

    public static UiBlurQuality fromRenderer(Renderer2D.BlurQuality quality) {
        if (quality == null) return MEDIUM;
        return switch (quality) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case ULTRA -> ULTRA;
        };
    }
}
