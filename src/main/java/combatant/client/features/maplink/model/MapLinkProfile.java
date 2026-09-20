/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Persisted web-map provider profile. */
public final class MapLinkProfile {
    public static final long MIN_MAX_UPDATE_DELAY_MS = 1_000L;
    public static final long DEFAULT_MAX_UPDATE_DELAY_MS = 2_000L;
    public static final long MAX_MAX_UPDATE_DELAY_MS = 4_000L;

    private final String id;
    private final String displayName;
    private final String serverMatcher;
    private final String baseUrl;
    private final MapLinkProviderType providerType;
    private final long maxUpdateDelayMs;
    private final int defaultY;
    /** Hidden provider-world -> Minecraft-dimension aliases, learned automatically at runtime. */
    private final Map<String, String> dimensionMappings;
    private final Map<String, String> requestHeaders;

    public MapLinkProfile(String id,
                          String displayName,
                          String serverMatcher,
                          String baseUrl,
                          MapLinkProviderType providerType,
                          long maxUpdateDelayMs,
                          int defaultY,
                          Map<String, String> dimensionMappings,
                          Map<String, String> requestHeaders) {
        this.id = sanitizeId(id);
        this.displayName = displayName == null ? "" : displayName.trim();
        this.serverMatcher = serverMatcher == null ? "" : serverMatcher.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.providerType = providerType == null ? MapLinkProviderType.PLAYERS_JSON : providerType;
        this.maxUpdateDelayMs = Math.max(MIN_MAX_UPDATE_DELAY_MS, Math.min(MAX_MAX_UPDATE_DELAY_MS, maxUpdateDelayMs));
        this.defaultY = defaultY;
        this.dimensionMappings = sanitizeMap(dimensionMappings);
        this.requestHeaders = sanitizeMap(requestHeaders);
    }

    public static MapLinkProfile playersJsonLegacy(String serverMatcher, String baseUrl) {
        return new MapLinkProfile(
                "legacy-locator",
                "Legacy locator map",
                serverMatcher,
                baseUrl,
                MapLinkProviderType.PLAYERS_JSON,
                DEFAULT_MAX_UPDATE_DELAY_MS,
                64,
                Map.of(),
                Map.of()
        );
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String serverMatcher() { return serverMatcher; }
    public String baseUrl() { return baseUrl; }
    public MapLinkProviderType providerType() { return providerType; }
    public long maxUpdateDelayMs() { return maxUpdateDelayMs; }
    public int defaultY() { return defaultY; }
    public Map<String, String> dimensionMappings() { return Map.copyOf(dimensionMappings); }
    public Map<String, String> requestHeaders() { return Map.copyOf(requestHeaders); }

    public String mapWorld(String providerWorld) {
        String normalized = normalizeWorld(providerWorld);
        if (normalized.isEmpty()) return "";
        String direct = dimensionMappings.get(normalized);
        if (direct != null && !direct.isBlank()) return normalizeWorld(direct);
        String rawDirect = dimensionMappings.get(providerWorld == null ? "" : providerWorld.trim());
        return rawDirect == null || rawDirect.isBlank() ? normalized : normalizeWorld(rawDirect);
    }

    public static String normalizeWorld(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("minecraft_")) {
            value = value.replaceFirst("_", ":");
        }
        return value;
    }

    private static String sanitizeId(String raw) {
        String source = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty()) return "profile";
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.isEmpty() ? "profile" : out.toString();
    }

    private static LinkedHashMap<String, String> sanitizeMap(Map<String, String> source) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (source == null) return out;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            String value = entry.getValue().trim();
            if (!key.isEmpty() && !value.isEmpty()) out.put(key, value);
        }
        return out;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MapLinkProfile other)) return false;
        return maxUpdateDelayMs == other.maxUpdateDelayMs
                && defaultY == other.defaultY
                && Objects.equals(id, other.id)
                && Objects.equals(displayName, other.displayName)
                && Objects.equals(serverMatcher, other.serverMatcher)
                && Objects.equals(baseUrl, other.baseUrl)
                && providerType == other.providerType
                && Objects.equals(dimensionMappings, other.dimensionMappings)
                && Objects.equals(requestHeaders, other.requestHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, serverMatcher, baseUrl, providerType, maxUpdateDelayMs,
                defaultY, dimensionMappings, requestHeaders);
    }
}
