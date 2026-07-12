/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ConfigProfileDiff(
        ConfigProfileType type,
        List<Entry> entries
) {
    public ConfigProfileDiff {
        if (type == null) type = ConfigProfileType.MODULES;
        List<Entry> copy = new ArrayList<>();
        if (entries != null) copy.addAll(entries);
        entries = Collections.unmodifiableList(copy);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int valueChangeCount() {
        int count = 0;
        for (Entry entry : entries) count += entry.values().size();
        return count;
    }

    public enum Kind {
        ADDED,
        REMOVED,
        CHANGED
    }

    public record Entry(String ownerId, String displayName, List<Value> values) {
        public Entry {
            if (ownerId == null) ownerId = "";
            if (displayName == null || displayName.isBlank()) displayName = ownerId;
            List<Value> copy = new ArrayList<>();
            if (values != null) copy.addAll(values);
            values = Collections.unmodifiableList(copy);
        }
    }

    public record Value(String key, Object currentValue, Object selectedValue, Kind kind) {
        public Value {
            if (key == null) key = "";
            if (kind == null) kind = Kind.CHANGED;
        }
    }
}
