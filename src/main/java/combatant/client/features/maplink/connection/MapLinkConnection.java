/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.connection;

import combatant.client.features.maplink.model.MapLinkFetchContext;
import combatant.client.features.maplink.model.MapLinkFetchResult;
import combatant.client.features.maplink.model.MapLinkProviderType;

import java.io.IOException;

/** Fetches player locations from a web-map provider. */
public interface MapLinkConnection {
    MapLinkProviderType type();

    MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException;
}
