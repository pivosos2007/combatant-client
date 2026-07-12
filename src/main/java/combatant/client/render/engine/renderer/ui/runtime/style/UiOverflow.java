/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import java.util.Locale;

public enum UiOverflow {
    VISIBLE,
    HIDDEN,
    SCROLL_X,
    SCROLL_Y,
    SCROLL;

    public static UiOverflow parse(String raw, UiOverflow fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return switch (raw.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "HIDDEN", "CLIP" -> HIDDEN;
            case "SCROLL_X", "X" -> SCROLL_X;
            case "SCROLL_Y", "Y" -> SCROLL_Y;
            case "SCROLL", "AUTO" -> SCROLL;
            default -> VISIBLE;
        };
    }

    public boolean clips() {
        return this != VISIBLE;
    }
}
