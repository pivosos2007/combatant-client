/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.action;

public record UiActionRef(String namespace, String name, String argument) {
    public static UiActionRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new UiActionRef("", "", "");
        }
        String text = raw.trim();
        String namespace = "";
        String name = text;
        String argument = "";

        int colon = text.indexOf(':');
        if (colon >= 0) {
            argument = text.substring(colon + 1);
            text = text.substring(0, colon);
        }
        int dot = text.indexOf('.');
        if (dot >= 0) {
            namespace = text.substring(0, dot);
            name = text.substring(dot + 1);
        }
        return new UiActionRef(namespace, name, argument);
    }

    public String key() {
        return namespace.isBlank() ? name : namespace + "." + name;
    }
}
