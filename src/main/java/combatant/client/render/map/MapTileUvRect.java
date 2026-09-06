/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

public record MapTileUvRect(double minU, double minV, double maxU, double maxV) {
    public static final MapTileUvRect FULL = new MapTileUvRect(0.0, 0.0, 1.0, 1.0);

    public MapTileUvRect {
        if (!Double.isFinite(minU) || !Double.isFinite(minV)
                || !Double.isFinite(maxU) || !Double.isFinite(maxV)
                || minU < 0.0 || minV < 0.0 || maxU > 1.0 || maxV > 1.0
                || minU >= maxU || minV >= maxV) {
            throw new IllegalArgumentException("Invalid tile UV rectangle.");
        }
    }
}
