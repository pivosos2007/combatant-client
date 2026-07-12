/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;

public record UiScriptModule(UiScriptModuleId id, UiScriptSourceKind sourceKind, String source, UiProps metadata) {
    public UiScriptModule(UiScriptModuleId id, String source, UiProps metadata) {
        this(id, UiScriptSourceKind.fromPath(id != null ? id.path() : ""), source, metadata);
    }

    public UiScriptModule {
        id = id != null ? id : new UiScriptModuleId("combatant", "main");
        sourceKind = sourceKind != null ? sourceKind : UiScriptSourceKind.fromPath(id.path());
        source = source != null ? source : "";
        metadata = metadata != null ? metadata : UiProps.EMPTY;
    }

    public UiScriptModuleId getId() {
        return id;
    }
}
