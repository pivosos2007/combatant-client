/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

/**
 * Plans one step of a straight 1x2 tunnel through a {@link WalkTerrain} and judges whether taking it is safe.
 *
 * <p>The tunnel runs through feet cell {@code fy} and head cell {@code fy + 1} of the next column. A step lists the
 * solid cells to dig (feet first) and the worst hazard around them: lava or fire that would be opened up or is
 * visible ahead, water that would flood the tunnel, a missing floor (a pit, or the void below), or cells that are
 * not loaded. Game-free, so it is tested on small hand-drawn grids.</p>
 */
public final class TunnelPlanner {

    private static final long[] NO_CELLS = new long[0];

    private TunnelPlanner() {
    }

    /** Ordered by severity: the worst hazard of a step wins. */
    public enum Hazard {
        NONE,
        /** A cell of the tunnel is unknown: not loaded yet, outside the world or an odd shape. */
        UNLOADED,
        /** The floor of the next column is missing but solid ground is within the allowed drop. */
        DROP,
        /** No floor within the allowed drop: a hole or the void. */
        VOID,
        /** Water in or beside the tunnel cells. */
        FLUID,
        /** Lava, fire or another danger cell in or beside the tunnel cells. */
        LAVA
    }

    /**
     * @param dig   packed cells ({@link WalkTerrain#pack}) to break, feet cell first; never null
     * @param x,y,z the feet cell the player walks into once the dig cells are gone
     */
    public record Step(Hazard hazard, long[] dig, int x, int y, int z) {
        public boolean safe() {
            return hazard == Hazard.NONE;
        }
    }

    /** Cardinal index of a yaw: 0 south (+z), 1 west (-x), 2 north (-z), 3 east (+x). */
    public static int cardinalFromYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0f), 4);
    }

    public static int dirX(int cardinal) {
        return switch (Math.floorMod(cardinal, 4)) {
            case 1 -> -1;
            case 3 -> 1;
            default -> 0;
        };
    }

    public static int dirZ(int cardinal) {
        return switch (Math.floorMod(cardinal, 4)) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
    }

    /**
     * @param fx,fy,fz  the feet cell the player stands in
     * @param dx,dz     the unit direction of the tunnel (one of them non-zero)
     * @param maxDrop   how far the floor of the next column may be missing before it counts as a void
     * @param lookAhead how many columns ahead (including the next one) are scanned for lava and water
     */
    public static Step plan(WalkTerrain terrain, int fx, int fy, int fz, int dx, int dz, int maxDrop, int lookAhead) {
        int nx = fx + dx;
        int nz = fz + dz;
        Hazard worst = Hazard.NONE;
        long[] dig = NO_CELLS;
        int count = 0;
        long[] buffer = new long[2];

        for (int y = fy; y <= fy + 1; y++) {
            byte cell = terrain.cell(nx, y, nz);
            worst = worse(worst, classify(cell, true));
            if (WalkTerrain.support(cell)) buffer[count++] = WalkTerrain.pack(nx, y, nz);
        }
        if (count > 0) {
            dig = new long[count];
            System.arraycopy(buffer, 0, dig, 0, count);
        }

        // What breaking the cells would open up: the ceiling and the three sides of both cells.
        worst = worse(worst, classify(terrain.cell(nx, fy + 2, nz), false));
        for (int y = fy; y <= fy + 1; y++) {
            worst = worse(worst, classify(terrain.cell(nx + dx, y, nz + dz), false));
            worst = worse(worst, classify(terrain.cell(nx - dz, y, nz + dx), false));
            worst = worse(worst, classify(terrain.cell(nx + dz, y, nz - dx), false));
        }

        // Lava and water already visible further down the line, before the tunnel gets there.
        for (int i = 2; i <= lookAhead; i++) {
            for (int y = fy; y <= fy + 1; y++) {
                worst = worse(worst, classify(terrain.cell(fx + dx * i, y, fz + dz * i), false));
            }
        }

        worst = worse(worst, floorHazard(terrain, nx, fy - 1, nz, Math.max(0, maxDrop)));
        return new Step(worst, dig, nx, fy, nz);
    }

    /**
     * Hazard of one cell. {@code inside}: the cell is part of the tunnel, so an unknown cell is a hazard; beside the
     * tunnel unknown cells (odd shapes, unloaded edge) are ignored.
     */
    private static Hazard classify(byte cell, boolean inside) {
        return switch (cell) {
            case WalkTerrain.DANGER -> Hazard.LAVA;
            case WalkTerrain.WATER -> Hazard.FLUID;
            case WalkTerrain.UNKNOWN -> inside ? Hazard.UNLOADED : Hazard.NONE;
            default -> Hazard.NONE;
        };
    }

    private static Hazard floorHazard(WalkTerrain terrain, int x, int floorY, int z, int maxDrop) {
        byte floor = terrain.cell(x, floorY, z);
        if (floor == WalkTerrain.DANGER) return Hazard.LAVA;
        if (floor == WalkTerrain.WATER) return Hazard.FLUID;
        if (WalkTerrain.support(floor) || floor == WalkTerrain.CLIMB) return Hazard.NONE;
        if (floor == WalkTerrain.UNKNOWN) return Hazard.VOID;
        for (int y = floorY - 1; y >= floorY - maxDrop; y--) {
            byte cell = terrain.cell(x, y, z);
            if (WalkTerrain.support(cell)) return Hazard.DROP;
            if (cell == WalkTerrain.DANGER) return Hazard.LAVA;
            if (cell == WalkTerrain.WATER) return Hazard.FLUID;
            if (cell == WalkTerrain.UNKNOWN) return Hazard.VOID;
        }
        return Hazard.VOID;
    }

    private static Hazard worse(Hazard a, Hazard b) {
        return b.ordinal() > a.ordinal() ? b : a;
    }
}
