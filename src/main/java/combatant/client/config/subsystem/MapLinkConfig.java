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
import combatant.client.util.map.maplink.model.MapLinkProfile;

import java.util.Comparator;
import java.util.List;

/**
 * Backend ownership for MapLink networking. No GUI contributor is registered yet.
 */
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
                .filter(MapLinkProfile::enabled)
                .sorted(Comparator.comparingInt(MapLinkProfile::sourcePriority).reversed())
                .toList();
    }

    public List<MapLinkProfile> allProfiles() {
        return List.copyOf(profiles.get());
    }

    public void setProfiles(List<MapLinkProfile> newProfiles) {
        profiles.set(newProfiles);
        saveConfig();
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        // Intentionally backend-only in this phase. Values are persisted and editable by config file/API,
        // but no ClickGUI surface is exposed yet.
        return List.of();
    }
}
