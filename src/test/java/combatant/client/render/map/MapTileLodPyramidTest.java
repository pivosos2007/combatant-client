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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileLodPyramidTest {
    @Test
    void parentBuildWaitsForFourChildrenAndPropagatesRevision() {
        MapTileLodPyramid pyramid = new MapTileLodPyramid(2);
        pyramid.putLeaf(new MapTileCoordinate(0, 0, 0), 10);
        pyramid.putLeaf(new MapTileCoordinate(1, 0, 0), 11);
        pyramid.putLeaf(new MapTileCoordinate(0, 1, 0), 12);
        assertTrue(pyramid.pollBuilds(4).isEmpty());

        pyramid.putLeaf(new MapTileCoordinate(1, 1, 0), 13);
        List<MapTileLodPyramid.DownsampleRequest> requests = pyramid.pollBuilds(4);
        assertEquals(1, requests.size());
        assertEquals(new MapTileCoordinate(0, 0, 1), requests.getFirst().parent());
        assertTrue(pyramid.complete(requests.getFirst(), 20));
        assertEquals(20, pyramid.state(new MapTileCoordinate(0, 0, 1)).revision());
        assertFalse(pyramid.state(new MapTileCoordinate(0, 0, 1)).dirty());
    }

    @Test
    void staleDownsampleCompletionIsRejected() {
        MapTileLodPyramid pyramid = new MapTileLodPyramid(1);
        for (MapTileCoordinate child : new MapTileCoordinate(0, 0, 1).children()) {
            pyramid.putLeaf(child, 1);
        }
        MapTileLodPyramid.DownsampleRequest request = pyramid.pollBuilds(1).getFirst();
        pyramid.putLeaf(new MapTileCoordinate(0, 0, 0), 2);
        assertFalse(pyramid.complete(request, 3));
        assertTrue(pyramid.state(request.parent()).dirty());
    }
}
