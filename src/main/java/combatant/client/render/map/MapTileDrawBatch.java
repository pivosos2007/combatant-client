/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** One atlas-page draw batch for a single LOD pass. */
public record MapTileDrawBatch(int lod, MapTileMaterial material, int page, List<Entry> entries) {
    public MapTileDrawBatch {
        if (lod < 0) throw new IllegalArgumentException("lod cannot be negative.");
        material = material == null ? MapTileMaterial.RGBA : material;
        entries = List.copyOf(entries);
    }

    public record Entry(MapTileResidencyKey key,
                        long revision,
                        MapTileSlot slot,
                        MapRect worldBounds,
                        MapTileUvRect textureUv,
                        int tintArgb) {
    }
}
