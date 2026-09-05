/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** Spatial tile coordinate in an explicit discrete LOD pyramid. */
public record MapTileCoordinate(int x, int z, int lod) implements Comparable<MapTileCoordinate> {
    public static final int MAX_LOD = 30;

    public MapTileCoordinate {
        if (lod < 0 || lod > MAX_LOD) {
            throw new IllegalArgumentException("LOD must be between 0 and " + MAX_LOD + '.');
        }
    }

    public long blockSpan(int leafBlockSpan) {
        if (leafBlockSpan <= 0) throw new IllegalArgumentException("leafBlockSpan must be positive.");
        return Math.multiplyExact((long) leafBlockSpan, 1L << lod);
    }

    public MapTileCoordinate parent() {
        if (lod == MAX_LOD) throw new IllegalStateException("Maximum LOD has no representable parent.");
        return new MapTileCoordinate(Math.floorDiv(x, 2), Math.floorDiv(z, 2), lod + 1);
    }

    public List<MapTileCoordinate> children() {
        if (lod == 0) return List.of();
        int childX = Math.multiplyExact(x, 2);
        int childZ = Math.multiplyExact(z, 2);
        int childLod = lod - 1;
        return List.of(
                new MapTileCoordinate(childX, childZ, childLod),
                new MapTileCoordinate(childX + 1, childZ, childLod),
                new MapTileCoordinate(childX, childZ + 1, childLod),
                new MapTileCoordinate(childX + 1, childZ + 1, childLod)
        );
    }

    public MapRect worldBounds(int leafBlockSpan) {
        long span = blockSpan(leafBlockSpan);
        return new MapRect((double) x * span, (double) z * span, span, span);
    }

    @Override
    public int compareTo(MapTileCoordinate other) {
        int lodOrder = Integer.compare(lod, other.lod);
        if (lodOrder != 0) return lodOrder;
        int zOrder = Integer.compare(z, other.z);
        return zOrder != 0 ? zOrder : Integer.compare(x, other.x);
    }
}
