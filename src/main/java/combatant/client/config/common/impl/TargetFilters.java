/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.common.CommonBooleanGroupSchema;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TargetFilters implements CommonBooleanGroupSchema {

    public static final String PLAYERS_ONLY = "players_only";
    public static final String IGNORE_FRIENDS = "ignore_friends";
    public static final String IGNORE_STAFF = "ignore_staff";
    public static final String IGNORE_ENEMIES = "ignore_enemies";
    public static final String IGNORE_NAKED = "ignore_naked";
    public static final String IGNORE_ENTITIES = "ignore_entities";
    public static final String VISIBLE_ONLY = "visible_only";

    @Override
    public String commonI18nKey() {
        return "target";
    }

    @Override
    public Map<String, Boolean> defaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(PLAYERS_ONLY, true);
        defaults.put(IGNORE_FRIENDS, true);
        defaults.put(IGNORE_STAFF, true);
        defaults.put(IGNORE_ENEMIES, false);
        defaults.put(IGNORE_NAKED, false);
        defaults.put(IGNORE_ENTITIES, false);
        defaults.put(VISIBLE_ONLY, false);
        return defaults;
    }
}
