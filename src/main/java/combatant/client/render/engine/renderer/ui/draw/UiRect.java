/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiRect(float x, float y, float width, float height) {
    public static UiRect of(double x, double y, double width, double height) {
        return new UiRect((float) x, (float) y, (float) width, (float) height);
    }

    public boolean empty() {
        return width == 0.0f || height == 0.0f;
    }
}
