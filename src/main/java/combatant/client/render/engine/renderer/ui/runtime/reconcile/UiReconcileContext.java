/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.reconcile;

import combatant.client.render.engine.renderer.ui.runtime.core.UiLifecycle;

public final class UiReconcileContext {
    private final UiLifecycle lifecycle;
    private final UiDiffStats stats = new UiDiffStats();

    public UiReconcileContext(UiLifecycle lifecycle) {
        this.lifecycle = lifecycle != null ? lifecycle : new UiLifecycle() {
        };
    }

    public UiLifecycle lifecycle() {
        return lifecycle;
    }

    public UiDiffStats stats() {
        return stats;
    }
}
