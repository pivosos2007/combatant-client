/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.Objects;

/** Circle/ellipse uncertainty region expressed in world blocks. */
public record MapUncertainty(String id,
                             double centerX,
                             double centerZ,
                             double radiusX,
                             double radiusZ,
                             int fillArgb,
                             int strokeArgb,
                             int priority) {
    public MapUncertainty {
        Objects.requireNonNull(id, "id");
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(radiusX) || !Double.isFinite(radiusZ)
                || radiusX < 0.0 || radiusZ < 0.0) {
            throw new IllegalArgumentException("Invalid uncertainty geometry.");
        }
    }
}
