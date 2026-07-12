/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

public enum SettingRenderContext {
    ;
    private static final ThreadLocal<SettingRenderSurface> CURRENT =
            ThreadLocal.withInitial(() -> SettingRenderSurface.SETTINGS);
    private static final ThreadLocal<Float> SCALE =
            ThreadLocal.withInitial(() -> 1.0f);

    public static SettingRenderSurface current() {
        return CURRENT.get();
    }

    public static float scale() {
        Float value = SCALE.get();
        if (value == null || !Float.isFinite(value) || value <= 0.0f) return 1.0f;
        return value;
    }

    public static Scope push(SettingRenderSurface surface) {
        return push(surface, 1.0f);
    }

    public static Scope push(SettingRenderSurface surface, float scale) {
        SettingRenderSurface previousSurface = CURRENT.get();
        float previousScale = scale();
        CURRENT.set(surface == null ? SettingRenderSurface.SETTINGS : surface);
        SCALE.set(Float.isFinite(scale) && scale > 0.0f ? scale : 1.0f);
        return new Scope(previousSurface, previousScale);
    }

    public static final class Scope implements AutoCloseable {
        private final SettingRenderSurface previousSurface;
        private final float previousScale;
        private boolean closed;

        private Scope(SettingRenderSurface previousSurface, float previousScale) {
            this.previousSurface = previousSurface;
            this.previousScale = previousScale;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            CURRENT.set(previousSurface);
            SCALE.set(previousScale);
        }
    }
}
