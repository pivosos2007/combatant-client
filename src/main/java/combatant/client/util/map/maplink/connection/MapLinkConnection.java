/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.connection;

import combatant.client.util.map.maplink.model.MapLinkFetchContext;
import combatant.client.util.map.maplink.model.MapLinkFetchResult;
import combatant.client.util.map.maplink.model.MapLinkProviderType;

import java.io.IOException;

/** Provider connection boundary. Contains no Xaero/UI side effects. */
public interface MapLinkConnection {
    MapLinkProviderType type();

    MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException;
}
