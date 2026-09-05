/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.connection;

import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.net.MapLinkHttpClient;

public enum MapLinkConnectionFactory {
    ;

    public static MapLinkConnection create(MapLinkProfile profile, MapLinkHttpClient http) {
        return switch (profile.providerType()) {
            case BLUEMAP -> new BlueMapConnection(profile, http);
            case DYNMAP -> new DynmapConnection(profile, http);
            case LIVEATLAS -> new LiveAtlasConnection(profile, http);
            case PL3XMAP -> new Pl3xMapConnection(profile, http);
            case PLAYERS_JSON -> new PlayersJsonConnection(profile, http);
            case SQUAREMAP -> new SquareMapConnection(profile, http);
        };
    }
}
