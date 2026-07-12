/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

enum UiScriptModuleTransform {
    ;

    static String toExecutableScript(String source) {
        String out = source != null ? source : "";
        out = out.replaceAll("(?m)^\\s*import\\s+[^;]+;?\\s*$", "");
        out = out.replace("export default", "globalThis.__ui_default =");
        out = out.replaceAll("(?m)^\\s*export\\s+class\\s+", "class ");
        out = out.replaceAll("(?m)^\\s*export\\s+function\\s+buildTemplate\\s*\\(", "function buildTemplate(");
        out = out.replaceAll("(?m)^\\s*export\\s+function\\s+render\\s*\\(", "function render(");
        out = out.replaceAll("(?m)^\\s*export\\s+(const|let|var)\\s+render\\s*=", "var render =");
        out = out.replaceAll("(?m)^\\s*export\\s+(const|let|var)\\s+meta\\s*=", "$1 meta =");
        out = out.replaceAll("(?m)^\\s*export\\s*\\{[^}]+}\\s*;?\\s*$", "");
        return "var module = { exports: {} }; var exports = module.exports;\n" + out + "\n";
    }
}
