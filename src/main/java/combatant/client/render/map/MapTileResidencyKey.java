/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.Objects;

/**
 * Identifies a resident map tile within a renderer-managed source.
 */
public record MapTileResidencyKey(String sourceId, MapTileCoordinate coordinate) {
    public MapTileResidencyKey {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(coordinate, "coordinate");
        if (sourceId.isBlank()) throw new IllegalArgumentException("sourceId cannot be blank.");
    }
}
