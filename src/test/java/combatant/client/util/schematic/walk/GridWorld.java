/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.HashMap;
import java.util.Map;

/** A small test world: a solid floor at y = -1 (feet at y = 0), open air above, overridable per cell. */
final class GridWorld implements WalkTerrain {

    private final Map<Long, Byte> cells = new HashMap<>();
    private final int floorY;

    GridWorld() {
        this(-1);
    }

    GridWorld(int floorY) {
        this.floorY = floorY;
    }

    @Override
    public byte cell(int x, int y, int z) {
        Byte value = cells.get(WalkTerrain.pack(x, y, z));
        if (value != null) return value;
        if (y < floorY - 2) return UNKNOWN;
        return y <= floorY ? SOLID : OPEN;
    }

    GridWorld set(int x, int y, int z, byte value) {
        cells.put(WalkTerrain.pack(x, y, z), value);
        return this;
    }

    GridWorld block(int x, int y, int z) {
        return set(x, y, z, SOLID);
    }

    GridWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, byte value) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) set(x, y, z, value);
            }
        }
        return this;
    }

    /**
     * Rows of a single layer at height {@code y}: '#' solid, '.' open, '~' danger, '?' unknown, 'w' water,
     * 'H' climbable, '_' half-block step, 's' slow ground, 'i' ice, 'c' cobweb, 'r' raised floor; row index is z,
     * column index is x.
     */
    GridWorld layer(int y, String... rows) {
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                char c = rows[z].charAt(x);
                set(x, y, z, switch (c) {
                    case '#' -> SOLID;
                    case '~' -> DANGER;
                    case '?' -> UNKNOWN;
                    case 'w' -> WATER;
                    case 'H' -> CLIMB;
                    case '_' -> STEP;
                    case 's' -> SLOW;
                    case 'i' -> ICE;
                    case 'c' -> WEB;
                    case 'r' -> RAISED;
                    default -> OPEN;
                });
            }
        }
        return this;
    }
}
