/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.addon;

import combatant.client.events.UsedImplicitly;
import combatant.client.features.module.Module;

/**
 * Early addon context used before Combatant's built-in modules are instantiated.
 *
 * <p>Only enabled addons receive this callback. Exclusions are startup-only: changing
 * an addon's enabled state after module discovery requires a client restart for the
 * built-in module set to be rebuilt.</p>
 */
public interface CombatantModuleLoadContext {
    @UsedImplicitly
    String addonId();

    /**
     * Prevents an auto-loaded built-in module from being instantiated.
     *
     * <p>The reference may be a {@code ModuleInfo.id}, module alias, simple class name,
     * or fully-qualified class name.</p>
     *
     * @return {@code true} when the exclusion was accepted for the current startup;
     *         {@code false} when the reference is invalid or module discovery has already started
     */
    @UsedImplicitly
    boolean disableBuiltInModule(String moduleReference);

    /**
     * Class-based convenience overload for addons that directly depend on a built-in module type.
     */
    @UsedImplicitly
    default boolean disableBuiltInModule(Class<? extends Module> moduleType) {
        return moduleType != null && disableBuiltInModule(moduleType.getName());
    }
}
