/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiStroke(float thickness, UiPathCap cap, UiPathJoin join) {
    public static final UiStroke NONE = new UiStroke(0f, UiPathCap.BUTT, UiPathJoin.MITER);

    public static UiStroke of(double thickness) {
        return new UiStroke((float) Math.max(0.0, thickness), UiPathCap.BUTT, UiPathJoin.MITER);
    }

    public UiStroke withRoundCapsAndJoins() {
        return new UiStroke(thickness, UiPathCap.ROUND, UiPathJoin.ROUND);
    }

    public boolean enabled() {
        return thickness > 0f;
    }
}
