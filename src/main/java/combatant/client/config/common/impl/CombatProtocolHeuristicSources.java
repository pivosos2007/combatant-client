/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.common.CommonBooleanGroupSchema;
import combatant.client.util.combat.protocol.CombatProtocolHeuristics;

import java.util.Map;

public final class CombatProtocolHeuristicSources implements CommonBooleanGroupSchema {
    @Override
    public String commonI18nKey() {
        return "combat.protocol_heuristics";
    }

    @Override
    public Map<String, Boolean> defaults() {
        return CombatProtocolHeuristics.defaultSourceToggles();
    }
}
