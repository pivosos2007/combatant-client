/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TerrainCacheTest {

    @Test
    void readsEachCellOnceUntilInvalidated() {
        int[] reads = {0};
        GridWorld world = new GridWorld().block(-17, 3, 40);
        TerrainCache cache = new TerrainCache((x, y, z) -> {
            reads[0]++;
            return world.cell(x, y, z);
        });
        assertEquals(WalkTerrain.SOLID, cache.cell(-17, 3, 40));
        assertEquals(WalkTerrain.SOLID, cache.cell(-17, 3, 40));
        assertEquals(WalkTerrain.OPEN, cache.cell(-17, 4, 40));
        assertEquals(2, reads[0]);

        world.set(-17, 3, 40, WalkTerrain.OPEN);
        assertEquals(WalkTerrain.SOLID, cache.cell(-17, 3, 40), "stale until told");
        cache.invalidateAround(-17, 4, 40);
        assertEquals(WalkTerrain.OPEN, cache.cell(-17, 3, 40));
        assertEquals(3, reads[0]);

        cache.clear();
        cache.cell(-17, 4, 40);
        assertEquals(4, reads[0]);
    }

    @Test
    void manySectionsAcrossTheSignBoundary() {
        GridWorld world = new GridWorld();
        TerrainCache cache = new TerrainCache(world);
        for (int x = -100; x <= 100; x += 7) {
            for (int z = -100; z <= 100; z += 7) {
                for (int y = -70; y <= 70; y += 35) assertEquals(world.cell(x, y, z), cache.cell(x, y, z));
            }
        }
        assertEquals(cache.misses(), cache.lookups());
        for (int x = -100; x <= 100; x += 7) assertEquals(world.cell(x, -1, 5), cache.cell(x, -1, 5));
    }

    /** A bulk source whose chunk is missing at first: the gap must not be kept once the chunk shows up. */
    @Test
    void sectionsReadWhileUnloadedAreReadAgainOnceLoaded() {
        boolean[] loaded = {false};
        int[] fills = {0};
        TerrainCache cache = new TerrainCache(new TerrainCache.SectionSource() {
            @Override
            public byte cell(int x, int y, int z) {
                return loaded[0] ? WalkTerrain.OPEN : WalkTerrain.UNKNOWN;
            }

            @Override
            public boolean fillSection(int sx, int sy, int sz, byte[] out) {
                fills[0]++;
                java.util.Arrays.fill(out, loaded[0] ? WalkTerrain.OPEN : WalkTerrain.UNKNOWN);
                return loaded[0];
            }
        });
        assertEquals(WalkTerrain.UNKNOWN, cache.cell(40, 0, 40));
        cache.refillIncomplete();
        assertEquals(WalkTerrain.UNKNOWN, cache.cell(40, 0, 40), "still not loaded");
        loaded[0] = true;
        assertEquals(WalkTerrain.UNKNOWN, cache.cell(40, 0, 40), "stale until the owner asks again");
        cache.refillIncomplete();
        assertEquals(WalkTerrain.OPEN, cache.cell(40, 0, 40));
        int before = fills[0];
        cache.refillIncomplete();
        assertEquals(before, fills[0], "a complete section is not read again");
        assertNotEquals(0, before);
    }
}
