/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

public enum UiThemeRegistry {
    ;
    private static UiTheme current = new UiTheme("default", 1);

    public static UiTheme current() {
        return current;
    }

    public static void setCurrent(UiTheme theme) {
        current = theme != null ? theme : new UiTheme("default", 1);
    }
}
