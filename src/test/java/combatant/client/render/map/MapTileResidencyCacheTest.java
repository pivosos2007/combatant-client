/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileResidencyCacheTest {
    @Test
    void pinsCurrentFrameAndEvictsLeastRecentlyUsedUnpinnedSlot() {
        MapTileResidencyCache cache = new MapTileResidencyCache(1, 2);
        MapTileResidencyKey a = key("a", 0);
        MapTileResidencyKey b = key("b", 1);
        MapTileResidencyKey c = key("c", 2);

        cache.beginFrame(1);
        cache.acquire(a, 1);
        cache.acquire(b, 1);
        assertFalse(cache.acquire(c, 1).available());

        cache.beginFrame(2);
        cache.acquire(b, 1);
        MapTileResidencyCache.Acquisition acquired = cache.acquire(c, 1);
        assertTrue(acquired.available());
        assertTrue(acquired.evicted().key().equals(a));
    }

    @Test
    void revisionChangeInvalidatesGeneration() {
        MapTileResidencyCache cache = new MapTileResidencyCache(1, 1);
        MapTileResidencyKey key = key("tile", 0);
        cache.beginFrame(1);
        MapTileSlot first = cache.acquire(key, 1).slot();
        MapTileSlot second = cache.acquire(key, 2).slot();
        assertNotEquals(first.generation(), second.generation());
        assertFalse(cache.isCurrent(key, first));
        assertTrue(cache.isCurrent(key, second));
    }

    private static MapTileResidencyKey key(String source, int x) {
        return new MapTileResidencyKey(source, new MapTileCoordinate(x, 0, 0));
    }
}
