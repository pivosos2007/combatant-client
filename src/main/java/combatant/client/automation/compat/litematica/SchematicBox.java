/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

/** Inclusive axis-aligned block box; free of Litematica types so it can cross the adapter boundary. */
public record SchematicBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public SchematicBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("empty box " + minX + "," + minY + "," + minZ
                    + " .. " + maxX + "," + maxY + "," + maxZ);
        }
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(SchematicBox other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** The overlap of both boxes, or null when they do not touch. */
    public SchematicBox intersection(SchematicBox other) {
        if (!intersects(other)) return null;
        return new SchematicBox(
                Math.max(minX, other.minX), Math.max(minY, other.minY), Math.max(minZ, other.minZ),
                Math.min(maxX, other.maxX), Math.min(maxY, other.maxY), Math.min(maxZ, other.maxZ));
    }

    public SchematicBox union(SchematicBox other) {
        return new SchematicBox(
                Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
