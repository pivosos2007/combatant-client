/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

enum UiTypeScriptStripper {
    ;

    static String strip(String source) {
        String out = source != null ? source : "";
        out = out.replaceAll("(?m)^\\s*import\\s+type\\s+[^;]+;?\\s*$", "");
        out = out.replaceAll("(?m)^\\s*export\\s+type\\s+[^;]+;?\\s*$", "");
        out = out.replaceAll("(?m)^\\s*type\\s+[A-Za-z_$][\\w$]*\\s*=\\s*[^;]+;?\\s*$", "");
        out = out.replaceAll("\\s+as\\s+const\\b", "");
        out = out.replaceAll(":\\s*[A-Za-z_$][\\w$]*(?:<[^>]+>)?(?=\\s*[,)=;{])", "");
        return out;
    }
}
