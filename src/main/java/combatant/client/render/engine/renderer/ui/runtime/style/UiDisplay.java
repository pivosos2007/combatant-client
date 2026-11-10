/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import java.util.Locale;

/** A null display value retains the node type's default layout. */
public enum UiDisplay {
    FLEX,
    BLOCK;

    public static UiDisplay parse(String value, UiDisplay fallback) {
        if (value == null) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "flex" -> FLEX;
            case "block" -> BLOCK;
            default -> fallback;
        };
    }
}
