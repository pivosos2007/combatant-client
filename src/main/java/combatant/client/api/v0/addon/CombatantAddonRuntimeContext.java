/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.addon;

import combatant.client.api.v0.client.CombatantClientApi;

public interface CombatantAddonRuntimeContext {
    String addonId();

    CombatantClientApi client();
}
