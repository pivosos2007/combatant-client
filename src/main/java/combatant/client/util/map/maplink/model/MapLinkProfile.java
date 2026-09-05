/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Combatant-owned provider profile. Deliberately contains no GUI or Xaero objects.
 */
public final class MapLinkProfile {
    private String id;
    private String displayName;
    private boolean enabled;
    private String serverMatcher;
    private String baseUrl;
    private MapLinkProviderType providerType;
    private long refreshIntervalMs;
    private int defaultY;
    private int sourcePriority;
    private final Map<String, String> dimensionMappings;
    private final Map<String, String> requestHeaders;

    public MapLinkProfile(String id,
                          String displayName,
                          boolean enabled,
                          String serverMatcher,
                          String baseUrl,
                          MapLinkProviderType providerType,
                          long refreshIntervalMs,
                          int defaultY,
                          int sourcePriority,
                          Map<String, String> dimensionMappings,
                          Map<String, String> requestHeaders) {
        this.id = sanitizeId(id);
        this.displayName = displayName == null ? "" : displayName.trim();
        this.enabled = enabled;
        this.serverMatcher = serverMatcher == null ? "" : serverMatcher.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.providerType = providerType == null ? MapLinkProviderType.PLAYERS_JSON : providerType;
        this.refreshIntervalMs = Math.max(0L, refreshIntervalMs);
        this.defaultY = defaultY;
        this.sourcePriority = sourcePriority;
        this.dimensionMappings = sanitizeMap(dimensionMappings);
        this.requestHeaders = sanitizeMap(requestHeaders);
    }

    public static MapLinkProfile playersJsonLegacy(String serverMatcher, String baseUrl) {
        return new MapLinkProfile(
                "legacy-locator",
                "Legacy locator map",
                true,
                serverMatcher,
                baseUrl,
                MapLinkProviderType.PLAYERS_JSON,
                10_000L,
                64,
                0,
                Map.of(),
                Map.of()
        );
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public String serverMatcher() { return serverMatcher; }
    public String baseUrl() { return baseUrl; }
    public MapLinkProviderType providerType() { return providerType; }
    public long refreshIntervalMs() { return refreshIntervalMs; }
    public int defaultY() { return defaultY; }
    public int sourcePriority() { return sourcePriority; }
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

    public boolean hasExplicitWorldMappings() {
        return !dimensionMappings.isEmpty();
    }

    public boolean hasMappingFor(String providerWorld) {
        if (!hasExplicitWorldMappings()) return true;
        String normalized = normalizeWorld(providerWorld);
        return dimensionMappings.containsKey(normalized)
                || dimensionMappings.containsKey(providerWorld == null ? "" : providerWorld.trim());
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
        return enabled == other.enabled
                && refreshIntervalMs == other.refreshIntervalMs
                && defaultY == other.defaultY
                && sourcePriority == other.sourcePriority
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
        return Objects.hash(id, displayName, enabled, serverMatcher, baseUrl, providerType, refreshIntervalMs,
                defaultY, sourcePriority, dimensionMappings, requestHeaders);
    }
}
