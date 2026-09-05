/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapVisibleTileSelectorTest {
    @Test
    void selectsNegativeTilesWithMathematicalFlooring() {
        MapViewport viewport = new MapViewport(new MapRect(0, 0, 256, 256), -128, -128, 1);
        List<MapTileCoordinate> tiles = new MapVisibleTileSelector(128, 16).select(viewport, 0, 0);
        assertEquals(List.of(
                new MapTileCoordinate(-2, -2, 0),
                new MapTileCoordinate(-1, -2, 0),
                new MapTileCoordinate(-2, -1, 0),
                new MapTileCoordinate(-1, -1, 0)
        ), tiles);
    }

    @Test
    void rejectsUnboundedSelections() {
        MapViewport viewport = new MapViewport(new MapRect(0, 0, 1000, 1000), 0, 0, 1);
        MapVisibleTileSelector selector = new MapVisibleTileSelector(16, 8);
        assertThrows(IllegalStateException.class, () -> selector.select(viewport, 0, 0));
    }
}
