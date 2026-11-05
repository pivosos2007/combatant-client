/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import java.util.List;

public record UiScriptRenderResult(UiNodeSpec root, UiScriptRuntimeError error,
                                   List<UiScriptDependencies.Dependency> dependencies) {
    public UiScriptRenderResult(UiNodeSpec root, UiScriptRuntimeError error) {
        this(root, error, null);
    }
    public static UiScriptRenderResult ok(UiNodeSpec root) {
        return new UiScriptRenderResult(root, null);
    }

    public static UiScriptRenderResult tracked(UiNodeSpec root, List<UiScriptDependencies.Dependency> dependencies) {
        return new UiScriptRenderResult(root, null, dependencies);
    }

    public static UiScriptRenderResult failed(UiScriptRuntimeError error) {
        return new UiScriptRenderResult(null, error);
    }

    public boolean success() {
        return error == null;
    }
}
