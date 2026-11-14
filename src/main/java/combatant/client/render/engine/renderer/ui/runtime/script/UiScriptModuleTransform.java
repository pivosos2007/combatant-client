/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

import java.util.regex.Pattern;

enum UiScriptModuleTransform {
    ;

    private static final Pattern REMAINING_MODULE_SYNTAX = Pattern.compile(
            "(?m)^\\s*(?:import|export)\\b"
    );

    static String toExecutableScript(String source) {
        String out = source != null ? source : "";
        out = out.replaceAll("(?m)^\\s*import\\s+[^;]+;?\\s*$", "");
        out = out.replace("export default", "globalThis.__ui_default =");
        out = out.replaceAll("(?m)^\\s*export\\s+class\\s+", "class ");
        // Imports are already recursively inlined by UiScriptModuleLoader. Keep named declarations
        // as ordinary script declarations; this supports reusable dependency helpers without
        // pretending the runtime implements full ESM linkage.
        out = out.replaceAll("(?m)^\\s*export\\s+function\\s+", "function ");
        out = out.replaceAll("(?m)^\\s*export\\s+(const|let|var)\\s+", "$1 ");
        out = out.replaceAll("(?m)^\\s*export\\s*\\{[^}]+}\\s*;?\\s*$", "");
        if (UiRuntimeValidation.enabled() && REMAINING_MODULE_SYNTAX.matcher(out).find()) {
            throw UiRuntimeValidation.invalid(
                    "Unsupported UI script module syntax remains after transform. "
                            + "Combatant runtime scripts only support the import/export forms handled by UiScriptModuleTransform."
            );
        }
        return "var module = { exports: {} }; var exports = module.exports;\n" + out + "\n";
    }
}
