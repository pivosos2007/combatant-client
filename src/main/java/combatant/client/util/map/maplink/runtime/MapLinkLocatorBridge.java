/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.runtime;

import combatant.client.util.map.maplink.model.MapLinkObservation;
import combatant.client.util.map.maplink.model.MapLinkProfile;
import combatant.client.util.map.maplink.model.MapLinkSnapshot;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Read-only locator adapter over {@link MapLinkRuntime}. */
public enum MapLinkLocatorBridge {
    ;

    public static Set<UUID> exactPlayerIds(Minecraft mc, String legacyServer, String legacyUrl) {
        MapLinkProfile fallback = legacyProfile(legacyServer, legacyUrl);
        MapLinkSnapshot snapshot = MapLinkRuntime.get().touch(mc, fallback);
        if (snapshot.observations().isEmpty()) return Collections.emptySet();

        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (MapLinkObservation observation : snapshot.observations()) {
            if (observation.resolvedUuid() != null) ids.add(observation.resolvedUuid());
        }
        return ids.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    private static MapLinkProfile legacyProfile(String server, String url) {
        if (server == null || server.isBlank() || url == null || url.isBlank() || "example".equalsIgnoreCase(url.trim())) {
            return null;
        }
        return MapLinkProfile.playersJsonLegacy(server, url);
    }
}
