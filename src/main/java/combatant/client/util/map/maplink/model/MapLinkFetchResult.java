/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record MapLinkFetchResult(List<MapLinkRawPlayer> players, long suggestedPollIntervalMs, Set<String> providerWorlds) {
    public MapLinkFetchResult {
        players = players == null ? List.of() : List.copyOf(players);
        suggestedPollIntervalMs = Math.max(250L, suggestedPollIntervalMs);
        providerWorlds = providerWorlds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(providerWorlds));
    }

    public static MapLinkFetchResult of(List<MapLinkRawPlayer> players, long pollMs) {
        LinkedHashSet<String> worlds = new LinkedHashSet<>();
        if (players != null) {
            for (MapLinkRawPlayer player : players) {
                if (player != null && !player.providerWorld().isBlank()) worlds.add(player.providerWorld());
            }
        }
        return new MapLinkFetchResult(players, pollMs, worlds);
    }
}
