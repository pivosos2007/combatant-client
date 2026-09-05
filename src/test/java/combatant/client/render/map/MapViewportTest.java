/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapViewportTest {
    @Test
    void projectionRoundTripsAndZoomKeepsAnchorStable() {
        MapViewport viewport = new MapViewport(new MapRect(10, 20, 800, 600), 125, -64, 2.5);
        MapScreenPoint screen = viewport.project(180, 42);
        MapPoint world = viewport.unproject(screen.x(), screen.y());
        assertEquals(180.0, world.x(), 1.0e-9);
        assertEquals(42.0, world.z(), 1.0e-9);

        MapViewport zoomed = viewport.zoomAt(screen.x(), screen.y(), 1.75);
        MapScreenPoint anchored = zoomed.project(180, 42);
        assertEquals(screen.x(), anchored.x(), 1.0e-9);
        assertEquals(screen.y(), anchored.y(), 1.0e-9);
    }

    @Test
    void screenDragMovesMapContentWithPointer() {
        MapViewport viewport = new MapViewport(new MapRect(0, 0, 400, 300), 0, 0, 2);
        MapScreenPoint before = viewport.project(20, 30);
        MapViewport panned = viewport.panPixels(12, -8);
        MapScreenPoint after = panned.project(20, 30);
        assertEquals(before.x() + 12, after.x(), 1.0e-9);
        assertEquals(before.y() - 8, after.y(), 1.0e-9);
    }
}
