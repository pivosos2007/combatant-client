/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.model;

public enum MapLinkProfileStatus {
    DISABLED,
    IDLE,
    CONNECTING,
    LIVE,
    STALE,
    AUTH_ERROR,
    HTTP_ERROR,
    PARSE_ERROR,
    WORLD_UNMAPPED
}
