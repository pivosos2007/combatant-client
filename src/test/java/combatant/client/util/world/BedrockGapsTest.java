package combatant.client.util.world;

import combatant.client.util.world.BedrockGaps.Cell;
import combatant.client.util.world.BedrockGaps.Gap;
import combatant.client.util.world.BedrockGaps.Kind;
import combatant.client.util.world.BedrockGaps.Side;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGapsTest {

    /** Fake grid: everything in [floorY, floorY + 4] is bedrock except explicitly cleared cells. */
    private static final class FakeGrid implements BedrockGaps.Grid {
        private final int floorY;
        private final int roofY;
        private final Set<Long> cleared = new HashSet<>();
        private final Set<Long> unknown = new HashSet<>();

        FakeGrid(int floorY, int roofY) {
            this.floorY = floorY;
            this.roofY = roofY;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
        }

        FakeGrid clear(int x, int y, int z) {
            cleared.add(key(x, y, z));
            return this;
        }

        FakeGrid unknown(int x, int y, int z) {
            unknown.add(key(x, y, z));
            return this;
        }

        @Override
        public Cell cell(int x, int y, int z) {
            long k = key(x, y, z);
            if (unknown.contains(k)) return Cell.UNKNOWN;
            if (cleared.contains(k)) return Cell.OTHER;
            boolean inFloor = y >= floorY && y <= floorY + 4;
            boolean inRoof = y <= roofY && y >= roofY - 4;
            return inFloor || inRoof ? Cell.BEDROCK : Cell.OTHER;
        }
    }

    @Test
    void intactFloorHasNoGaps() {
        FakeGrid grid = new FakeGrid(-64, 127);
        assertTrue(BedrockGaps.scan(grid, 0, 0, 15, 15, -64, 5, Side.FLOOR).isEmpty());
    }

    @Test
    void missingOuterCellIsHole() {
        FakeGrid grid = new FakeGrid(-64, 127).clear(3, -64, 7);
        List<Gap> gaps = BedrockGaps.scan(grid, 0, 0, 15, 15, -64, 5, Side.FLOOR);
        assertEquals(List.of(new Gap(3, -64, 7, Kind.HOLE, Side.FLOOR)), gaps);
    }

    @Test
    void fullyOpenColumnIsShaftNotHole() {
        FakeGrid grid = new FakeGrid(0, 127);
        for (int y = 0; y <= 4; y++) grid.clear(2, y, 2);
        List<Gap> gaps = BedrockGaps.scan(grid, 0, 0, 5, 5, 0, 5, Side.FLOOR);
        assertEquals(List.of(new Gap(2, 0, 2, Kind.SHAFT, Side.FLOOR)), gaps);
    }

    @Test
    void roofScansDownwardFromOuterLayer() {
        FakeGrid grid = new FakeGrid(0, 127);
        for (int y = 123; y <= 127; y++) grid.clear(1, y, 1);
        grid.clear(4, 127, 4);
        List<Gap> gaps = BedrockGaps.scan(grid, 0, 0, 5, 5, 127, 5, Side.ROOF);
        assertEquals(2, gaps.size());
        assertTrue(gaps.contains(new Gap(1, 127, 1, Kind.SHAFT, Side.ROOF)));
        assertTrue(gaps.contains(new Gap(4, 127, 4, Kind.HOLE, Side.ROOF)));
    }

    @Test
    void unknownCellsAreNeverReported() {
        FakeGrid grid = new FakeGrid(0, 127).clear(1, 0, 1).unknown(1, 0, 1);
        assertTrue(BedrockGaps.scan(grid, 0, 0, 3, 3, 0, 5, Side.FLOOR).isEmpty());
    }

    @Test
    void unknownInnerLayerPreventsShaft() {
        FakeGrid grid = new FakeGrid(0, 127);
        for (int y = 0; y <= 4; y++) grid.clear(1, y, 1);
        grid.unknown(1, 3, 1);
        List<Gap> gaps = BedrockGaps.scan(grid, 0, 0, 3, 3, 0, 5, Side.FLOOR);
        assertEquals(List.of(new Gap(1, 0, 1, Kind.HOLE, Side.FLOOR)), gaps);
    }
}
