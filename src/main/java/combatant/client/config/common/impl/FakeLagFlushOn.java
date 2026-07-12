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

public final class FakeLagFlushOn implements CommonBooleanGroupSchema {
    public static final String ENTITY_INTERACT = "entity_interact";
    public static final String BLOCK_INTERACT = "block_interact";
    public static final String ACTION = "action";

    @Override
    public String commonI18nKey() {
        return "fakelag.flush_on";
    }

    @Override
    public Map<String, Boolean> defaults() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(ENTITY_INTERACT, false);
        defaults.put(BLOCK_INTERACT, false);
        defaults.put(ACTION, false);
        return defaults;
    }
}
