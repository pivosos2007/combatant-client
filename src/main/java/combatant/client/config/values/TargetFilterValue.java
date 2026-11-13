/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import combatant.client.config.common.impl.TargetFilters;

import java.util.Map;

public final class TargetFilterValue extends BooleanMapValue {
    private final boolean fixedPlayersOnly;

    public TargetFilterValue(String name, Map<String, Boolean> defaults, boolean fixedPlayersOnly) {
        super(name, mergedDefaults(defaults));
        this.fixedPlayersOnly = fixedPlayersOnly;
    }

    private static Map<String, Boolean> mergedDefaults(Map<String, Boolean> overrides) {
        Map<String, Boolean> defaults = new TargetFilters().defaults();
        defaults.putAll(overrides);
        return defaults;
    }

    public boolean fixedPlayersOnly() {
        return fixedPlayersOnly;
    }

    @Override
    public boolean get(String key) {
        if (fixedPlayersOnly && TargetFilters.PLAYERS_ONLY.equals(key)) return true;
        if (fixedPlayersOnly && TargetFilters.MOBS_ONLY.equals(key)) return false;
        return super.get(key);
    }
}
