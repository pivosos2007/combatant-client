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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.util.map.maplink.model.MapLinkFetchContext;
import combatant.client.util.map.maplink.model.MapLinkFetchResult;
import combatant.client.util.map.maplink.model.MapLinkProfile;
import combatant.client.util.map.maplink.model.MapLinkProviderType;
import combatant.client.util.map.maplink.model.MapLinkRawPlayer;
import combatant.client.util.map.maplink.net.MapLinkHttpClient;
import combatant.client.util.map.maplink.net.MapLinkParseException;
import combatant.client.util.map.maplink.net.MapLinkUris;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class PlayersJsonConnection extends AbstractMapLinkConnection {
    private final URI playersUri;

    public PlayersJsonConnection(MapLinkProfile profile, MapLinkHttpClient http) {
        this(profile, http, playersEndpoint(profile.baseUrl()));
    }

    PlayersJsonConnection(MapLinkProfile profile, MapLinkHttpClient http, URI playersUri) {
        super(profile, http);
        this.playersUri = playersUri;
    }

    @Override
    public MapLinkProviderType type() {
        return MapLinkProviderType.PLAYERS_JSON;
    }

    @Override
    public MapLinkFetchResult fetchPlayers(MapLinkFetchContext context) throws IOException, InterruptedException {
        String body = http.text(playersUri, profile);
        try {
            JsonElement rootElement = JsonParser.parseString(body);
            if (!rootElement.isJsonObject()) throw new MapLinkParseException("PlayersJson root is not an object");
            JsonObject root = rootElement.getAsJsonObject();
            JsonArray players = root.has("players") && root.get("players").isJsonArray()
                    ? root.getAsJsonArray("players") : new JsonArray();

            List<MapLinkRawPlayer> out = new ArrayList<>(players.size());
            for (JsonElement element : players) {
                if (!element.isJsonObject()) continue;
                JsonObject player = element.getAsJsonObject();
                String name = string(player, "name");
                if (name.isBlank()) continue;
                String world = MapLinkProfile.normalizeWorld(string(player, "world"));
                double x = number(player, "x", 0.0);
                double y = number(player, "y", profile.defaultY());
                double z = number(player, "z", 0.0);
                String uuid = string(player, "uuid");
                out.add(new MapLinkRawPlayer(name, parseUuid(uuid), world, x, y, z, 0L, ""));
            }
            return MapLinkFetchResult.of(out, poll(1_000L));
        } catch (MapLinkParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MapLinkParseException("Invalid PlayersJson payload from " + playersUri, e);
        }
    }

    public URI playersUri() {
        return playersUri;
    }

    static URI playersEndpoint(String rawBase) {
        URI base = MapLinkUris.normalizeBase(rawBase);
        String text = base.toString();
        if (text.endsWith("players.json")) return base;
        if (text.endsWith("/tiles")) return URI.create(text + "/players.json");
        return MapLinkUris.append(base, "/tiles/players.json");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() ? value.getAsString() : "";
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble() : fallback;
    }
}
