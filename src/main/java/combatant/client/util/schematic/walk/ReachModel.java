/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

/**
 * Whether a cell can be clicked from an eye position, judged on the block grid alone.
 *
 * <p>A placement needs a solid neighbour whose face toward the cell points at the eyes, is within reach at its
 * nearest point and can be seen without the ray crossing another block. A break needs one of the block's own
 * faces with air in front of it under the same rules. This is the cheap estimate the walker plans with; the
 * placer still checks the real click against the world before it goes out.</p>
 */
public final class ReachModel {

    /** Player eye height above the feet when standing. */
    public static final double EYE_HEIGHT = 1.62;

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    /** Keeps the probe point a hair inside the face so the ray ends in the right cell. */
    private static final double INSET = 0.02;
    /** The ray is checked against points this far in from the face edge, like the placer's face samples. */
    private static final double EDGE_MARGIN = 0.2;

    private ReachModel() {
    }

    /** Can a block be placed into {@code (tx, ty, tz)} by clicking a neighbour seen from the eyes? */
    public static boolean placeable(WalkTerrain terrain, double ex, double ey, double ez,
                                    int tx, int ty, int tz, double reach) {
        if (!replaceable(terrain.cell(tx, ty, tz))) return false;
        for (int[] d : FACES) {
            int sx = tx + d[0], sy = ty + d[1], sz = tz + d[2];
            if (!terrain.solid(sx, sy, sz)) continue;
            // The clicked face is the support's face toward the target, it lies on the shared boundary and points
            // from the support back into the target, i.e. along -d.
            if (faceVisible(terrain, ex, ey, ez, -d[0], -d[1], -d[2], sx, sy, sz, tx, ty, tz, reach)) {
                return true;
            }
        }
        return false;
    }

    /** Can the block at {@code (tx, ty, tz)} be hit on a face that has air in front of it? */
    public static boolean breakable(WalkTerrain terrain, double ex, double ey, double ez,
                                    int tx, int ty, int tz, double reach) {
        return breakFace(terrain, ex, ey, ez, tx, ty, tz, reach) >= 0;
    }

    /**
     * The face to hit when breaking {@code (tx, ty, tz)}, or -1 when none can be. Faces are numbered
     * +X, -X, +Y, -Y, +Z, -Z; the nearest usable one wins.
     */
    public static int breakFace(WalkTerrain terrain, double ex, double ey, double ez,
                                int tx, int ty, int tz, double reach) {
        if (!terrain.solid(tx, ty, tz)) return -1;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < FACES.length; i++) {
            int[] d = FACES[i];
            int ax = tx + d[0], ay = ty + d[1], az = tz + d[2];
            if (!replaceable(terrain.cell(ax, ay, az))) continue;
            if (!faceVisible(terrain, ex, ey, ez, d[0], d[1], d[2], tx, ty, tz, ax, ay, az, reach)) continue;
            double dx = tx + 0.5 + d[0] * 0.5 - ex, dy = ty + 0.5 + d[1] * 0.5 - ey, dz = tz + 0.5 + d[2] * 0.5 - ez;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Unit normal of face {@code index} as numbered by {@link #breakFace}. */
    public static int[] faceNormal(int index) {
        return FACES[index].clone();
    }

    /**
     * The face lies on the boundary between the block cell {@code (bx, by, bz)} and the air cell
     * {@code (airX, airY, airZ)} and points along {@code (nx, ny, nz)} into the air cell.
     */
    private static boolean faceVisible(WalkTerrain terrain, double ex, double ey, double ez,
                                       int nx, int ny, int nz,
                                       int bx, int by, int bz, int airX, int airY, int airZ, double reach) {
        double cx = bx + 0.5 + nx * 0.5;
        double cy = by + 0.5 + ny * 0.5;
        double cz = bz + 0.5 + nz * 0.5;
        // Seen from behind the face plane the face is turned away.
        if ((ex - cx) * nx + (ey - cy) * ny + (ez - cz) * nz <= 1.0e-6) return false;
        // Nearest point of the face, kept off its edges, decides reach and is where the ray is aimed.
        double px = nx != 0 ? cx : clamp(ex, bx + EDGE_MARGIN, bx + 1 - EDGE_MARGIN);
        double py = ny != 0 ? cy : clamp(ey, by + EDGE_MARGIN, by + 1 - EDGE_MARGIN);
        double pz = nz != 0 ? cz : clamp(ez, bz + EDGE_MARGIN, bz + 1 - EDGE_MARGIN);
        double dx = px - ex, dy = py - ey, dz = pz - ez;
        if (dx * dx + dy * dy + dz * dz > reach * reach) return false;
        // Stop the ray just in front of the face, inside the air cell: every cell it crosses must be open.
        return clear(terrain, ex, ey, ez, px + nx * INSET, py + ny * INSET, pz + nz * INSET, airX, airY, airZ);
    }

    /** Grid walk from the eye to the end point; only the end cell and open cells may be crossed. */
    static boolean clear(WalkTerrain terrain, double x0, double y0, double z0, double x1, double y1, double z1,
                         int endX, int endY, int endZ) {
        int x = floor(x0), y = floor(y0), z = floor(z0);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - x0 : x0 - x) * tDeltaX;
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - y0 : y0 - y) * tDeltaY;
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - z0 : z0 - z) * tDeltaZ;
        for (int guard = 0; guard < 64; guard++) {
            if (x == endX && y == endY && z == endZ) return true;
            if (guard > 0 && !replaceable(terrain.cell(x, y, z))) return false;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > 1.0) break;
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > 1.0) break;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0) break;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return x == endX && y == endY && z == endZ;
    }

    /** Air or water: a block can go there and a click ray passes through it. */
    static boolean replaceable(byte cell) {
        return cell == WalkTerrain.OPEN || cell == WalkTerrain.WATER;
    }

    /**
     * Eye position of a player standing centred in the feet cell {@code (fx, fy, fz)}.
     */
    public static double eyeY(int fy) {
        return fy + EYE_HEIGHT;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
