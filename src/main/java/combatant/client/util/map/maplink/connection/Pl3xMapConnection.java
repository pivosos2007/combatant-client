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
import java.util.Objects;
import java.util.Set;

public final class Pl3xMapConnection extends AbstractMapLinkConnection {
    private final URI base;
    private final URI settingsUri;
    private volatile int version = -1;
    private volatile URI playersUri;
    private volatile long discoveredPollMs = 1_000L;
    private volatile boolean cadenceResolved;

    public Pl3xMapConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this(profile, http, MapLinkUris.normalizeBase(profile.baseUrl()));
    }

    Pl3xMapConnection(MapLinkProfile profile, MapLinkHttpClient http, URI base) {
        super(profile, http);
        this.base = base;
        this.settingsUri = MapLinkUris.append(base, "/tiles/settings.json");
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.PL3XMAP;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        ensureVersion();
        PlayerUpdate update = http.json(playersUri, profile, PlayerUpdate.class);
        List<MapLinkRawPlayer> out = new ArrayList<>(update.players == null ? 0 : update.players.length);
        Set<String> worlds = new LinkedHashSet<>();
        if (update.players != null) {
            for (Player player : update.players) {
                if (player == null || player.name == null || player.name.isBlank()) continue;
                String world = MapLinkProfile.normalizeWorld(player.world);
                if (!world.isBlank()) worlds.add(world);
                double x = version == 0 && player.position != null ? player.position.x : player.x;
                double z = version == 0 && player.position != null ? player.position.z : player.z;
                out.add(new MapLinkRawPlayer(player.name, parseUuid(player.uuid), world, x, profile.defaultY(), z, 0L, ""));
            }
        }
        resolveCadenceOnce(update);
        return new MapLinkFetchResult(out, poll(discoveredPollMs), worlds);
    }

    private void ensureVersion() throws IOException, InterruptedException {
        if (version >= 0) return;
        synchronized (this) {
            if (version >= 0) return;
            String settings = http.text(settingsUri, profile);
            if (settings.contains("\"players\":[")) {
                version = 0;
                playersUri = settingsUri;
            } else {
                version = 1;
                playersUri = MapLinkUris.append(base, "/tiles/players.json");
            }
        }
    }

    private void resolveCadenceOnce(PlayerUpdate update) {
        if (cadenceResolved || profile.refreshIntervalMs() > 0) return;
        synchronized (this) {
            if (cadenceResolved) return;
            cadenceResolved = true;
            try {
                float seconds = 1.0f;
                if (update.worldSettings != null) {
                    for (WorldSetting world : update.worldSettings) {
                        if (world == null || world.name == null || world.name.isBlank()) continue;
                        MarkerLayerConfig[] layers = http.json(
                                MapLinkUris.append(base, "/tiles/" + world.name.replace(":", "-").replace(" ", "%20") + "/markers.json"),
                                profile,
                                MarkerLayerConfig[].class
                        );
                        if (layers == null) continue;
                        for (MarkerLayerConfig layer : layers) {
                            if (layer != null && Objects.equals(layer.key, "pl3xmap_players")) {
                                seconds = Math.max(seconds, layer.updateInterval);
                            }
                        }
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
        WorldSetting[] worldSettings = new WorldSetting[0];
    }

    private static final class Player {
        String name;
        String world;
        int x;
        int z;
        Int3 position;
        String uuid;
    }

    private static final class Int3 {
        int x;
        int y;
        int z;
    }

    private static final class WorldSetting {
        String name;
    }

    private static final class MarkerLayerConfig {
        String key;
        float updateInterval = 1.0f;
    }
}
