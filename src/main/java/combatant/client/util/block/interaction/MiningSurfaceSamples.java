/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.block.interaction;

/** Dependency-free face geometry for voxel-shape multipoints (also exercised by unit tests). */
public final class MiningSurfaceSamples {
    private static final double INSET = 0.0008;
    private MiningSurfaceSamples() { }

    public enum Face { WEST, EAST, DOWN, UP, NORTH, SOUTH }
    public record Point(double x, double y, double z) { }

    public static Point onFace(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               Face face, double u, double v) {
        double x = minX + (maxX - minX) * u;
        double y = minY + (maxY - minY) * v;
        double z = minZ + (maxZ - minZ) * v;
        double ix = Math.min(INSET, Math.max(0.0, (maxX - minX) * 0.25));
        double iy = Math.min(INSET, Math.max(0.0, (maxY - minY) * 0.25));
        double iz = Math.min(INSET, Math.max(0.0, (maxZ - minZ) * 0.25));
        return switch (face) {
            case WEST -> new Point(minX + ix, minY + (maxY - minY) * u, z);
            case EAST -> new Point(maxX - ix, minY + (maxY - minY) * u, z);
            case DOWN -> new Point(x, minY + iy, z);
            case UP -> new Point(x, maxY - iy, z);
            case NORTH -> new Point(x, y, minZ + iz);
            case SOUTH -> new Point(x, y, maxZ - iz);
        };
    }
}
