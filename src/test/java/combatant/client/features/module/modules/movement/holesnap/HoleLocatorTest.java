/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.holesnap;

import combatant.client.features.module.modules.movement.holesnap.HoleLocator.Cell;
import combatant.client.features.module.modules.movement.holesnap.HoleLocator.Hole;
import combatant.client.features.module.modules.movement.holesnap.HoleLocator.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HoleLocatorTest {

    private record Pos(int x, int y, int z) {
    }

    /** Flat ground of ordinary blocks up to y=63, open air above, with edits on top. */
    private static final class Grid implements HoleLocator.World {
        private final Map<Pos, Cell> edits = new HashMap<>();

        @Override
        public Cell at(int x, int y, int z) {
            Cell edited = edits.get(new Pos(x, y, z));
            if (edited != null) return edited;
            return y <= 63 ? Cell.OTHER : Cell.AIR;
        }

        void set(int x, int y, int z, Cell cell) {
            edits.put(new Pos(x, y, z), cell);
        }

        /** Carves a one-block pit at (x, y, z) and lines its floor and four walls with {@code lining}. */
        void hole(int x, int y, int z, Cell lining) {
            // Open shaft from the hole cell up through the ground surface and three blocks of headroom.
            for (int shaft = y; shaft <= Math.max(y + 2, 63); shaft++) set(x, shaft, z, Cell.AIR);
            set(x, y - 1, z, lining);
            set(x + 1, y, z, lining);
            set(x - 1, y, z, lining);
            set(x, y, z + 1, lining);
            set(x, y, z - 1, lining);
            // Ground blocks beside a deep shaft stay solid above the lining so the pit is really a pit.
            for (int up = 1; up <= 63 - y; up++) {
                for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (!edits.containsKey(new Pos(x + side[0], y + up, z + side[1]))) {
                        set(x + side[0], y + up, z + side[1], Cell.OTHER);
                    }
                }
            }
        }
    }

    private static Hole nearest(Grid grid, double px, double py, double pz) {
        return HoleLocator.nearest(grid, px, py, pz, 8, 3, 1, true, true);
    }

    @Test
    void findsASingleBedrockHole() {
        Grid grid = new Grid();
        grid.hole(5, 64, 5, Cell.BEDROCK);

        Hole hole = nearest(grid, 0.5, 64.0, 0.5);

        assertNotNull(hole);
        assertEquals(new Hole(5, 64, 5, Kind.BEDROCK), hole);
        assertEquals(5.5, hole.centerX());
        assertEquals(5.5, hole.centerZ());
    }

    @Test
    void oneBlastProofBlockMakesItAMixedHole() {
        Grid grid = new Grid();
        grid.hole(5, 64, 5, Cell.BEDROCK);
        grid.set(6, 64, 5, Cell.BLAST_PROOF);

        assertEquals(Kind.BLAST_PROOF, nearest(grid, 0.5, 64.0, 0.5).kind());
    }

    @Test
    void anUnsafeWallMeansNoHole() {
        Grid grid = new Grid();
        grid.hole(5, 64, 5, Cell.BEDROCK);
        grid.set(5, 64, 6, Cell.OTHER);

        assertNull(nearest(grid, 0.5, 64.0, 0.5));
    }

    @Test
    void anUnsafeFloorMeansNoHole() {
        Grid grid = new Grid();
        grid.hole(5, 64, 5, Cell.BEDROCK);
        grid.set(5, 63, 5, Cell.OTHER);

        assertNull(nearest(grid, 0.5, 64.0, 0.5));
    }

    @Test
    void needsThreeBlocksOfHeadroom() {
        Grid grid = new Grid();
        grid.hole(5, 64, 5, Cell.BEDROCK);
        grid.set(5, 66, 5, Cell.OTHER);

        assertNull(nearest(grid, 0.5, 64.0, 0.5));
    }

    @Test
    void prefersTheCloserHole() {
        Grid grid = new Grid();
        grid.hole(6, 64, 0, Cell.BEDROCK);
        grid.hole(-2, 64, 0, Cell.BEDROCK);

        assertEquals(-2, nearest(grid, 0.5, 64.0, 0.5).x());
    }

    @Test
    void typeFiltersPickTheNextBestHole() {
        Grid grid = new Grid();
        grid.hole(2, 64, 0, Cell.BEDROCK);
        grid.hole(6, 64, 0, Cell.BLAST_PROOF);

        Hole onlyBlastProof = HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, false, true);
        assertEquals(6, onlyBlastProof.x());

        Hole onlyBedrock = HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, true, false);
        assertEquals(2, onlyBedrock.x());

        assertNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, false, false));
    }

    @Test
    void ignoresHolesBeyondTheRange() {
        Grid grid = new Grid();
        grid.hole(7, 64, 0, Cell.BEDROCK);

        assertNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 5, 3, 1, true, true));
        assertNotNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, true, true));
    }

    @Test
    void rangeIsRoundNotSquare() {
        Grid grid = new Grid();
        // 5 blocks along both axes is about 7.1 away: inside a square of 5, outside a circle of 5.
        grid.hole(5, 64, 5, Cell.BEDROCK);

        assertNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 5, 3, 1, true, true));
    }

    @Test
    void dropLimitDecidesWhetherADeepHoleCounts() {
        Grid grid = new Grid();
        grid.hole(3, 60, 0, Cell.BEDROCK);

        assertNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, true, true));
        assertEquals(60, HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 4, 1, true, true).y());
    }

    @Test
    void riseLimitDecidesWhetherAHigherHoleCounts() {
        Grid grid = new Grid();
        // A hole on a one-block ledge: its floor sits at y=64.
        grid.set(3, 64, 0, Cell.OTHER);
        grid.set(3, 65, 0, Cell.OTHER);
        grid.hole(3, 65, 0, Cell.BEDROCK);

        assertNull(HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 0, true, true));
        assertEquals(65, HoleLocator.nearest(grid, 0.5, 64.0, 0.5, 8, 3, 1, true, true).y());
    }

    @Test
    void standingInsideAHoleReturnsThatHole() {
        Grid grid = new Grid();
        grid.hole(0, 64, 0, Cell.BEDROCK);
        grid.hole(6, 64, 0, Cell.BEDROCK);

        Hole hole = nearest(grid, 0.5, 64.0, 0.5);
        assertEquals(0, hole.x());
        assertEquals(0, hole.z());
    }
}
