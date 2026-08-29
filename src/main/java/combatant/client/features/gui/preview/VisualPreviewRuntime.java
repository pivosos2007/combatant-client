/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import combatant.client.util.screen.ClientScreen;

/** Single runtime guard for world suppression and preview-aware render hooks. */
public enum VisualPreviewRuntime {
    ;

    private static final ThreadLocal<Integer> SUBJECT_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static boolean isActive() {
        return ClientScreen.current() instanceof VisualPreviewScreen;
    }

    public static VisualPreviewScreen activeScreen() {
        return ClientScreen.current() instanceof VisualPreviewScreen screen ? screen : null;
    }

    public static boolean isRenderingSubject() {
        return SUBJECT_RENDER_DEPTH.get() > 0;
    }

    /** True while the named module owns the active preview screen. */
    public static boolean isPreviewingModule(String moduleId) {
        if (moduleId == null) return false;
        VisualPreviewScreen screen = activeScreen();
        if (screen == null) return false;
        return ("hands:" + moduleId.trim().toLowerCase(java.util.Locale.ROOT))
                .equals(screen.provider().id());
    }

    public static Scope enterSubjectRender() {
        SUBJECT_RENDER_DEPTH.set(SUBJECT_RENDER_DEPTH.get() + 1);
        return new Scope();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            int next = Math.max(0, SUBJECT_RENDER_DEPTH.get() - 1);
            if (next == 0) SUBJECT_RENDER_DEPTH.remove();
            else SUBJECT_RENDER_DEPTH.set(next);
        }
    }
}
