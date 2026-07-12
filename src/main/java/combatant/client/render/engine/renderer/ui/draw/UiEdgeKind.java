/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Per-edge modifier for flexible UI boxes.
 */
public enum UiEdgeKind {
    STRAIGHT(0.0f),
    NOTCHED(1.0f),
    INSET(2.0f),
    CUT(3.0f),
    CUSTOM(8.0f);

    private final float shaderCode;

    UiEdgeKind(float shaderCode) {
        this.shaderCode = shaderCode;
    }

    public float shaderCode() {
        return shaderCode;
    }
}
