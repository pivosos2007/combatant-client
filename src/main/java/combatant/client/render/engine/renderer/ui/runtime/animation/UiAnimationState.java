/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.animation;

import net.minecraft.util.Util;

public final class UiAnimationState {
    private UiAnimationSpec spec;
    private long startMs;

    public void start(UiAnimationSpec spec) {
        this.spec = spec;
        this.startMs = Util.getMillis();
    }

    public UiAnimationSpec spec() {
        return spec;
    }

    public float value() {
        if (spec == null) return 0.0f;
        long now = Util.getMillis();
        long elapsed = Math.max(0L, now - startMs - spec.delayMs());
        long duration = spec.durationMs();
        long cycle = spec.loop() ? elapsed % duration : Math.min(elapsed, duration);
        float t = duration <= 0L ? 1.0f : cycle / (float) duration;
        if (spec.alternate() && spec.loop() && ((elapsed / duration) & 1L) == 1L) {
            t = 1.0f - t;
        }
        float eased = spec.easing().apply(t);
        return spec.from() + (spec.to() - spec.from()) * eased;
    }

    public boolean finished() {
        if (spec == null || spec.loop()) return false;
        return Util.getMillis() - startMs >= spec.delayMs() + spec.durationMs();
    }
}
