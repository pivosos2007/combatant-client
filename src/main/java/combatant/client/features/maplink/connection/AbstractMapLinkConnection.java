/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.connection;

import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.net.MapLinkHttpClient;

import java.util.UUID;

abstract class AbstractMapLinkConnection implements MapLinkConnection {
    protected final MapLinkProfile profile;
    protected final MapLinkHttpClient http;

    protected AbstractMapLinkConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this.profile = profile;
        this.http = http;
    }

    protected long poll(long providerDefaultMs) {
        long providerDelay = Math.max(MapLinkProfile.MIN_MAX_UPDATE_DELAY_MS, providerDefaultMs);
        return Math.min(profile.maxUpdateDelayMs(), providerDelay);
    }

    protected static UUID parseUuid(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        try {
            if (value.length() == 32 && value.indexOf('-') < 0) {
                value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                        + "-" + value.substring(16, 20) + "-" + value.substring(20);
            }
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
