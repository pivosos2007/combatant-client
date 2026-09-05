/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** A projected logical-screen position. */
public record MapScreenPoint(double x, double y) {
    public MapScreenPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Screen point values must be finite.");
        }
    }
}
