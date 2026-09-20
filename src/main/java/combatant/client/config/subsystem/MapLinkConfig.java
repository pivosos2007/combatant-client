/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.MapLinkProfilesValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.maplink.model.MapLinkProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backend ownership for MapLink networking and persisted server profiles. */
@ConfigSubsystem(value = "map/maplink", settingOwner = "maplink")
public final class MapLinkConfig extends SubsystemConfig {
    public static final MapLinkConfig INSTANCE = new MapLinkConfig();

    private final BooleanValue enabled = bool("enabled", true);
    private final NumberValue<Integer> connectTimeoutMs = number("connectTimeoutMs", 10_000, 1_000, 60_000);
    private final NumberValue<Integer> requestTimeoutMs = number("requestTimeoutMs", 10_000, 1_000, 60_000);
    private final NumberValue<Integer> staleAfterMs = number("staleAfterMs", 15_000, 1_000, 300_000);
    private final NumberValue<Integer> maxBackoffMs = number("maxBackoffMs", 60_000, 1_000, 600_000);
    private final MapLinkProfilesValue profiles = value(new MapLinkProfilesValue("profiles"));

    private MapLinkConfig() {
        loadConfig();
    }

    public static MapLinkConfig get() {
        return INSTANCE;
    }

    public boolean enabled() {
        return enabled.get();
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs.get().intValue();
    }

    public int requestTimeoutMs() {
        return requestTimeoutMs.get().intValue();
    }

    public int staleAfterMs() {
        return staleAfterMs.get().intValue();
    }

    public int maxBackoffMs() {
        return maxBackoffMs.get().intValue();
    }

    public List<MapLinkProfile> profiles() {
        return profiles.get().stream()
                .filter(MapLinkConfig::isConfiguredProfile)
                .toList();
    }

    public static boolean isConfiguredProfile(MapLinkProfile profile) {
        return profile != null
                && profile.serverMatcher() != null && !profile.serverMatcher().isBlank()
                && profile.baseUrl() != null && !profile.baseUrl().isBlank();
    }

    public List<MapLinkProfile> allProfiles() {
        return List.copyOf(profiles.get());
    }

    public void setProfiles(List<MapLinkProfile> newProfiles) {
        profiles.set(newProfiles);
        saveConfig();
    }

    /** Stores auto-discovered web-map world aliases for invisibility/reconnect recovery. */
    public synchronized void rememberDimensionMappings(String profileId, Map<String, String> learnedMappings) {
        if (profileId == null || profileId.isBlank() || learnedMappings == null || learnedMappings.isEmpty()) return;
        List<MapLinkProfile> current = profiles.get();
        List<MapLinkProfile> next = new ArrayList<>(current.size());
        boolean changed = false;
        for (MapLinkProfile profile : current) {
            if (profile == null || !profile.id().equals(profileId)) {
                next.add(profile);
                continue;
            }
            LinkedHashMap<String, String> mappings = new LinkedHashMap<>(profile.dimensionMappings());
            boolean profileChanged = false;
            for (Map.Entry<String, String> entry : learnedMappings.entrySet()) {
                String providerWorld = entry.getKey() == null ? "" : entry.getKey().trim();
                String minecraftDimension = entry.getValue() == null ? "" : entry.getValue().trim();
                if (providerWorld.isBlank() || minecraftDimension.isBlank()) continue;
                String previous = mappings.put(providerWorld, minecraftDimension);
                if (!minecraftDimension.equals(previous)) profileChanged = true;
            }
            if (profileChanged) {
                changed = true;
                next.add(new MapLinkProfile(profile.id(), profile.displayName(), profile.serverMatcher(), profile.baseUrl(),
                        profile.providerType(), profile.maxUpdateDelayMs(), profile.defaultY(), mappings, profile.requestHeaders()));
            } else {
                next.add(profile);
            }
        }
        if (changed) {
            profiles.set(next);
            saveConfig();
        }
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of(
                SettingDef.bool("enabled", enabled),
                SettingDef.number("connectTimeoutMs", connectTimeoutMs),
                SettingDef.number("requestTimeoutMs", requestTimeoutMs),
                SettingDef.number("staleAfterMs", staleAfterMs),
                SettingDef.number("maxBackoffMs", maxBackoffMs)
        );
    }
}
