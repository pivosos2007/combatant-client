/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

public record UiStyleToken(String raw, String variant, String name, String value) {
    public static UiStyleToken parse(String raw) {
        String text = raw != null ? raw.trim() : "";
        String variant = "";
        int colon = text.indexOf(':');
        if (colon > 0) {
            variant = text.substring(0, colon);
            text = text.substring(colon + 1);
        }
        int dash = text.indexOf('-');
        String name = dash > 0 ? text.substring(0, dash) : text;
        String value = dash > 0 ? text.substring(dash + 1) : "";
        return new UiStyleToken(raw, variant, name, value);
    }
}
