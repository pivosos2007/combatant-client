/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.Locale;

public enum UiScriptSourceKind {
    JAVASCRIPT("js"),
    TYPESCRIPT("ts");

    private final String extension;

    UiScriptSourceKind(String extension) {
        this.extension = extension;
    }

    public static UiScriptSourceKind fromPath(String path) {
        if (path == null) return JAVASCRIPT;
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".ts") ? TYPESCRIPT : JAVASCRIPT;
    }

    public String extension() {
        return extension;
    }
}
