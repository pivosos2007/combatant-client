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
import combatant.client.util.map.maplink.net.MapLinkHttpClient;
import combatant.client.util.map.maplink.net.MapLinkUris;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveAtlasConnection extends AbstractMapLinkConnection {
    private static final Pattern DYNMAP = Pattern.compile("dynmap\\s*:\\s*\\{(?:\\R|.)*?[^}\"']*}", Pattern.MULTILINE);
    private static final Pattern PL3XMAP = Pattern.compile("pl3xmap\\s*:\\s*['\"](.+?)['\"]");
    private static final Pattern SQUAREMAP = Pattern.compile("squaremap\\s*:\\s*['\"](.+?)['\"]");

    private final URI base;
    private volatile List<MapLinkConnection> connections = List.of();
    private volatile int preferredIndex;

    public LiveAtlasConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        super(profile, http);
        this.base = MapLinkUris.normalizeBase(profile.baseUrl());
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.LIVEATLAS;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        ensureConnections();
        IOException lastIo = null;
        MapLinkFetchResult firstSuccess = null;
        int size = connections.size();
        for (int offset = 0; offset < size; offset++) {
            int index = (preferredIndex + offset) % size;
            MapLinkConnection connection = connections.get(index);
            try {
                MapLinkFetchResult result = connection.fetchPlayers(context);
                if (firstSuccess == null) firstSuccess = result;
                if (containsLocalPlayer(result, context.localPlayerName())) {
                    preferredIndex = index;
                    return result;
                }
                if (!result.players().isEmpty() && firstSuccess.players().isEmpty()) firstSuccess = result;
            } catch (IOException e) {
                lastIo = e;
            }
        }
        if (firstSuccess != null) return firstSuccess;
        if (lastIo != null) throw lastIo;
        return MapLinkFetchResult.of(List.of(), poll(1_000L));
    }

    private void ensureConnections() throws IOException, InterruptedException {
        if (!connections.isEmpty()) return;
        synchronized (this) {
            if (!connections.isEmpty()) return;
            String html = http.text(base, profile);
            List<MapLinkConnection> found = new ArrayList<>();

            Matcher dynmap = DYNMAP.matcher(html);
            while (dynmap.find()) {
                String snippet = dynmap.group();
                try {
                    found.add(new DynmapConnection(profile, http, base, snippet));
                } catch (RuntimeException ignored) {
                }
            }

            Matcher pl3x = PL3XMAP.matcher(html);
            while (pl3x.find()) {
                try {
                    URI endpoint = MapLinkUris.resolveAgainstOrigin(base, pl3x.group(1));
                    found.add(new Pl3xMapConnection(profile, http, stripTrailingSlash(endpoint)));
                } catch (RuntimeException ignored) {
                }
            }

            Matcher square = SQUAREMAP.matcher(html);
            while (square.find()) {
                try {
                    URI endpoint = MapLinkUris.resolveAgainstOrigin(base, square.group(1));
                    found.add(new SquareMapConnection(profile, http, stripTrailingSlash(endpoint)));
                } catch (RuntimeException ignored) {
                }
            }

            if (found.isEmpty()) {
                found.add(new PlayersJsonConnection(profile, http, PlayersJsonConnection.playersEndpoint(base.toString())));
            }
            connections = List.copyOf(found);
        }
    }

    private static boolean containsLocalPlayer(MapLinkFetchResult result, String localName) {
        if (localName == null || localName.isBlank()) return false;
        return result.players().stream().anyMatch(player -> localName.equals(player.name()));
    }

    private static URI stripTrailingSlash(URI uri) {
        String text = uri.toString();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return URI.create(text);
    }
}
