/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.addon.CombatantAddonRuntimeContext;
import combatant.client.api.v0.client.CombatantClientApi;

final class CombatantAddonRuntimeContextImpl implements CombatantAddonRuntimeContext {
    private final String addonId;

    CombatantAddonRuntimeContextImpl(String addonId) {
        this.addonId = addonId;
    }

    @Override
    public String addonId() {
        return addonId;
    }

    @Override
    public CombatantClientApi client() {
        return CombatantClientApi.get();
    }
}
