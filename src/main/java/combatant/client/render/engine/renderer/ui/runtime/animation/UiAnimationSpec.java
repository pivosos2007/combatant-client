/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.animation;

public record UiAnimationSpec(String property,
                              float from,
                              float to,
                              long durationMs,
                              long delayMs,
                              UiEasing easing,
                              boolean loop,
                              boolean alternate) {
    public UiAnimationSpec {
        property = property != null ? property : "";
        durationMs = Math.max(1L, durationMs);
        delayMs = Math.max(0L, delayMs);
        easing = easing != null ? easing : UiEasing.EASE_OUT_CUBIC;
    }
}
