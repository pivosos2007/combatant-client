/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import combatant.client.features.gui.hud.nondraggable.impl.CustomBar;
import combatant.client.util.logging.DebugLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public enum MapPlayersCache {
    ;
    private static final long REFRESH_MS = 10_000L;
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
    private static volatile Set<UUID> cachedPlayers = Collections.emptySet();
    private static volatile long lastFetchMs = 0L;
    private static volatile String lastUrl = null;

    public static Set<UUID> getPlayers(Minecraft mc, CustomBar bm) {
        if (mc == null || bm == null) return Collections.emptySet();
        if (!bm.isLocatorMapFilterEnabled()) return Collections.emptySet();

        String serverHost = bm.getLocatorMapServer();
        if (serverHost == null || serverHost.isBlank()) return Collections.emptySet();
        if (!isOnServer(mc, serverHost)) return Collections.emptySet();

        String baseUrl = bm.getLocatorMapUrl();
        if (baseUrl == null || baseUrl.isBlank()) return Collections.emptySet();

        String playersUrl = normalizePlayersUrl(baseUrl);
        if (playersUrl == null || playersUrl.isBlank()) return Collections.emptySet();

        long now = System.currentTimeMillis();
        if (!playersUrl.equals(lastUrl)) {
            lastUrl = playersUrl;
            cachedPlayers = Collections.emptySet();
            lastFetchMs = 0L;
        }

        if (now - lastFetchMs >= REFRESH_MS && FETCHING.compareAndSet(false, true)) {
            DebugLog.server("Locator map fetch start: %s", playersUrl);
            CompletableFuture.runAsync(() -> fetch(playersUrl));
        }

        return cachedPlayers;
    }

    private static void fetch(String playersUrl) {
        try {
            String json = httpGet(playersUrl);
            if (json == null || json.isBlank()) {
                DebugLog.server("Locator map fetch empty: %s", playersUrl);
                return;
            }

            Set<UUID> next = parsePlayers(json);
            cachedPlayers = next;
            DebugLog.server("Locator map fetch ok: %d player(s)", next.size());
        } catch (Exception ignored) {
            DebugLog.server("Locator map fetch failed: %s", playersUrl);
        } finally {
            lastFetchMs = System.currentTimeMillis();
            FETCHING.set(false);
        }
    }

    private static String httpGet(String urlText) throws Exception {
        URL url = URI.create(urlText).toURL();
        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        request.setRequestMethod("GET");
        request.setRequestProperty("Content-Type", "application/json");
        request.setInstanceFollowRedirects(true);
        request.setConnectTimeout(10_000);
        request.setReadTimeout(10_000);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private static Set<UUID> parsePlayers(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray players = root.has("players") ? root.getAsJsonArray("players") : new JsonArray();
            if (players == null || players.isEmpty()) return Collections.emptySet();

            Set<UUID> ids = new HashSet<>();
            for (JsonElement el : players) {
                if (!el.isJsonObject()) continue;
                JsonObject p = el.getAsJsonObject();
                if (!p.has("uuid")) continue;
                String raw = p.get("uuid").getAsString();
                UUID uuid = parseUuid(raw);
                if (uuid != null) ids.add(uuid);
            }
            return ids;
        } catch (Exception ignored) {
            return Collections.emptySet();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            if (s.indexOf('-') >= 0) {
                return UUID.fromString(s);
            }
            if (s.length() == 32) {
                String dashed = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                        + "-" + s.substring(16, 20) + "-" + s.substring(20);
                return UUID.fromString(dashed);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static String normalizePlayersUrl(String baseUrl) {
        String b = baseUrl.trim();
        if (b.isEmpty()) return "";
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);

        if (b.endsWith("players.json")) {
            return b;
        }
        if (b.endsWith("/tiles")) {
            return b + "/players.json";
        }
        return b + "/tiles/players.json";
    }

    private static boolean isOnServer(Minecraft mc, String serverHost) {
        if (mc == null || mc.hasSingleplayerServer()) return false;
        ServerData info = mc.getCurrentServer();
        if (info == null || info.ip == null) return false;
        String current = stripPort(info.ip);
        return current.equalsIgnoreCase(serverHost.trim());
    }

    private static String stripPort(String address) {
        if (address == null) return "";
        String s = address.trim();
        if (s.isEmpty()) return "";

        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 1) {
                return s.substring(1, end);
            }
            return s;
        }

        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0 && lastColon + 1 < s.length()) {
            String tail = s.substring(lastColon + 1);
            boolean digits = true;
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (c < '0' || c > '9') {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                return s.substring(0, lastColon);
            }
        }
        return s;
    }
}
