/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.model;

import java.util.List;
import java.util.Map;

public record MapLinkSnapshot(long generation,
                              long publishedAtMs,
                              List<MapLinkObservation> observations,
                              Map<String, MapLinkProfileState> profileStates) {
    public MapLinkSnapshot {
        observations = observations == null ? List.of() : List.copyOf(observations);
        profileStates = profileStates == null ? Map.of() : Map.copyOf(profileStates);
    }

    public static MapLinkSnapshot empty() {
        return new MapLinkSnapshot(0L, 0L, List.of(), Map.of());
    }
}
