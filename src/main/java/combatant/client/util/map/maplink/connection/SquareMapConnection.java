/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Adapted from Map Link.
 * Original copyright (C) 2024-2025 Leander Knüttel and contributors.
 * Original license: GNU General Public License v3.0 or later.
 *
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SquareMapConnection extends AbstractMapLinkConnection {
    private final URI base;
    private final URI playersUri;
    private volatile long discoveredPollMs = 1_000L;
    private volatile boolean cadenceResolved;

    public SquareMapConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this(profile, http, MapLinkUris.normalizeBase(profile.baseUrl()));
    }

    SquareMapConnection(MapLinkProfile profile, MapLinkHttpClient http, URI base) {
        super(profile, http);
        this.base = base;
        this.playersUri = MapLinkUris.append(base, "/tiles/players.json");
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.SQUAREMAP;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        PlayerUpdate update = http.json(playersUri, profile, PlayerUpdate.class);
        List<MapLinkRawPlayer> out = new ArrayList<>(update.players == null ? 0 : update.players.length);
        Set<String> worlds = new LinkedHashSet<>();
        if (update.players != null) {
            for (Player player : update.players) {
                if (player == null || player.name == null || player.name.isBlank()) continue;
                String world = MapLinkProfile.normalizeWorld(player.world);
                if (!world.isBlank()) worlds.add(world);
                double y = player.y == Integer.MIN_VALUE ? profile.defaultY() : player.y;
                out.add(new MapLinkRawPlayer(player.name, parseUuid(player.uuid), world, player.x, y, player.z, 0L, ""));
            }
        }
        resolveCadenceOnce();
        return new MapLinkFetchResult(out, poll(discoveredPollMs), worlds);
    }

    private void resolveCadenceOnce() {
        if (cadenceResolved || profile.refreshIntervalMs() > 0) return;
        synchronized (this) {
            if (cadenceResolved) return;
            cadenceResolved = true;
            try {
                Configuration configuration = http.json(MapLinkUris.append(base, "/tiles/settings.json"), profile, Configuration.class);
                float seconds = 1.0f;
                if (configuration.worlds != null) {
                    for (World world : configuration.worlds) {
                        if (world == null || world.name == null || world.name.isBlank()) continue;
                        WorldSettings settings = http.json(
                                MapLinkUris.append(base, "/tiles/" + world.name.replace(" ", "%20") + "/settings.json"),
                                profile,
                                WorldSettings.class
                        );
                        if (settings.player_tracker != null) seconds = Math.max(seconds, settings.player_tracker.update_interval);
                    }
                }
                discoveredPollMs = Math.max(1_000L, (long) Math.ceil(seconds * 1_000.0));
            } catch (Exception ignored) {
                discoveredPollMs = 1_000L;
            }
        }
    }

    private static final class PlayerUpdate {
        Player[] players = new Player[0];
    }

    private static final class Player {
        String name;
        String world;
        int x;
        int y = Integer.MIN_VALUE;
        int z;
        String uuid;
    }

    private static final class Configuration {
        World[] worlds = new World[0];
    }

    private static final class World {
        String name;
    }

    private static final class WorldSettings {
        PlayerTracker player_tracker;
    }

    private static final class PlayerTracker {
        float update_interval = 1.0f;
    }
}
