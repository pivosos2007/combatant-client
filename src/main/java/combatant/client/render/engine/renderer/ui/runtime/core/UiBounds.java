/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

public record UiBounds(float x, float y, float width, float height) {
    public static final UiBounds ZERO = new UiBounds(0.0f, 0.0f, 0.0f, 0.0f);

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }
}

