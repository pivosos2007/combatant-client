/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.model;

import java.util.Set;

public record MapLinkProfileState(
        String profileId,
        MapLinkProfileStatus status,
        long lastAttemptMs,
        long lastSuccessMs,
        String detail,
        int playerCount,
        Set<String> providerWorlds,
        long updateCadenceMs,
        int consecutiveFailures
) {
    public MapLinkProfileState {
        detail = detail == null ? "" : detail;
        providerWorlds = providerWorlds == null ? Set.of() : Set.copyOf(providerWorlds);
    }
}
