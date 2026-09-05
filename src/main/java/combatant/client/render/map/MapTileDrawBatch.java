/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** One atlas-page draw batch. */
public record MapTileDrawBatch(int page, List<Entry> entries) {
    public MapTileDrawBatch {
        entries = List.copyOf(entries);
    }

    public record Entry(MapTileResidencyKey key,
                        long revision,
                        MapTileSlot slot,
                        MapRect worldBounds,
                        int tintArgb) {
    }
}
