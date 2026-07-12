/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.addon;

/**
 * Fabric entrypoint contract for Combatant addons.
 *
 * <p>Declare it in fabric.mod.json under the {@value #ENTRYPOINT_KEY} entrypoint.
 */
public interface CombatantAddon {
    String ENTRYPOINT_KEY = "combatant:addon";
    int API_VERSION = 0;

    /**
     * Called before Combatant auto-loads its built-in modules.
     *
     * <p>Use this hook only for startup module exclusions. Normal addon registration
     * still belongs in {@link #onInitialize(CombatantAddonContext)}.</p>
     */
    default void onConfigureModules(CombatantModuleLoadContext context) {
    }

    void onInitialize(CombatantAddonContext context);

    /**
     * Called after all built-in and addon module configs have been loaded and applied.
     * This is the safe startup hook for mutating settings of existing modules.
     */
    default void onClientReady(CombatantAddonRuntimeContext context) {
    }

    default void onRuntimeSuspended(CombatantAddonRuntimeContext context) {
    }

    default void onRuntimeResumed(CombatantAddonRuntimeContext context) {
    }

    default void onShutdown(CombatantAddonRuntimeContext context) {
    }
}
