/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Adapted from Map Link.
 * Original copyright (C) 2024-2025 Leander Knüttel and contributors.
 * Original license: GNU General Public License v3.0 or later.
 * Dynmap upstream: RemotePlayers by ewpratten.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.connection;

import combatant.client.features.maplink.model.MapLinkFetchContext;
import combatant.client.features.maplink.model.MapLinkFetchResult;
import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.model.MapLinkProviderType;
import combatant.client.features.maplink.model.MapLinkRawPlayer;
import combatant.client.features.maplink.net.MapLinkHttpClient;
import combatant.client.features.maplink.net.MapLinkUris;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DynmapConnection extends AbstractMapLinkConnection {
    private static final Pattern JS_CONFIGURATION = Pattern.compile("configuration\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern JS_UPDATE = Pattern.compile("update\\s*:\\s*['\"]([^'\"]+)['\"]");

    private final URI base;
    private final String liveAtlasConfig;
    private volatile URI queryUri;
    private volatile long discoveredPollMs = 2_000L;
    private volatile boolean initialized;

    public DynmapConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this(profile, http, MapLinkUris.normalizeBase(profile.baseUrl()), null);
    }

    DynmapConnection(MapLinkProfile profile, MapLinkHttpClient http, URI base, String liveAtlasConfig) {
        super(profile, http);
        this.base = base;
        this.liveAtlasConfig = liveAtlasConfig;
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.DYNMAP;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        ensureInitialized();
        PlayerUpdate update = http.json(queryUri, profile, PlayerUpdate.class);
        List<MapLinkRawPlayer> out = new ArrayList<>(update.players == null ? 0 : update.players.length);
        Set<String> worlds = new LinkedHashSet<>();
        if (update.players != null) {
            for (Player player : update.players) {
                if (player == null || player.account == null || player.account.isBlank()) continue;
                String world = MapLinkProfile.normalizeWorld(player.world);
                if (!world.isBlank()) worlds.add(world);
                out.add(new MapLinkRawPlayer(player.account, null, world, player.x, player.y, player.z, 0L, ""));
            }
        }
        return new MapLinkFetchResult(out, poll(discoveredPollMs), worlds);
    }

    private void ensureInitialized() throws IOException, InterruptedException {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;

            if (liveAtlasConfig != null) {
                initializeFromConfig(liveAtlasConfig);
                initialized = true;
                return;
            }

            try {
                URI direct = URI.create(profile.baseUrl().trim().replace(" ", "%20"));
                PlayerUpdate probe = http.json(direct, profile, PlayerUpdate.class);
                if (probe != null && probe.players != null) {
                    queryUri = direct;
                    initialized = true;
                    return;
                }
            } catch (Exception ignored) {
            }

            try {
                String configJs = http.text(MapLinkUris.append(base, "/standalone/config.js"), profile);
                initializeFromConfig(configJs);
                initialized = true;
                return;
            } catch (Exception ignored) {
            }

            try {
                URI configuration = MapLinkUris.append(base, "/up/configuration");
                Config config = http.json(configuration, profile, Config.class);
                String world = firstWorld(config);
                discoveredPollMs = Math.max(1_000L, (long) Math.ceil(config.updaterate));
                queryUri = MapLinkUris.append(base, "/up/world/" + encode(world) + "/");
                initialized = true;
                return;
            } catch (Exception ignored) {
            }

            URI configuration = MapLinkUris.append(base, "/standalone/dynmap_config.json?");
            Config config = http.json(configuration, profile, Config.class);
            String world = firstWorld(config);
            discoveredPollMs = Math.max(1_000L, (long) Math.ceil(config.updaterate));
            queryUri = MapLinkUris.append(base, "/standalone/world/" + encode(world) + ".json?");
            initialized = true;
        }
    }

    private void initializeFromConfig(String configJs) throws IOException, InterruptedException {
        String configurationTarget = extract(JS_CONFIGURATION, configJs, "Dynmap configuration endpoint");
        URI configurationUri = MapLinkUris.resolveAgainstOrigin(base, configurationTarget);
        Config config = http.json(configurationUri, profile, Config.class);
        String world = firstWorld(config);
        discoveredPollMs = Math.max(1_000L, (long) Math.ceil(config.updaterate));

        String updateTarget = extract(JS_UPDATE, configJs, "Dynmap update endpoint")
                .replace("{timestamp}", "1")
                .replace("{world}", encode(world));
        queryUri = MapLinkUris.resolveAgainstOrigin(base, updateTarget);
    }

    private static String firstWorld(Config config) throws IOException {
        if (config == null || config.worlds == null || config.worlds.length == 0 || config.worlds[0] == null
                || config.worlds[0].name == null || config.worlds[0].name.isBlank()) {
            throw new IOException("Dynmap configuration has no worlds");
        }
        return config.worlds[0].name;
    }

    private static String extract(Pattern pattern, String source, String what) throws IOException {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        if (!matcher.find()) throw new IOException(what + " was not found");
        return matcher.group(1);
    }

    private static String encode(String value) {
        return value.replace(" ", "%20");
    }

    private static final class Config {
        float updaterate = 2_000.0f;
        World[] worlds = new World[0];
    }

    private static final class World {
        String name;
    }

    private static final class PlayerUpdate {
        Player[] players = new Player[0];
    }

    private static final class Player {
        String account;
        String world;
        double x;
        double y;
        double z;
    }
}
