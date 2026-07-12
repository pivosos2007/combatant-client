/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

/**
 * Preferred glyph backend. Placement is handled by TextPlacementMode, not by this enum.
 */
public enum TextBackendPreference {
    AUTO,
    VANILLA_SODIUM,
    BITMAP_ATLAS,
    MSDF
}
