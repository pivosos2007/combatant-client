/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** Immutable overlay input safe to publish across backend/render boundaries. */
public record MapOverlaySnapshot(List<MapMarker> markers,
                                 List<MapLabel> labels,
                                 List<MapUncertainty> uncertainties) {
    public static final MapOverlaySnapshot EMPTY = new MapOverlaySnapshot(List.of(), List.of(), List.of());

    public MapOverlaySnapshot {
        markers = markers == null ? List.of() : List.copyOf(markers);
        labels = labels == null ? List.of() : List.copyOf(labels);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }
}
