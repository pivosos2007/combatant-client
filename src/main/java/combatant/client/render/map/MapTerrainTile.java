/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Immutable terrain input supplied by a data-backend adapter. Pixels may be absent while loading. */
public record MapTerrainTile(MapTileResidencyKey key,
                             long revision,
                             MapRect worldBounds,
                             MapTilePixels pixels,
                             int tintArgb) {
    public MapTerrainTile {
        if (key == null || worldBounds == null) throw new NullPointerException();
    }

    public static MapTerrainTile loading(MapTileResidencyKey key, long revision, MapRect worldBounds) {
        return new MapTerrainTile(key, revision, worldBounds, null, 0xFFFFFFFF);
    }
}
