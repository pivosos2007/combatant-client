/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Selects discrete terrain LOD independently from continuous viewport zoom. */
public record MapLodPolicy(int leafBlockSpan, int maxLod, double minimumProjectedTilePixels) {
    public MapLodPolicy {
        if (leafBlockSpan <= 0) throw new IllegalArgumentException("leafBlockSpan must be positive.");
        if (maxLod < 0 || maxLod > MapTileCoordinate.MAX_LOD) {
            throw new IllegalArgumentException("maxLod is outside the supported range.");
        }
        if (!Double.isFinite(minimumProjectedTilePixels) || minimumProjectedTilePixels <= 0.0) {
            throw new IllegalArgumentException("minimumProjectedTilePixels must be finite and positive.");
        }
    }

    public int select(double pixelsPerBlock) {
        if (!Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0.0) {
            throw new IllegalArgumentException("pixelsPerBlock must be finite and positive.");
        }
        int lod = 0;
        double projectedSize = leafBlockSpan * pixelsPerBlock;
        while (lod < maxLod && projectedSize < minimumProjectedTilePixels) {
            lod++;
            projectedSize *= 2.0;
        }
        return lod;
    }
}
