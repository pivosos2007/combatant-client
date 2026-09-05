/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** Narrow immutable renderer input; it contains no provider, Xaero or Minecraft entity objects. */
public record MapSceneDataSnapshot(long generation,
                                   List<MapTerrainTile> terrain,
                                   MapOverlaySnapshot overlays,
                                   MapGridSpec grid) {
    public static final MapSceneDataSnapshot EMPTY =
            new MapSceneDataSnapshot(0L, List.of(), MapOverlaySnapshot.EMPTY, MapGridSpec.DEFAULT);

    public MapSceneDataSnapshot {
        terrain = terrain == null ? List.of() : List.copyOf(terrain);
        overlays = overlays == null ? MapOverlaySnapshot.EMPTY : overlays;
        grid = grid == null ? MapGridSpec.DEFAULT : grid;
    }
}
