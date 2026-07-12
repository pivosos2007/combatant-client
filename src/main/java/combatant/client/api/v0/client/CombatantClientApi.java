/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.client;

import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;

import java.util.List;

public final class CombatantClientApi {
    private static final CombatantClientApi INSTANCE = new CombatantClientApi();

    private CombatantClientApi() {
    }

    public static CombatantClientApi get() {
        return INSTANCE;
    }

    public List<Module> modules() {
        return ModuleManager.getModules();
    }

    public Module module(String id) {
        return ModuleManager.get(id);
    }

    public boolean isModuleEnabled(String id) {
        return ModuleManager.isEnabled(id);
    }

    public void setModuleEnabled(String id, boolean enabled) {
        ModuleManager.setEnabled(id, enabled);
    }
}
