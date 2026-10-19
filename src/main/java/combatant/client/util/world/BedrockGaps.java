/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure detection of holes in the bedrock floor/roof of a world column range.
 *
 * <p>The outermost bedrock layer (floor: lowest world Y, roof: highest world Y of the Nether) is generated as a solid
 * sheet; a non-bedrock cell there is a {@link Kind#HOLE}. The layers behind it are generated with random gaps, so a
 * column whose entire bedrock thickness is non-bedrock is an open {@link Kind#SHAFT} one can pass through (reported
 * instead of the hole at the same column).</p>
 */
public final class BedrockGaps {

    private BedrockGaps() {
    }

    public enum Cell {
        BEDROCK,
        OTHER,
        /** Chunk not loaded or outside the world: never reported. */
        UNKNOWN
    }

    public enum Kind {
        /** The solid outer layer cell is not bedrock. */
        HOLE,
        /** Every layer of the bedrock thickness at this column is non-bedrock. */
        SHAFT
    }

    /** Which end of the world a layer span belongs to. */
    public enum Side {
        FLOOR,
        ROOF
    }

    /** Read access to a block grid. */
    @FunctionalInterface
    public interface Grid {
        Cell cell(int x, int y, int z);
    }

    public record Gap(int x, int y, int z, Kind kind, Side side) {
    }

    /**
     * Scans {@code [minX, maxX] x [minZ, maxZ]} (inclusive) for gaps.
     *
     * @param outerY    Y of the solid outer layer (world floor Y, or world top Y - 1 for the roof)
     * @param thickness number of layers in the bedrock span, including the outer layer ({@code >= 1})
     * @param side      FLOOR: span extends upward from {@code outerY}; ROOF: span extends downward
     */
    public static List<Gap> scan(Grid grid, int minX, int minZ, int maxX, int maxZ,
                                 int outerY, int thickness, Side side) {
        List<Gap> out = new ArrayList<>();
        int step = side == Side.FLOOR ? 1 : -1;
        int layers = Math.max(1, thickness);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Cell outer = grid.cell(x, outerY, z);
                if (outer != Cell.OTHER) continue;
                Kind kind = layers > 1 && isShaft(grid, x, z, outerY, layers, step) ? Kind.SHAFT : Kind.HOLE;
                out.add(new Gap(x, outerY, z, kind, side));
            }
        }
        return out;
    }

    private static boolean isShaft(Grid grid, int x, int z, int outerY, int layers, int step) {
        for (int i = 1; i < layers; i++) {
            if (grid.cell(x, outerY + step * i, z) != Cell.OTHER) return false;
        }
        return true;
    }
}
