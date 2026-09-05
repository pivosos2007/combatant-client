/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.ArrayList;
import java.util.List;

/** Computes a bounded deterministic tile set for a viewport and one terrain LOD. */
public final class MapVisibleTileSelector {
    private final int leafBlockSpan;
    private final int maxVisibleTiles;

    public MapVisibleTileSelector(int leafBlockSpan, int maxVisibleTiles) {
        if (leafBlockSpan <= 0) throw new IllegalArgumentException("leafBlockSpan must be positive.");
        if (maxVisibleTiles <= 0) throw new IllegalArgumentException("maxVisibleTiles must be positive.");
        this.leafBlockSpan = leafBlockSpan;
        this.maxVisibleTiles = maxVisibleTiles;
    }

    public List<MapTileCoordinate> select(MapViewport viewport, int lod, int paddingTiles) {
        if (viewport == null) throw new NullPointerException("viewport");
        if (paddingTiles < 0) throw new IllegalArgumentException("paddingTiles cannot be negative.");
        MapTileCoordinate scaleProbe = new MapTileCoordinate(0, 0, lod);
        long span = scaleProbe.blockSpan(leafBlockSpan);
        MapRect world = viewport.visibleWorldBounds();

        long minX = floorTile(world.x(), span) - paddingTiles;
        long minZ = floorTile(world.y(), span) - paddingTiles;
        long maxX = exclusiveMaxTile(world.maxX(), span) + paddingTiles;
        long maxZ = exclusiveMaxTile(world.maxY(), span) + paddingTiles;
        long width = Math.addExact(Math.subtractExact(maxX, minX), 1L);
        long height = Math.addExact(Math.subtractExact(maxZ, minZ), 1L);
        long count = Math.multiplyExact(width, height);
        if (count > maxVisibleTiles) {
            throw new IllegalStateException("Visible tile request exceeds limit: " + count + " > " + maxVisibleTiles);
        }
        if (minX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE
                || minZ < Integer.MIN_VALUE || maxZ > Integer.MAX_VALUE) {
            throw new IllegalStateException("Visible tile coordinates exceed integer range.");
        }

        List<MapTileCoordinate> result = new ArrayList<>((int) count);
        for (long z = minZ; z <= maxZ; z++) {
            for (long x = minX; x <= maxX; x++) {
                result.add(new MapTileCoordinate((int) x, (int) z, lod));
            }
        }
        return List.copyOf(result);
    }

    private static long floorTile(double coordinate, long span) {
        double value = Math.floor(coordinate / span);
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw new IllegalStateException("Tile coordinate exceeds long range.");
        }
        return (long) value;
    }

    private static long exclusiveMaxTile(double coordinate, long span) {
        double value = Math.ceil(coordinate / span) - 1.0;
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw new IllegalStateException("Tile coordinate exceeds long range.");
        }
        return (long) value;
    }
}
