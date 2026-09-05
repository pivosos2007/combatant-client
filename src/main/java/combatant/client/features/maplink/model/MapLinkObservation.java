/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.model;

import java.util.UUID;

/** Immutable exact web-map location observation published by the backend. */
public record MapLinkObservation(
        String profileId,
        MapLinkProviderType providerType,
        String rawPlayerName,
        UUID resolvedUuid,
        String providerWorld,
        String mappedWorld,
        double x,
        double y,
        double z,
        long fetchTimestamp,
        long providerTimestamp,
        String revision,
        int sourcePriority
) {
    public boolean worldMapped() {
        return mappedWorld != null && !mappedWorld.isBlank();
    }
}
