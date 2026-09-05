/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.Objects;

/** Provider-neutral marker presentation input. */
public record MapMarker(String id,
                        double worldX,
                        double worldZ,
                        double radiusPixels,
                        int fillArgb,
                        int strokeArgb,
                        int priority) {
    public MapMarker {
        Objects.requireNonNull(id, "id");
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)
                || !Double.isFinite(radiusPixels) || radiusPixels <= 0.0) {
            throw new IllegalArgumentException("Invalid map marker geometry.");
        }
    }
}
