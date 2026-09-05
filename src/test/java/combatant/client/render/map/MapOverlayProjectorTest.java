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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapOverlayProjectorTest {
    @Test
    void projectsMarkersAndPrioritizesMarkerHitOverUncertainty() {
        MapViewport viewport = new MapViewport(new MapRect(0, 0, 200, 200), 0, 0, 1);
        MapOverlaySnapshot snapshot = new MapOverlaySnapshot(
                List.of(new MapMarker("player", 0, 0, 5, 0xFFFFFFFF, 0, 100)),
                List.of(),
                List.of(new MapUncertainty("area", 0, 0, 30, 20, 0x200000FF, 0, 10))
        );
        MapOverlayDrawList draw = new MapOverlayProjector().project(viewport, snapshot, MapGridSpec.DEFAULT);
        assertEquals(1, draw.circles().size());
        assertEquals(1, draw.ellipses().size());
        assertTrue(draw.hitIndex().hit(100, 100).isPresent());
        assertEquals("player", draw.hitIndex().hit(100, 100).orElseThrow().id());
    }
}
