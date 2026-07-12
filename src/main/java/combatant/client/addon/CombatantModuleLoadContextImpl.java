/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.addon.CombatantModuleLoadContext;
import combatant.client.features.module.ModuleAutoLoader;

import java.util.ArrayList;
import java.util.List;

final class CombatantModuleLoadContextImpl implements CombatantModuleLoadContext {
    private final AddonRegistration registration;
    private final List<String> pendingExclusions = new ArrayList<>();

    CombatantModuleLoadContextImpl(AddonRegistration registration) {
        this.registration = registration;
    }

    @Override
    public String addonId() {
        return registration.descriptor.id();
    }

    @Override
    public boolean disableBuiltInModule(String moduleReference) {
        String reference = moduleReference == null ? "" : moduleReference.trim();
        if (reference.isEmpty()) return false;

        if (!ModuleAutoLoader.acceptsDisableRequests()) {
            noteRestartRequired(reference);
            return false;
        }

        if (!pendingExclusions.contains(reference)) {
            pendingExclusions.add(reference);
        }
        return true;
    }

    void commit() {
        for (String reference : pendingExclusions) {
            if (!registration.disabledBuiltInModules.contains(reference)) {
                registration.disabledBuiltInModules.add(reference);
            }
            if (!ModuleAutoLoader.requestDisable(reference, addonId())) {
                noteRestartRequired(reference);
            }
        }
        pendingExclusions.clear();
    }

    private void noteRestartRequired(String reference) {
        if (!registration.disabledBuiltInModules.contains(reference)) {
            registration.disabledBuiltInModules.add(reference);
        }
        registration.restartRequired = true;
        registration.issue(
                AddonIssue.Severity.WARNING,
                "Built-in module exclusion requires restart",
                reference
        );
    }
}
