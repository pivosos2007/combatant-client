/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.model;

import java.util.Map;
import java.util.UUID;

/** Immutable main-thread capture passed to provider workers. */
public record MapLinkFetchContext(String localPlayerName,
                                  String currentWorldId,
                                  Map<String, UUID> knownPlayersByLowerName) {
    public MapLinkFetchContext {
        localPlayerName = localPlayerName == null ? "" : localPlayerName;
        currentWorldId = MapLinkProfile.normalizeWorld(currentWorldId);
        knownPlayersByLowerName = knownPlayersByLowerName == null ? Map.of() : Map.copyOf(knownPlayersByLowerName);
    }

    public UUID resolveKnownUuid(String playerName) {
        if (playerName == null) return null;
        return knownPlayersByLowerName.get(playerName.trim().toLowerCase(java.util.Locale.ROOT));
    }
}
