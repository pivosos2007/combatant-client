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
    private static final ThreadLocal<SettingOverlayHost> OVERLAY_HOST = new ThreadLocal<>();

    public static SettingRenderSurface current() {
        return CURRENT.get();
    }

    public static float scale() {
        Float value = SCALE.get();
        if (value == null || !Float.isFinite(value) || value <= 0.0f) return 1.0f;
        return value;
    }

    public static SettingOverlayHost overlayHost() {
        return OVERLAY_HOST.get();
    }

    public static Scope push(SettingRenderSurface surface) {
        return push(surface, 1.0f, null);
    }

    public static Scope push(SettingRenderSurface surface, SettingOverlayHost overlayHost) {
        return push(surface, 1f, overlayHost);
    }

    public static Scope push(SettingRenderSurface surface, float scale) {
        return push(surface, scale, null);
    }

    public static Scope push(SettingRenderSurface surface, float scale, SettingOverlayHost overlayHost) {
        SettingRenderSurface previousSurface = CURRENT.get();
        float previousScale = scale();
        SettingOverlayHost previousOverlayHost = OVERLAY_HOST.get();
        CURRENT.set(surface == null ? SettingRenderSurface.SETTINGS : surface);
        SCALE.set(Float.isFinite(scale) && scale > 0.0f ? scale : 1.0f);
        OVERLAY_HOST.set(overlayHost);
        return new Scope(previousSurface, previousScale, previousOverlayHost);
    }

    public static final class Scope implements AutoCloseable {
        private final SettingRenderSurface previousSurface;
        private final float previousScale;
        private final SettingOverlayHost previousOverlayHost;
        private boolean closed;

        private Scope(SettingRenderSurface previousSurface, float previousScale, SettingOverlayHost previousOverlayHost) {
            this.previousSurface = previousSurface;
            this.previousScale = previousScale;
            this.previousOverlayHost = previousOverlayHost;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            CURRENT.set(previousSurface);
            SCALE.set(previousScale);
            OVERLAY_HOST.set(previousOverlayHost);
        }
    }
}
