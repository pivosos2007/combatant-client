/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.util;

import combatant.client.render.engine.animation.AnimationUtility;

public enum ClickGuiMath {
    ;

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float lerp(float a, float b, float t) {
        return AnimationUtility.lerp(a, b, t);
    }

    public static float animateTo(float value, float target, float speed) {
        return AnimationUtility.approach(value, target, speed);
    }

    public static boolean insideRect(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static float rectOverlap(float ax, float ay, float aw, float ah,
                                    float bx, float by, float bw, float bh) {
        float ix = Math.max(ax, bx);
        float iy = Math.max(ay, by);
        float ax2 = Math.min(ax + aw, bx + bw);
        float ay2 = Math.min(ay + ah, by + bh);
        if (ax2 <= ix || ay2 <= iy) return 0f;
        return (ax2 - ix) * (ay2 - iy);
    }
}

