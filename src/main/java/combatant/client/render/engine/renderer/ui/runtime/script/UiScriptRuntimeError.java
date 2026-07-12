/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.io.PrintWriter;
import java.io.StringWriter;

public record UiScriptRuntimeError(UiScriptModuleId moduleId, String phase, String message, Throwable cause) {
    public UiScriptRuntimeError {
        phase = phase != null ? phase : "";
        message = message != null ? message : "";
    }

    public String stackTrace() {
        if (cause == null) return "";
        StringWriter out = new StringWriter(2048);
        cause.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
