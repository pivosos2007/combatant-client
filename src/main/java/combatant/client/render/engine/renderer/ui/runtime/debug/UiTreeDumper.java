/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.debug;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

public enum UiTreeDumper {
    ;

    public static String dump(UiNode root) {
        StringBuilder out = new StringBuilder();
        dump(root, out, 0);
        return out.toString();
    }

    private static void dump(UiNode node, StringBuilder out, int depth) {
        if (node == null) {
            out.append("<empty>\n");
            return;
        }
        out.append("  ".repeat(Math.max(0, depth)));
        UiBounds bounds = node.bounds();
        out.append(node.type())
                .append(" key=").append(node.key())
                .append(" id=").append(node.runtimeId())
                .append(" bounds=")
                .append(bounds.x()).append(',')
                .append(bounds.y()).append(' ')
                .append(bounds.width()).append('x')
                .append(bounds.height())
                .append(" measured=")
                .append(node.measuredWidth()).append('x')
                .append(node.measuredHeight());
        if (node.state().contentWidth() > 0.0f || node.state().contentHeight() > 0.0f) {
            out.append(" content=")
                    .append(node.state().contentWidth()).append('x')
                    .append(node.state().contentHeight());
        }
        if (node.state().scrollX() > 0.0f || node.state().scrollY() > 0.0f) {
            out.append(" scroll=")
                    .append(node.state().scrollX()).append(',')
                    .append(node.state().scrollY());
        }
        if (!node.styleClass().isBlank()) {
            out.append(" class=\"").append(node.styleClass()).append('"');
        }
        if (node.lastError() != null && !node.lastError().isBlank()) {
            out.append(" error=\"").append(node.lastError()).append('"');
        }
        out.append('\n');
        for (UiNode child : node.children()) {
            dump(child, out, depth + 1);
        }
    }
}
