/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

/** Hit helpers for runtime-owned scroll chrome. */
public final class UiScrollSupport {
    private UiScrollSupport() {
    }

    public static UiNode scrollbarAt(UiNode root, float x, float y) {
        if (root == null || !root.state().visible()) return null;
        for (int i = root.children().size() - 1; i >= 0; i--) {
            UiNode child = scrollbarAt(root.children().get(i), x, y);
            if (child != null) return child;
        }
        UiScrollbarMetrics metrics = UiScrollbarMetrics.resolve(root);
        return metrics != null && metrics.containsTrack(x, y) ? root : null;
    }
}
