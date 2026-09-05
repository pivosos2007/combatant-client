/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Generation-checked fixed atlas slot. */
public record MapTileSlot(int page, int slot, long generation) {
    public MapTileSlot {
        if (page < 0 || slot < 0 || generation <= 0L) {
            throw new IllegalArgumentException("Invalid map tile slot.");
        }
    }
}
