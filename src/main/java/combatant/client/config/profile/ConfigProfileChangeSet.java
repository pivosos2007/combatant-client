/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies runtime edits to a stored profile without discarding entries or values unavailable in this session. */
final class ConfigProfileChangeSet {
    private ConfigProfileChangeSet() {
    }

    static boolean hasChanges(List<ConfigProfileEntry> baseline, List<ConfigProfileEntry> current) {
        Map<String, ConfigProfileEntry> before = byOwner(baseline);
        for (ConfigProfileEntry entry : current) {
            ConfigProfileEntry old = before.get(entry.ownerId());
            if (old == null) return true;
            for (Map.Entry<String, Object> value : entry.values().entrySet()) {
                if (!old.values().containsKey(value.getKey())
                        || !Objects.equals(old.values().get(value.getKey()), value.getValue())) return true;
            }
        }
        Map<String, ConfigProfileEntry> after = byOwner(current);
        for (String owner : before.keySet()) {
            if (owner.startsWith("theme:") && !after.containsKey(owner)) return true;
        }
        return false;
    }

    static List<ConfigProfileEntry> merge(List<ConfigProfileEntry> stored,
                                          List<ConfigProfileEntry> baseline,
                                          List<ConfigProfileEntry> current) {
        Map<String, ConfigProfileEntry> before = byOwner(baseline);
        Map<String, ConfigProfileEntry> updated = byOwner(stored);
        for (ConfigProfileEntry entry : current) {
            ConfigProfileEntry old = before.get(entry.ownerId());
            if (old == null) {
                ConfigProfileEntry saved = updated.get(entry.ownerId());
                if (saved == null) {
                    updated.put(entry.ownerId(), entry);
                } else {
                    Map<String, Object> values = new LinkedHashMap<>(saved.values());
                    values.putAll(entry.values());
                    updated.put(entry.ownerId(), new ConfigProfileEntry(
                            entry.ownerId(), entry.displayName(), values));
                }
                continue;
            }
            ConfigProfileEntry saved = updated.get(entry.ownerId());
            Map<String, Object> values = saved == null
                    ? new LinkedHashMap<>(entry.values())
                    : new LinkedHashMap<>(saved.values());
            boolean changed = false;
            for (Map.Entry<String, Object> value : entry.values().entrySet()) {
                if (!old.values().containsKey(value.getKey())
                        || !Objects.equals(old.values().get(value.getKey()), value.getValue())) {
                    values.put(value.getKey(), value.getValue());
                    changed = true;
                }
            }
            if (changed) updated.put(entry.ownerId(), new ConfigProfileEntry(
                    entry.ownerId(), entry.displayName(), values));
        }
        Map<String, ConfigProfileEntry> after = byOwner(current);
        for (String owner : before.keySet()) {
            if (owner.startsWith("theme:") && !after.containsKey(owner)) updated.remove(owner);
        }
        return new ArrayList<>(updated.values());
    }

    private static Map<String, ConfigProfileEntry> byOwner(List<ConfigProfileEntry> entries) {
        Map<String, ConfigProfileEntry> map = new LinkedHashMap<>();
        for (ConfigProfileEntry entry : entries) map.put(entry.ownerId(), entry);
        return map;
    }
}
