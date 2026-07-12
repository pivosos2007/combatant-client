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

public record ConfigProfileSnapshot(
        ConfigProfileMeta meta,
        List<ConfigProfileEntry> entries
) {
    public ConfigProfileSnapshot {
        if (meta == null) throw new IllegalArgumentException("profile meta is null");
        List<ConfigProfileEntry> copy = new ArrayList<>();
        if (entries != null) copy.addAll(entries);
        entries = Collections.unmodifiableList(copy);
    }
}
