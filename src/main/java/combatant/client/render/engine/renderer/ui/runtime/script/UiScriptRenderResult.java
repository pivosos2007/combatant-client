/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

public record UiScriptRenderResult(UiNodeSpec root, UiScriptRuntimeError error) {
    public static UiScriptRenderResult ok(UiNodeSpec root) {
        return new UiScriptRenderResult(root, null);
    }

    public static UiScriptRenderResult failed(UiScriptRuntimeError error) {
        return new UiScriptRenderResult(null, error);
    }

    public boolean success() {
        return error == null;
    }
}
