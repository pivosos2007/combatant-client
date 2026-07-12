/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.util.*;

public final class ConfigProfileDiffEngine {
    public ConfigProfileDiff compare(ConfigProfileSnapshot current, ConfigProfileSnapshot selected) {
        if (current == null || selected == null) {
            return new ConfigProfileDiff(ConfigProfileType.MODULES, List.of());
        }
        ConfigProfileType type = selected.meta().type();
        Map<String, ConfigProfileEntry> currentMap = mapByOwner(current);
        Map<String, ConfigProfileEntry> selectedMap = mapByOwner(selected);

        Set<String> owners = new LinkedHashSet<>();
        owners.addAll(currentMap.keySet());
        owners.addAll(selectedMap.keySet());

        List<ConfigProfileDiff.Entry> entries = new ArrayList<>();
        for (String ownerId : owners) {
            ConfigProfileEntry c = currentMap.get(ownerId);
            ConfigProfileEntry s = selectedMap.get(ownerId);
            String displayName = s != null ? s.displayName() : c != null ? c.displayName() : ownerId;
            Map<String, Object> cv = c != null ? c.values() : Map.of();
            Map<String, Object> sv = s != null ? s.values() : Map.of();

            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(cv.keySet());
            keys.addAll(sv.keySet());

            List<ConfigProfileDiff.Value> values = new ArrayList<>();
            for (String key : keys) {
                boolean hasCurrent = cv.containsKey(key);
                boolean hasSelected = sv.containsKey(key);
                Object currentValue = cv.get(key);
                Object selectedValue = sv.get(key);
                if (hasCurrent && hasSelected && Objects.equals(currentValue, selectedValue)) continue;
                ConfigProfileDiff.Kind kind;
                if (!hasCurrent) kind = ConfigProfileDiff.Kind.ADDED;
                else if (!hasSelected) kind = ConfigProfileDiff.Kind.REMOVED;
                else kind = ConfigProfileDiff.Kind.CHANGED;
                values.add(new ConfigProfileDiff.Value(key, currentValue, selectedValue, kind));
            }
            if (!values.isEmpty()) {
                entries.add(new ConfigProfileDiff.Entry(ownerId, displayName, values));
            }
        }
        return new ConfigProfileDiff(type, entries);
    }

    private Map<String, ConfigProfileEntry> mapByOwner(ConfigProfileSnapshot snapshot) {
        Map<String, ConfigProfileEntry> map = new LinkedHashMap<>();
        for (ConfigProfileEntry entry : snapshot.entries()) {
            map.put(entry.ownerId(), entry);
        }
        return map;
    }
}
