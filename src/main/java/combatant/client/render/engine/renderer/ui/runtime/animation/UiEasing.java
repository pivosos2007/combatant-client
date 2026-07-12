/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.animation;

import combatant.client.render.engine.animation.AnimationUtility;

import java.util.Locale;

public enum UiEasing {
    LINEAR,
    SMOOTHSTEP,
    EASE_IN_CUBIC,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_OUT_BACK,
    EASE_IN_BACK;

    public static UiEasing parse(String value) {
        if (value == null || value.isBlank()) return EASE_OUT_CUBIC;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "linear" -> LINEAR;
            case "smooth", "smoothstep" -> SMOOTHSTEP;
            case "in-cubic", "ease-in-cubic" -> EASE_IN_CUBIC;
            case "out-cubic", "ease-out-cubic" -> EASE_OUT_CUBIC;
            case "in-out-cubic", "ease-in-out-cubic" -> EASE_IN_OUT_CUBIC;
            case "out-back", "ease-out-back" -> EASE_OUT_BACK;
            case "in-back", "ease-in-back" -> EASE_IN_BACK;
            default -> EASE_OUT_CUBIC;
        };
    }

    public float apply(float t) {
        return switch (this) {
            case LINEAR -> AnimationUtility.clamp01(t);
            case SMOOTHSTEP -> AnimationUtility.smoothstep(t);
            case EASE_IN_CUBIC -> AnimationUtility.easeInCubic(t);
            case EASE_OUT_CUBIC -> AnimationUtility.easeOutCubic(t);
            case EASE_IN_OUT_CUBIC -> AnimationUtility.easeInOutCubic(t);
            case EASE_OUT_BACK -> AnimationUtility.easeOutBack(t);
            case EASE_IN_BACK -> AnimationUtility.easeInBack(t);
        };
    }
}
