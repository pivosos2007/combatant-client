/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.config.values;

import combatant.client.util.map.maplink.model.MapLinkProfile;
import combatant.client.util.map.maplink.model.MapLinkProviderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Structured backend-only value for MapLink provider profiles. */
public final class MapLinkProfilesValue extends ConfigValue<List<MapLinkProfile>> {
    public MapLinkProfilesValue(String name) {
        super(name, List.of());
    }

    @Override
    public void set(List<MapLinkProfile> profiles) {
        value = profiles == null ? List.of() : List.copyOf(profiles);
    }

    @Override
    public Object toJson() {
        List<Map<String, Object>> out = new ArrayList<>(value.size());
        for (MapLinkProfile profile : value) {
            if (profile == null) continue;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", profile.id());
            row.put("displayName", profile.displayName());
            row.put("enabled", profile.enabled());
            row.put("serverMatcher", profile.serverMatcher());
            row.put("baseUrl", profile.baseUrl());
            row.put("providerType", profile.providerType().name().toLowerCase(Locale.ROOT));
            row.put("refreshIntervalMs", profile.refreshIntervalMs());
            row.put("defaultY", profile.defaultY());
            row.put("sourcePriority", profile.sourcePriority());
            row.put("dimensionMappings", profile.dimensionMappings());
            row.put("requestHeaders", profile.requestHeaders());
            out.add(row);
        }
        return out;
    }

    @Override
    public void fromJson(Object json) {
        if (!(json instanceof List<?> rows)) return;
        List<MapLinkProfile> profiles = new ArrayList<>();
        int fallbackIndex = 0;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) continue;
            String id = string(row.get("id"), "profile-" + fallbackIndex++);
            String displayName = string(row.get("displayName"), id);
            boolean enabled = bool(row.get("enabled"), true);
            String serverMatcher = string(row.get("serverMatcher"), "");
            String baseUrl = string(row.get("baseUrl"), "");
            MapLinkProviderType providerType = provider(row.get("providerType"));
            long refreshIntervalMs = number(row.get("refreshIntervalMs"), 0L).longValue();
            int defaultY = number(row.get("defaultY"), 64).intValue();
            int sourcePriority = number(row.get("sourcePriority"), 0).intValue();
            Map<String, String> mappings = stringMap(row.get("dimensionMappings"));
            Map<String, String> headers = stringMap(row.get("requestHeaders"));
            profiles.add(new MapLinkProfile(id, displayName, enabled, serverMatcher, baseUrl, providerType,
                    refreshIntervalMs, defaultY, sourcePriority, mappings, headers));
        }
        value = List.copyOf(profiles);
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static Number number(Object value, Number fallback) {
        return value instanceof Number n ? n : fallback;
    }

    private static MapLinkProviderType provider(Object value) {
        if (value instanceof String raw) {
            String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            if (normalized.equals("BLUEMAP")) normalized = "BLUEMAP";
            if (normalized.equals("SQUARE_MAP")) normalized = "SQUAREMAP";
            if (normalized.equals("PLAYERSJSON")) normalized = "PLAYERS_JSON";
            try {
                return MapLinkProviderType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return MapLinkProviderType.PLAYERS_JSON;
    }

    private static Map<String, String> stringMap(Object value) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) return out;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = String.valueOf(entry.getKey()).trim();
            String text = String.valueOf(entry.getValue()).trim();
            if (!key.isEmpty() && !text.isEmpty()) out.put(key, text);
        }
        return out;
    }
}
