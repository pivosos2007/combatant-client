/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.Objects;

/** Provider-neutral text label anchored in world X-Z space. */
public record MapLabel(String id,
                       double worldX,
                       double worldZ,
                       String text,
                       int argb,
                       int priority) {
    public MapLabel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IllegalArgumentException("Invalid map label anchor.");
        }
    }
}
