/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

public final class UiFocusManager {
    private UiNode focused;

    public UiNode focused() {
        return focused;
    }

    public void focus(UiNode node) {
        if (focused == node) return;
        if (focused != null) focused.state().setFocused(false);
        focused = node;
        if (focused != null) focused.state().setFocused(true);
    }
}
