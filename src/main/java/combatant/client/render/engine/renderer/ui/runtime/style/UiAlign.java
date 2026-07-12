/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import java.util.Locale;

public enum UiAlign {
    START,
    CENTER,
    END,
    STRETCH;

    public static UiAlign parse(String raw, UiAlign fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return switch (raw.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "CENTER", "MIDDLE" -> CENTER;
            case "END", "RIGHT", "BOTTOM" -> END;
            case "STRETCH", "FILL" -> STRETCH;
            default -> START;
        };
    }
}
