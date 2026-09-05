/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.model;

import java.util.UUID;

/** Provider-level player observation before server/world identity resolution. */
public record MapLinkRawPlayer(
        String name,
        UUID uuid,
        String providerWorld,
        double x,
        double y,
        double z,
        long providerTimestamp,
        String revision
) {
    public MapLinkRawPlayer {
        name = name == null ? "" : name.trim();
        providerWorld = MapLinkProfile.normalizeWorld(providerWorld);
        revision = revision == null ? "" : revision;
    }
}
