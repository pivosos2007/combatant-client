/*
 * This file is part of the Combatant Client distribution.
 *
 * This file contains code adapted from Map Link.
 * Original Map Link code:
 * Copyright (C) 2024 - 2025 Leander Knüttel and contributors.
 * Licensed under the GNU General Public License, version 3 or
 * (at your option) any later version.
 *
 * Combatant modifications:
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.connection;

import combatant.client.util.map.maplink.model.MapLinkFetchContext;
import combatant.client.util.map.maplink.model.MapLinkFetchResult;
import combatant.client.util.map.maplink.model.MapLinkProfile;
import combatant.client.util.map.maplink.model.MapLinkProviderType;
import combatant.client.util.map.maplink.model.MapLinkRawPlayer;
import combatant.client.util.map.maplink.net.MapLinkHttpClient;
import combatant.client.util.map.maplink.net.MapLinkUris;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlueMapConnection extends AbstractMapLinkConnection {
    private final URI base;
    private volatile List<WorldEndpoint> worlds = List.of();

    public BlueMapConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this(profile, http, MapLinkUris.normalizeBase(profile.baseUrl()));
    }

    BlueMapConnection(MapLinkProfile profile, MapLinkHttpClient http, URI base) {
        super(profile, http);
        this.base = base;
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.BLUEMAP;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        ensureWorlds();
        Map<String, MapLinkRawPlayer> merged = new LinkedHashMap<>();
        Set<String> providerWorlds = new LinkedHashSet<>();
        IOException lastIo = null;
        int successfulWorlds = 0;

        for (WorldEndpoint world : worlds) {
            try {
                PlayerUpdate update = http.json(world.playersUri, profile, PlayerUpdate.class);
                successfulWorlds++;
                providerWorlds.add(world.id);
                if (update.players == null) continue;
                for (Player player : update.players) {
                    if (player == null || player.name == null || player.name.isBlank() || player.position == null) continue;
                    // BlueMap exposes players of other maps as foreign. Keep only the authoritative copy,
                    // so multi-map polling preserves real provider-world identity instead of "thisWorld/foreign".
                    if (player.foreign) continue;
                    UUID uuid = parseUuid(player.uuid);
                    String key = (uuid != null ? uuid.toString() : player.name.toLowerCase()) + "@" + world.id;
                    merged.put(key, new MapLinkRawPlayer(player.name, uuid, world.id,
                            player.position.x, player.position.y, player.position.z, 0L, ""));
                }
            } catch (IOException e) {
                lastIo = e;
            }
        }

        if (successfulWorlds == 0 && lastIo != null) throw lastIo;
        return new MapLinkFetchResult(new ArrayList<>(merged.values()), poll(1_000L), providerWorlds);
    }

    private void ensureWorlds() throws IOException, InterruptedException {
        if (!worlds.isEmpty()) return;
        synchronized (this) {
            if (!worlds.isEmpty()) return;
            Configuration configuration = http.json(MapLinkUris.append(base, "/settings.json?"), profile, Configuration.class);
            if (configuration.maps == null || configuration.maps.length == 0) {
                throw new IOException("BlueMap configuration has no maps");
            }
            List<WorldEndpoint> found = new ArrayList<>(configuration.maps.length);
            for (String world : configuration.maps) {
                if (world == null || world.isBlank()) continue;
                String encoded = world.replace(" ", "%20");
                found.add(new WorldEndpoint(world, MapLinkUris.append(base, "/maps/" + encoded + "/live/players.json?")));
            }
            if (found.isEmpty()) throw new IOException("BlueMap configuration has no usable maps");
            worlds = List.copyOf(found);
        }
    }

    private record WorldEndpoint(String id, URI playersUri) {}

    private static final class Configuration {
        String[] maps = new String[0];
    }

    private static final class PlayerUpdate {
        Player[] players = new Player[0];
    }

    private static final class Player {
        String name;
        String uuid;
        boolean foreign;
        Vec3 position;
    }

    private static final class Vec3 {
        double x;
        double y;
        double z;
    }
}
