/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.net;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import combatant.client.config.subsystem.MapLinkConfig;
import combatant.client.util.map.maplink.model.MapLinkProfile;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Shared HTTP transport for all web-map providers. */
public final class MapLinkHttpClient {
    private static final Gson GSON = new Gson();
    private final HttpClient client;
    private final MapLinkConfig config;

    public MapLinkHttpClient(MapLinkConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public String text(URI uri, MapLinkProfile profile) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Accept", "application/json,text/plain,text/html;q=0.9,*/*;q=0.8")
                .header("User-Agent", "Combatant-MapLink/1");
        if (profile != null) {
            for (Map.Entry<String, String> header : profile.requestHeaders().entrySet()) {
                if (!header.getKey().isBlank() && !header.getValue().isBlank()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new MapLinkHttpException(status, "HTTP " + status + " for " + uri);
        }
        return response.body() == null ? "" : response.body();
    }

    public <T> T json(URI uri, MapLinkProfile profile, Class<T> type) throws IOException, InterruptedException {
        return parse(text(uri, profile), type, uri);
    }

    public <T> T json(URI uri, MapLinkProfile profile, Type type) throws IOException, InterruptedException {
        try {
            T value = GSON.fromJson(text(uri, profile), type);
            if (value == null) throw new MapLinkParseException("Empty JSON object from " + uri);
            return value;
        } catch (JsonParseException | IllegalStateException e) {
            throw new MapLinkParseException("Invalid JSON from " + uri, e);
        }
    }

    private static <T> T parse(String text, Class<T> type, URI uri) throws IOException {
        try {
            T value = GSON.fromJson(text, type);
            if (value == null) throw new MapLinkParseException("Empty JSON object from " + uri);
            return value;
        } catch (JsonParseException | IllegalStateException e) {
            throw new MapLinkParseException("Invalid JSON from " + uri, e);
        }
    }
}
