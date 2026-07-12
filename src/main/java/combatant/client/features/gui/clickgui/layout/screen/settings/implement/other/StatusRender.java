/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.other;

import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.render.engine.animation.AnimationUtility;

public final class StatusRender {
    private float stateAnim;

    public void render(float x, float y, float targetState, float scale) {
        stateAnim = AnimationUtility.approach(stateAnim, targetState, 0.22f);

        float w = 16f * scale;
        float h = 8f * scale;
        float knob = 9f * scale;
        float knobX = x + 8f * scale * stateAnim - 0.5f * scale;
        float knobY = y - 0.5f * scale;

        LayoutRender2D.rounded(x, y, w, h, 4f * scale, LayoutRender2D.argb(40, 128, 128, 128));
        LayoutRender2D.rounded(x, y, w, h, 4f * scale, LayoutRender2D.argb(Math.round(100f * stateAnim), 128, 128, 128));
        LayoutRender2D.roundedQuad(
                knobX, knobY, knob, knob, 4.5f * scale,
                LayoutRender2D.argb(255, 61, 67, 71),
                LayoutRender2D.argb(255, 71, 77, 81),
                LayoutRender2D.argb(255, 81, 87, 91),
                LayoutRender2D.argb(255, 91, 97, 101)
        );
        LayoutRender2D.roundedStroke(knobX, knobY, knob, knob, 4.5f * scale, 2f * scale, LayoutRender2D.argb(255, 155, 155, 165));
    }
}
